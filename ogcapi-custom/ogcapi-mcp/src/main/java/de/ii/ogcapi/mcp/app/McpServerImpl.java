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
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.search.domain.StoredQueryExpression;
import de.ii.ogcapi.features.search.domain.StoredQueryRepository;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.EndpointExtension;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
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
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaObject;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaInteger;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaObject;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaString;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServlet;
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

  @Inject
  public McpServerImpl(
      AppContext appContext,
      Jackson jackson,
      ExtensionRegistry extensionRegistry,
      StoredQueryRepository storedQueryRepository,
      FeaturesCoreProviders providers) {
    this.appContext = appContext;
    this.extensionRegistry = extensionRegistry;
    this.storedQueryRepository = storedQueryRepository;
    this.objectMapper = jackson.getDefaultObjectMapper();
    this.providers = providers;
  }

  // TODO: az, using custom transport for now, regular transport needs upgrade to dropwizard v4
  @Override
  public HttpServlet getServlet(OgcApiDataV2 apiData) {
    return servers.computeIfAbsent(
        apiData.getStableHash(),
        key -> {
          HttpServletStatelessServerTransportJavaX transport =
              HttpServletStatelessServerTransportJavaX.builder()
                  .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                  .messageEndpoint("/mcp")
                  .build();
          try {
            McpStatelessSyncServer server = createServer(apiData, transport);
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }

          return transport;
        });
  }

  private McpStatelessSyncServer createServer(
      OgcApiDataV2 apiData, McpStatelessServerTransport transport) throws IOException {
    McpSchema mcpSchema = getSchema(apiData);
    McpStatelessSyncServer server =
        io.modelcontextprotocol.server.McpServer.sync(transport)
            .serverInfo(appContext.getName(), appContext.getVersion())
            .capabilities(ServerCapabilities.builder().tools(true).build())
            .build();

    for (McpTool tool : mcpSchema.getTools()) {
      server.addTool(
          new McpStatelessServerFeatures.SyncToolSpecification(
              new Tool(
                  tool.getId(),
                  tool.getName(),
                  tool.getDescription(),
                  objectMapper.readValue(
                      objectMapper.writeValueAsString(tool.getInputSchema()),
                      io.modelcontextprotocol.spec.McpSchema.JsonSchema.class),
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
                          apiData, collectionId, arguments.arguments(), tool.getQueryParameters());

                  return new CallToolResult(result, false);
                }

                return new CallToolResult("Unknown tool id: " + tool.getId(), true);
              }));
    }

    return server;
  }

  // TODO: implement
  private String handleCollectionQuery(
      OgcApiDataV2 apiData,
      String collectionId,
      Map<String, Object> parameters,
      List<OgcApiQueryParameter> queryParameters) {

    Map<String, String> stringParams =
        parameters.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey, e -> e.getValue() != null ? e.getValue().toString() : null));

    QueryParameterSet parameterSet = QueryParameterSet.of(queryParameters, stringParams);
    System.out.println("parameterSet: " + parameterSet.getValues());
    // curl -H "Content-Type: application/json" -H "Accept: application/json,text/event-stream" -d
    // '{"jsonrpc": "2.0", "id": 2, "method": "tools/call", "params": {"name":
    // "collection_nullpunkte", "arguments":{"nknr": "12345"}}}' -v
    // http://localhost:7080/strassen/mcp

    FeatureTypeConfigurationOgcApi collectionData = apiData.getCollections().get(collectionId);
    FeatureProvider featureProvider = providers.getFeatureProviderOrThrow(apiData, collectionData);
    System.out.println("featureProvider: " + featureProvider);

    /*TODO:
      - add a List<OgcApiQueryParameter> to McpTool, add the filteredItems to it in getSchema
      - pass that list as argument to this method from createServer
      - create QueryParameterSet using QueryParameterSet.of with that list and the 'parameters' map
      - get FeatureProvider for api and collection using FeaturesCoreProviders, see EndpointFeatures:312
      - create FeatureQuery using FeaturesQuery.requestToFeatureQuery, see EndpointFeatures:297
      - create QueryInputFeatures using ImmutableQueryInputFeatures.Builder
      - create ApiRequestContext with ImmutableStaticRequestContext.Builder, see PubSubBuildingBlock:545 (leave alternateMediaTypes empty)
      - call FeaturesCoreQueryHandler.handle(Query.FEATURES, queryInput, apiRequestContext) with that input
      - the returned Response should contain the result to be
    */

    return "TODO: handled collection query for collection: " + collectionId;
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
  public McpSchema getSchema(OgcApiDataV2 apiData) {

    if (!schemas.containsKey(apiData.getStableHash())) {
      LOGGER.debug("Creating MCP schema for API {}", apiData.getId());

      McpConfiguration mcpConfiguration =
          apiData.getExtension(McpConfiguration.class).orElseThrow();

      List<StoredQueryExpression> storedQueries = storedQueryRepository.getAll(apiData);

      Optional<McpIncludeExclude> includedOpt = mcpConfiguration.getIncluded();
      Optional<McpIncludeExclude> excludedOpt = mcpConfiguration.getExcluded();

      List<String> includedQueries =
          includedOpt.map(McpConfiguration.McpIncludeExclude::getQueries).orElse(null);
      List<String> excludedQueries =
          excludedOpt.map(McpConfiguration.McpIncludeExclude::getQueries).orElse(List.of());

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
                                    Map.Entry::getKey,
                                    entry -> {
                                      Object schema = entry.getValue();
                                      ImmutableJsonSchemaObject.Builder builder =
                                          new ImmutableJsonSchemaObject.Builder();

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
                        new ImmutableJsonSchemaObject.Builder()
                            .properties(parameterProperties)
                            .build();

                    JsonSchemaObject inputSchema =
                        new ImmutableJsonSchemaObject.Builder()
                            .properties(Map.of("parameters", parametersSchema))
                            .build();

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
          includedOpt.map(McpConfiguration.McpIncludeExclude::getCollections).orElse(null);
      List<String> excludedCollections =
          excludedOpt.map(McpConfiguration.McpIncludeExclude::getCollections).orElse(List.of());

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
                          qp ->
                              new ImmutableJsonSchemaObject.Builder()
                                  .description(qp.getDescription())
                                  .build())));

      Map<String, JsonSchemaObject> properties =
          collectionProperties.entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey, // collectionId
                      e ->
                          new ImmutableJsonSchemaObject.Builder()
                              .properties(e.getValue())
                              .build()));

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
