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
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.features.core.domain.FeaturesQuery;
import de.ii.ogcapi.features.core.domain.ImmutableQueryInputFeatures;
import de.ii.ogcapi.features.search.domain.ImmutableQueryInputQuery;
import de.ii.ogcapi.features.search.domain.ImmutableStoredQueryExpression;
import de.ii.ogcapi.features.search.domain.ParameterResolver;
import de.ii.ogcapi.features.search.domain.QueryExpression;
import de.ii.ogcapi.features.search.domain.QueryExpressionQueryParameter;
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler;
import de.ii.ogcapi.features.search.domain.StoredQueryExpression;
import de.ii.ogcapi.features.search.domain.StoredQueryRepository;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableStaticRequestContext;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.mcp.domain.ImmutableMcpSchema;
import de.ii.ogcapi.mcp.domain.ImmutableMcpTool;
import de.ii.ogcapi.mcp.domain.McpConfiguration;
import de.ii.ogcapi.mcp.domain.McpSchema;
import de.ii.ogcapi.mcp.domain.McpServer;
import de.ii.ogcapi.mcp.domain.McpTool;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.base.domain.AppLifeCycle;
import de.ii.xtraplatform.base.domain.Jackson;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.cql.domain.Cql;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import io.swagger.v3.oas.models.media.Schema;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServlet;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
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
  private final SearchQueriesHandler searchQueriesHandler;
  private final Cql cql;

  // Utility method to parse parameter values
  private Object parseParameterValue(Schema<?> schema, Object value) {
    if (schema != null && "array".equals(schema.getType())) {
      if (value instanceof List<?> list) {
        return list.stream().map(Object::toString).collect(Collectors.joining(","));
      }
      if (value instanceof String str) {
        String cleaned = str.replaceAll("^\\[|\\]$", "");
        return cleaned;
      }
    }
    return value != null ? value.toString() : null;
  }

  @Inject
  public McpServerImpl(
      AppContext appContext,
      Jackson jackson,
      ExtensionRegistry extensionRegistry,
      StoredQueryRepository storedQueryRepository,
      FeaturesCoreProviders providers,
      FeaturesQuery ogcApiFeaturesQuery,
      FeaturesCoreQueriesHandler featuresCoreQueriesHandler,
      SearchQueriesHandler searchQueriesHandler,
      Cql cql) {
    this.appContext = appContext;
    this.extensionRegistry = extensionRegistry;
    this.storedQueryRepository = storedQueryRepository;
    this.objectMapper = jackson.getDefaultObjectMapper();
    this.providers = providers;
    this.ogcApiFeaturesQuery = ogcApiFeaturesQuery;
    this.featuresCoreQueriesHandler = featuresCoreQueriesHandler;
    this.searchQueriesHandler = searchQueriesHandler;
    this.cql = cql;
  }

  // TODO: az, using custom transport for now, regular transport needs upgrade to dropwizard v4
  @Override
  public HttpServlet getServlet(OgcApi api) {
    return servers.computeIfAbsent(
        api.getData().getStableHash(),
        key -> {
          HttpServletStatelessServerTransportJavaX transport =
              HttpServletStatelessServerTransportJavaX.builder()
                  .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                  .messageEndpoint("/mcp")
                  .build();
          try {
            McpStatelessSyncServer server = createServer(api, transport);
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }

          return transport;
        });
  }

  private McpStatelessSyncServer createServer(OgcApi api, McpStatelessServerTransport transport)
      throws IOException {
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
                      objectMapper.writeValueAsString(tool.getInputSchema()),
                      io.modelcontextprotocol.spec.McpSchema.JsonSchema.class),
                  null,
                  null,
                  null),
              (exchange, arguments) -> {
                try {
                  if (tool.getId().startsWith(STORED_QUERY_PREFIX)) {
                    String queryId = tool.getId().substring(STORED_QUERY_PREFIX.length());

                    String result =
                        handleStoredQuery(
                            api, queryId, arguments.arguments(), tool.getQueryParameters());
                    Map<String, Object> resultAsMap = objectMapper.readValue(result, Map.class);
                    return new CallToolResult(List.of(new TextContent(result)), false, resultAsMap);

                  } else if (tool.getId().startsWith(COLLECTION_QUERY_PREFIX)) {
                    String collectionId = tool.getId().substring(COLLECTION_QUERY_PREFIX.length());
                    String result =
                        handleCollectionQuery(
                            api, collectionId, arguments.arguments(), tool.getQueryParameters());

                    Map<String, Object> resultAsMap = objectMapper.readValue(result, Map.class);
                    return new CallToolResult(List.of(new TextContent(result)), false, resultAsMap);
                  }
                } catch (Throwable e) {
                  LogContext.errorAsDebug(LOGGER, e, "Error executing MCP tool '{}'", tool.getId());

                  return new CallToolResult(
                      "Error executing tool '" + tool.getId() + "': " + e.getMessage(), true);
                }

                return new CallToolResult("Unknown tool id: " + tool.getId(), true);
              }));
    }

    return server;
  }

  private String handleCollectionQuery(
      OgcApi api,
      String collectionId,
      Map<String, Object> parameters,
      List<OgcApiQueryParameter> queryParameters) {
    OgcApiDataV2 apiData = api.getData();

    Map<String, String> stringParams =
        parameters.entrySet().stream()
            .filter(e -> Objects.nonNull(e.getValue()))
            .collect(
                Collectors.toMap(
                    Entry::getKey,
                    e -> {
                      OgcApiQueryParameter param =
                          queryParameters.stream()
                              .filter(p -> p.getName().equals(e.getKey()))
                              .findFirst()
                              .orElse(null);
                      Schema<?> schema =
                          param != null ? param.getSchema(api.getData(), Optional.empty()) : null;
                      Object parsed = parseParameterValue(schema, e.getValue());
                      return parsed != null ? parsed.toString() : null;
                    }));

    QueryParameterSet parameterSet = QueryParameterSet.of(queryParameters, stringParams);

    FeatureTypeConfigurationOgcApi collectionData = apiData.getCollections().get(collectionId);
    parameterSet = parameterSet.evaluate(api, Optional.of(collectionData));

    FeatureProvider featureProvider = providers.getFeatureProviderOrThrow(apiData, collectionData);

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

    FeatureQuery query =
        ogcApiFeaturesQuery.requestToFeatureQuery(
            apiData,
            collectionData,
            coreConfiguration.getDefaultEpsgCrs(),
            coreConfiguration.getCoordinatePrecision(),
            defaultPageSize,
            parameterSet);

    FeaturesCoreQueriesHandler.QueryInputFeatures queryInput =
        new ImmutableQueryInputFeatures.Builder()
            .collectionId(collectionId)
            .query(query)
            .featureProvider(featureProvider)
            .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
            .defaultPageSize(Optional.of(defaultPageSize))
            .includeBodyLinks(false)
            .sendResponseAsStream(false)
            .build();

    URI uri = URI.create("/collections/" + collectionId + "/items");

    ApiRequestContext requestContext =
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

    try (Response response =
        featuresCoreQueriesHandler.handle(
            FeaturesCoreQueriesHandler.Query.FEATURES, queryInput, requestContext)) {

      Object entity = response.getEntity();
      String result = entity instanceof byte[] ? new String((byte[]) entity) : "";

      return result;
    }
  }

  private String handleStoredQuery(
      OgcApi api,
      String queryId,
      Map<String, Object> parameters,
      List<OgcApiQueryParameter> queryParameters)
      throws IOException {

    OgcApiDataV2 apiData = api.getData();

    Map<String, String> stringParams =
        parameters.entrySet().stream()
            .filter(e -> Objects.nonNull(e.getValue()))
            .collect(
                Collectors.toMap(
                    Entry::getKey,
                    e -> {
                      OgcApiQueryParameter param =
                          queryParameters.stream()
                              .filter(p -> p.getName().equals(e.getKey()))
                              .findFirst()
                              .orElse(null);
                      Schema<?> schema =
                          param != null ? param.getSchema(api.getData(), Optional.empty()) : null;
                      Object parsed = parseParameterValue(schema, e.getValue());
                      return parsed != null ? parsed.toString() : null;
                    }));

    QueryParameterSet queryParameterSet = QueryParameterSet.of(queryParameters, stringParams);
    queryParameterSet = queryParameterSet.evaluate(api, Optional.empty());

    StoredQueryExpression storedQuery = storedQueryRepository.get(apiData, queryId);
    ImmutableStoredQueryExpression.Builder builder =
        new ImmutableStoredQueryExpression.Builder().from(storedQuery);
    for (OgcApiQueryParameter parameter : queryParameterSet.getDefinitions()) {
      if (parameter instanceof QueryExpressionQueryParameter) {
        ((QueryExpressionQueryParameter) parameter).applyTo(builder, queryParameterSet);
      }
    }

    storedQuery = builder.build();

    FeaturesCoreConfiguration coreConfiguration =
        apiData.getExtension(FeaturesCoreConfiguration.class).orElseThrow();

    SchemaValidator schemaValidator =
        queryParameterSet.getDefinitions().stream()
            .map(
                param -> {
                  try {
                    java.lang.reflect.Field f =
                        param.getClass().getDeclaredField("schemaValidator");
                    f.setAccessible(true);
                    return (SchemaValidator) f.get(param);
                  } catch (Exception e) {
                    return null;
                  }
                })
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

    if (schemaValidator == null) {
      throw new IllegalStateException("No SchemaValidator found!");
    }

    QueryExpression executableQuery =
        new ParameterResolver(queryParameterSet, schemaValidator, cql).visit(storedQuery);

    SearchQueriesHandler.QueryInputQuery queryInput =
        new ImmutableQueryInputQuery.Builder()
            .query(executableQuery)
            .featureProvider(providers.getFeatureProviderOrThrow(apiData))
            .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
            .minimumPageSize(Optional.ofNullable(coreConfiguration.getMinimumPageSize()))
            .defaultPageSize(Optional.ofNullable(coreConfiguration.getDefaultPageSize()))
            .maximumPageSize(Optional.ofNullable(coreConfiguration.getMaximumPageSize()))
            .isStoredQuery(true)
            .includeBodyLinks(false)
            .build();

    ApiRequestContext requestContext =
        new ImmutableStaticRequestContext.Builder()
            .webContext(appContext)
            .api(api)
            .requestUri(URI.create("/search/" + queryId))
            .mediaType(
                new ImmutableApiMediaType.Builder()
                    .type(new MediaType("application", "geo+json"))
                    .label("GeoJSON")
                    .parameter("json")
                    .build())
            .alternateMediaTypes(Set.of())
            .queryParameterSet(queryParameterSet)
            .build();

    try (Response response =
        searchQueriesHandler.handle(SearchQueriesHandler.Query.QUERY, queryInput, requestContext)) {
      Object entity = response.getEntity();

      if (entity instanceof StreamingOutput) {
        StreamingOutput streamingOutput = (StreamingOutput) entity;
        OutputStream os = new ByteArrayOutputStream();
        streamingOutput.write(os);
        return os.toString();
      }

      return entity instanceof byte[] ? new String((byte[]) entity) : "";
    }
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

      List<ImmutableMcpTool> storedQueryTools =
          McpToolUtils.filterAndCreateStoredQueries(
              storedQueries, mcpConfiguration, extensionRegistry, apiData);

      List<ImmutableMcpTool> collectionTools =
          McpToolUtils.filterAndCreateCollections(
              mcpConfiguration,
              apiData,
              extensionRegistry,
              List.of("bbox", "datetime", "limit", "offset", "sortby"));

      schemas.put(
          apiData.getStableHash(),
          new ImmutableMcpSchema.Builder()
              .addAllTools(
                  Stream.concat(collectionTools.stream(), storedQueryTools.stream())
                      .collect(Collectors.toList()))
              .build());
    }
    return schemas.get(apiData.getStableHash());
  }
}
