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
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import io.swagger.v3.oas.models.media.ObjectSchema;
import java.io.IOException;
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
                      objectMapper.writeValueAsString(tool.getInputSchema()), JsonSchema.class),
                  null,
                  null,
                  null),
              (exchange, arguments) -> {
                try {
                  if (tool.getId().startsWith(STORED_QUERY_PREFIX)) {
                    String queryId = tool.getId().substring(STORED_QUERY_PREFIX.length());
                    String result = handleStoredQuery(api, queryId, arguments.arguments());

                    return new CallToolResult(result, false);
                  } else if (tool.getId().startsWith(COLLECTION_QUERY_PREFIX)) {
                    String collectionId = tool.getId().substring(COLLECTION_QUERY_PREFIX.length());
                    String result =
                        handleCollectionQuery(
                            api, collectionId, arguments.arguments(), tool.getQueryParameters());

                    return new CallToolResult(result, false);
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
            .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().toString()));

    QueryParameterSet parameterSet = QueryParameterSet.of(queryParameters, stringParams);

    FeatureTypeConfigurationOgcApi collectionData = apiData.getCollections().get(collectionId);
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

  // TODO: implement
  private String handleStoredQuery(OgcApi api, String queryId, Map<String, Object> parameters) {
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

      List<ImmutableMcpTool> queryTools =
          McpToolUtils.filterAndCreateStoredQueries(storedQueries, mcpConfiguration);
      McpToolUtils.CollectionsResult collectionsResult =
          McpToolUtils.filterAndCreateCollections(mcpConfiguration, apiData, extensionRegistry);
      Map<String, ObjectSchema> collections = collectionsResult.getCollections();
      List<QueryParameterTemplateQueryable> filteredItems = collectionsResult.getFilteredItems();

      schemas.put(
          apiData.getStableHash(),
          new ImmutableMcpSchema.Builder()
              .addAllTools(
                  Stream.concat(
                          collections.entrySet().stream()
                              .map(
                                  e -> {
                                    List<OgcApiQueryParameter> collectionQueryParameters =
                                        filteredItems.stream()
                                            .filter(qp -> qp.getCollectionId().equals(e.getKey()))
                                            .collect(Collectors.toList());

                                    return new ImmutableMcpTool.Builder()
                                        .id(COLLECTION_QUERY_PREFIX + e.getKey())
                                        .name(
                                            "Collection Query - "
                                                + apiData
                                                    .getCollections()
                                                    .get(e.getKey())
                                                    .getLabel())
                                        .description(
                                            apiData
                                                .getCollections()
                                                .get(e.getKey())
                                                .getDescription()
                                                .orElse(""))
                                        .inputSchema(e.getValue())
                                        .queryParameters(collectionQueryParameters)
                                        .build();
                                  }),
                          queryTools.stream())
                      .collect(Collectors.toList()))
              .build());
    }
    return schemas.get(apiData.getStableHash());
  }
}
