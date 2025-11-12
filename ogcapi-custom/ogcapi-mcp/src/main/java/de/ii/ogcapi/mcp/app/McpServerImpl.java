/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.collections.queryables.domain.QueryParameterTemplateQueryable;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.features.core.domain.FeaturesQuery;
import de.ii.ogcapi.features.core.domain.ImmutableQueryInputFeatures;
import de.ii.ogcapi.features.search.domain.StoredQueryExpression;
import de.ii.ogcapi.features.search.domain.StoredQueryRepository;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.EndpointExtension;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableStaticRequestContext;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.mcp.domain.ImmutableMcpSchema;
import de.ii.ogcapi.mcp.domain.ImmutableMcpTool;
import de.ii.ogcapi.mcp.domain.McpConfiguration;
import de.ii.ogcapi.mcp.domain.McpConfiguration.McpIncludeExclude;
import de.ii.ogcapi.mcp.domain.McpSchema;
import de.ii.ogcapi.mcp.domain.McpServer;
import de.ii.ogcapi.mcp.domain.McpTool;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.base.domain.AppLifeCycle;
import de.ii.xtraplatform.base.domain.Jackson;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaObject.Builder;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaInteger;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaObject;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaString;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServlet;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class McpServerImpl implements McpServer, AppLifeCycle {

  private static final Logger LOGGER = LoggerFactory.getLogger(McpServerImpl.class);
  private static final String STORED_QUERY_PREFIX = "query_";
  private static final String COLLECTION_QUERY_PREFIX = "collection_";

  private final Map<String, McpSchema> schemas = new ConcurrentHashMap<>();
  private final AppContext appContext;
  private final ExtensionRegistry extensionRegistry;
  private final StoredQueryRepository storedQueryRepository;
  private final ObjectMapper objectMapper;
  private final Map<String, HttpServletStatelessServerTransportJavaX> servers =
      new ConcurrentHashMap<>();
  private final FeaturesCoreProviders providers;
  private final FeaturesQuery ogcApiFeaturesQuery;
  private final FeaturesCoreQueriesHandler featuresCoreQueriesHandler;

  @Inject
  public McpServerImpl(
      AppContext appContext,
      Jackson jackson,
      ExtensionRegistry extensionRegistry,
      StoredQueryRepository storedQueryRepository,
      FeaturesCoreProviders providers,
      FeaturesQuery ogcApiFeaturesQuery,
      FeaturesCoreQueriesHandler featuresCoreQueriesHandler) {
    this.appContext = appContext;
    this.extensionRegistry = extensionRegistry;
    this.storedQueryRepository = storedQueryRepository;
    this.objectMapper = jackson.getDefaultObjectMapper();
    this.providers = providers;
    this.ogcApiFeaturesQuery = ogcApiFeaturesQuery;
    this.featuresCoreQueriesHandler = featuresCoreQueriesHandler;
  }

  // TODO: az, using custom transport for now, regular transport needs upgrade to dropwizard v4
  @Override
  public HttpServlet getServlet(OgcApi api, OgcApiDataV2 apiData) {
    return servers.computeIfAbsent(
        apiData.getStableHash(),
        key -> {
          HttpServletStatelessServerTransportJavaX transport =
              HttpServletStatelessServerTransportJavaX.builder()
                  .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                  .messageEndpoint("/mcp")
                  .build();
          try {
            McpStatelessSyncServer server = createServer(api, apiData, transport);
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }

          return transport;
        });
  }

  private McpStatelessSyncServer createServer(
      OgcApi api, OgcApiDataV2 apiData, McpStatelessServerTransport transport) throws IOException {
    McpSchema mcpSchema = getSchema(api);
    McpStatelessSyncServer server =
        io.modelcontextprotocol.server.McpServer.sync(transport)
            .serverInfo(appContext.getName(), appContext.getVersion())
            .capabilities(ServerCapabilities.builder().tools(true).build())
            .build();

    for (McpTool tool : mcpSchema.getTools()) {
      server.addTool(
          new SyncToolSpecification(
              new Tool(
                  tool.getId(),
                  tool.getName(),
                  tool.getDescription(),
                  objectMapper.readValue(
                      objectMapper.writeValueAsString(tool.getInputSchema()), JsonSchema.class),
                  null,
                  null,
                  null),
              (exchange, arguments) -> {
                if (tool.getId().startsWith(STORED_QUERY_PREFIX)) {
                  String queryId = tool.getId().substring(STORED_QUERY_PREFIX.length());
                  String result = handleStoredQuery(apiData, queryId, arguments.arguments());

                  return new CallToolResult(result, false);
                } else if (tool.getId().startsWith(COLLECTION_QUERY_PREFIX)) {
                  String collectionId = tool.getId().substring(COLLECTION_QUERY_PREFIX.length());
                  String result =
                      handleCollectionQuery(
                          api,
                          apiData,
                          collectionId,
                          arguments.arguments(),
                          tool.getQueryParameters());

                  return new CallToolResult(result, false);
                }

                return new CallToolResult("Unknown tool id: " + tool.getId(), true);
              }));
    }

    return server;
  }

  private String handleCollectionQuery(
      OgcApi api,
      OgcApiDataV2 apiData,
      String collectionId,
      Map<String, Object> parameters,
      List<OgcApiQueryParameter> queryParameters) {

    Map<String, String> stringParams =
        parameters.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Entry::getKey, e -> e.getValue() != null ? e.getValue().toString() : null));

    QueryParameterSet parameterSet = QueryParameterSet.of(queryParameters, stringParams);
    System.out.println("parameterSet: " + parameterSet.getValues());
    // curl -H "Content-Type: application/json" -H "Accept: application/json,text/event-stream" -d
    // '{"jsonrpc": "2.0", "id": 2, "method": "tools/call", "params": {"name":
    // "collection_nullpunkte", "arguments":{"nknr": "12345"}}}' -v
    // http://localhost:7080/strassen/mcp

    FeatureTypeConfigurationOgcApi collectionData = apiData.getCollections().get(collectionId);
    FeatureProvider featureProvider = providers.getFeatureProviderOrThrow(apiData, collectionData);
    System.out.println("featureProvider: " + featureProvider);

    FeaturesCoreConfiguration coreConfiguration =
        collectionData
            .getExtension(FeaturesCoreConfiguration.class)
            .filter(ExtensionConfiguration::isEnabled)
            .filter(
                cfg ->
                    cfg.getItemType().orElse(FeaturesCoreConfiguration.ItemType.feature)
                        != FeaturesCoreConfiguration.ItemType.unknown)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        String.format(
                            "Features are not supported in collection '%s'.", collectionId)));

    int defaultPageSize = coreConfiguration.getDefaultPageSize();

    System.out.println("cfg and pagesize: " + coreConfiguration + ", " + defaultPageSize);

    FeatureQuery query =
        ogcApiFeaturesQuery.requestToFeatureQuery(
            apiData,
            collectionData,
            coreConfiguration.getDefaultEpsgCrs(),
            coreConfiguration.getCoordinatePrecision(),
            defaultPageSize,
            parameterSet);

    System.out.println("query: " + query);

    FeaturesCoreQueriesHandler.QueryInputFeatures queryInput =
        new ImmutableQueryInputFeatures.Builder()
            .collectionId(collectionId)
            .query(query)
            .featureProvider(featureProvider)
            .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
            .defaultPageSize(Optional.of(defaultPageSize))
            .build();

    System.out.println("queryInput: " + queryInput);

    URI uri = URI.create("/collections/" + collectionId + "/items");

    ApiRequestContext requestContextGeoJson =
        new ImmutableStaticRequestContext.Builder()
            .webContext(appContext)
            .api(api)
            .requestUri(uri)
            .mediaType(
                new ImmutableApiMediaType.Builder()
                    .type(new MediaType("application", "geo+json"))
                    .label("GeoJSON")
                    .parameter("json")
                    .build())
            .alternateMediaTypes(Set.of())
            .queryParameterSet(parameterSet)
            .build();

    System.out.println("requestContextGeoJson: " + requestContextGeoJson);

    javax.ws.rs.core.Response response =
        featuresCoreQueriesHandler.handle(
            FeaturesCoreQueriesHandler.Query.FEATURES, queryInput, requestContextGeoJson);

    System.out.println("response: " + response);

    try {
      String result = response.readEntity(String.class);
      System.out.println("result: " + result);
      return result;
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("Fehler beim Lesen der Entity: " + e.getMessage());
      return null;
    }
  }

  // TODO: implement
  private String handleStoredQuery(
      OgcApiDataV2 apiData, String queryId, Map<String, Object> parameters) {
    return "TODO: handled stored query: " + queryId;
  }

  @Override
  public void onStop() {
    servers.values().forEach(HttpServletStatelessServerTransportJavaX::close);

    AppLifeCycle.super.onStop();
  }

  @Override
  public McpSchema getSchema(OgcApi api) {
    OgcApiDataV2 apiData = api.getData();

    if (!schemas.containsKey(apiData.getStableHash())) {
      LOGGER.debug("Creating MCP schema for API {}", apiData.getId());

      McpConfiguration mcpConfiguration =
          apiData.getExtension(McpConfiguration.class).orElseThrow();

      List<StoredQueryExpression> storedQueries = storedQueryRepository.getAll(apiData);

      Optional<McpIncludeExclude> includedOpt = mcpConfiguration.getIncluded();
      Optional<McpIncludeExclude> excludedOpt = mcpConfiguration.getExcluded();

      List<String> includedQueries = includedOpt.map(McpIncludeExclude::getQueries).orElse(null);
      List<String> excludedQueries =
          excludedOpt.map(McpIncludeExclude::getQueries).orElse(List.of());

      List<StoredQueryExpression> filteredQueries;
      if ((includedQueries == null || includedOpt.isEmpty()) && excludedQueries.isEmpty()) {
        filteredQueries = storedQueries;
      } else if (includedQueries == null) {
        filteredQueries = List.of();
      } else if (includedQueries.contains("*")) {
        filteredQueries =
            storedQueries.stream()
                .filter(
                    q ->
                        !excludedQueries.contains(q.getId())
                            && !excludedQueries.contains(
                                q.getTitle() != null ? q.getTitle().orElse("") : ""))
                .toList();
      } else {
        filteredQueries =
            storedQueries.stream()
                .filter(
                    q ->
                        includedQueries.contains(q.getId())
                            || includedQueries.contains(
                                q.getTitle() != null ? q.getTitle().orElse("") : ""))
                .filter(
                    q ->
                        !excludedQueries.contains(q.getId())
                            && !excludedQueries.contains(
                                q.getTitle() != null ? q.getTitle().orElse("") : ""))
                .toList();
      }

      List<ImmutableMcpTool> queryTools =
          filteredQueries.stream()
              .map(
                  query -> {
                    String queryId = query.getId() != null ? query.getId() : "unknown";
                    String title = query.getTitle().orElse(queryId);
                    String description = query.getDescription().orElse("");

                    // Parameter-Schema
                    Map<String, JsonSchemaObject> parameterProperties =
                        query.getParameters().entrySet().stream()
                            .collect(
                                Collectors.toMap(
                                    Entry::getKey,
                                    entry -> {
                                      Object schema = entry.getValue();
                                      Builder builder = new Builder();

                                      String paramTitle = entry.getKey();
                                      String paramDescription = "";
                                      String pattern = null;
                                      Object defaultValue = null;

                                      if (schema instanceof JsonSchemaString s) {
                                        paramTitle =
                                            s.getTitle() != null
                                                ? String.valueOf(s.getTitle())
                                                    .replace("Optional[", "")
                                                    .replace("]", "")
                                                : paramTitle;
                                        paramDescription =
                                            s.getDescription() != null
                                                ? String.valueOf(s.getDescription())
                                                    .replace("Optional[", "")
                                                    .replace("]", "")
                                                : "";
                                        pattern = String.valueOf(s.getPattern());
                                        defaultValue = s.getDefault_();
                                      } else if (schema instanceof JsonSchemaInteger i) {
                                        paramTitle =
                                            i.getTitle() != null
                                                ? String.valueOf(i.getTitle())
                                                    .replace("Optional[", "")
                                                    .replace("]", "")
                                                : paramTitle;
                                        paramDescription =
                                            i.getDescription() != null
                                                ? String.valueOf(i.getDescription())
                                                    .replace("Optional[", "")
                                                    .replace("]", "")
                                                : "";
                                        defaultValue = i.getDefault_();
                                      }

                                      builder.title(paramTitle).description(paramDescription);

                                      if (pattern != null) builder.codelistId(pattern);
                                      if (defaultValue != null) builder.default_(defaultValue);

                                      return builder.build();
                                    }));

                    JsonSchemaObject parametersSchema =
                        new Builder().properties(parameterProperties).build();

                    JsonSchemaObject inputSchema =
                        new Builder().properties(Map.of("parameters", parametersSchema)).build();

                    return new ImmutableMcpTool.Builder()
                        .id(STORED_QUERY_PREFIX + queryId)
                        .name("Stored Query - " + title)
                        .description(description)
                        .inputSchema(inputSchema)
                        .build();
                  })
              .collect(Collectors.toList());

      // Collections
      List<ApiEndpointDefinition> definitions =
          extensionRegistry.getExtensionsForType(EndpointExtension.class).stream()
              .filter(endpoint -> endpoint.isEnabledForApi(apiData))
              .map(endpoint -> endpoint.getDefinition(apiData))
              .filter(
                  def ->
                      def.getResources().values().stream()
                          .flatMap(res -> res.getOperations().values().stream())
                          .anyMatch(op -> op.getOperationId().contains("getItems")))
              .toList();

      List<QueryParameterTemplateQueryable> allItems =
          definitions.stream()
              .flatMap(def -> def.getResources().values().stream())
              .flatMap(res -> res.getOperations().values().stream())
              .flatMap(op -> op.getQueryParameters().stream())
              .filter(param -> param instanceof QueryParameterTemplateQueryable)
              .map(param -> (QueryParameterTemplateQueryable) param)
              .toList();

      List<QueryParameterTemplateQueryable> filteredItems;

      includedOpt = mcpConfiguration.getIncluded();
      excludedOpt = mcpConfiguration.getExcluded();

      List<String> includedCollections =
          includedOpt.map(McpIncludeExclude::getCollections).orElse(null);
      List<String> excludedCollections =
          excludedOpt.map(McpIncludeExclude::getCollections).orElse(List.of());

      if (includedOpt.isEmpty() && excludedOpt.isEmpty()) {
        filteredItems = allItems;
      } else if (includedCollections == null) {
        filteredItems = List.of();
      } else if (includedCollections.contains("*")) {
        filteredItems =
            allItems.stream()
                .filter(qp -> !excludedCollections.contains(qp.getCollectionId()))
                .toList();
      } else {
        filteredItems =
            allItems.stream()
                .filter(qp -> includedCollections.contains(qp.getCollectionId()))
                .filter(qp -> !excludedCollections.contains(qp.getCollectionId()))
                .toList();
      }

      Map<String, Map<String, JsonSchemaObject>> collectionProperties =
          filteredItems.stream()
              .collect(
                  Collectors.groupingBy(
                      QueryParameterTemplateQueryable::getCollectionId,
                      Collectors.toMap(
                          QueryParameterTemplateQueryable::getName,
                          qp -> new Builder().description(qp.getDescription()).build())));

      Map<String, JsonSchemaObject> properties =
          collectionProperties.entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Entry::getKey, // collectionId
                      e -> new Builder().properties(e.getValue()).build()));

      schemas.put(
          apiData.getStableHash(),
          new ImmutableMcpSchema.Builder()
              .addAllTools(
                  properties.entrySet().stream()
                      .map(
                          e -> {
                            // filter query parameters for this collection
                            List<OgcApiQueryParameter> collectionQueryParameters =
                                filteredItems.stream()
                                    .filter(qp -> qp.getCollectionId().equals(e.getKey()))
                                    .collect(Collectors.toList());

                            return new ImmutableMcpTool.Builder()
                                .id(COLLECTION_QUERY_PREFIX + e.getKey())
                                .name(
                                    "Collection Query - "
                                        + apiData.getCollections().get(e.getKey()).getLabel())
                                .description(
                                    apiData
                                        .getCollections()
                                        .get(e.getKey())
                                        .getDescription()
                                        .orElse(""))
                                .inputSchema(e.getValue())
                                .queryParameters(collectionQueryParameters)
                                .build();
                          })
                      .toList())
              .addAllTools(queryTools)
              .build());
    }
    return schemas.get(apiData.getStableHash());
  }
}
