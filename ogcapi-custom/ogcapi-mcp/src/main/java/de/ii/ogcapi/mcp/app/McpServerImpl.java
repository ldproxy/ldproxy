/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.features.core.domain.FeaturesQuery;
import de.ii.ogcapi.features.core.domain.ImmutableQueryInputFeatures;
import de.ii.ogcapi.features.search.domain.ImmutableQueryExpression;
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
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.mcp.domain.ImmutableMcpSchema;
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
import de.ii.xtraplatform.web.domain.URICustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator.ValidationResponse;
import io.modelcontextprotocol.json.schema.jackson2.DefaultJsonSchemaValidator;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServlet;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class McpServerImpl implements McpServer, AppLifeCycle {

  private static final Logger LOGGER = LoggerFactory.getLogger(McpServerImpl.class);
  private static final String STORED_QUERY_PREFIX = "query_";
  private static final String COLLECTION_QUERY_PREFIX = "collection_";
  private static final Set<String> PAGING_RELS = Set.of("next", "prev");

  private final Map<String, McpSchema> schemas = new ConcurrentHashMap<>();
  private final AppContext appContext;
  private final ExtensionRegistry extensionRegistry;
  private final StoredQueryRepository storedQueryRepository;
  private final ObjectMapper objectMapper;
  private final ObjectMapper mcpObjectMapper;
  private final Map<String, HttpServletStatelessServerTransport> servers =
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
    this.mcpObjectMapper = mcpObjectMapper(objectMapper);
    this.providers = providers;
    this.ogcApiFeaturesQuery = ogcApiFeaturesQuery;
    this.featuresCoreQueriesHandler = featuresCoreQueriesHandler;
    this.searchQueriesHandler = searchQueriesHandler;
    this.cql = cql;
  }

  @Override
  public HttpServlet getServlet(OgcApi api) {
    String cacheKey = getCacheKey(api.getData());
    HttpServletStatelessServerTransport current = servers.get(cacheKey);
    if (Objects.nonNull(current)) {
      return current;
    }
    removeOutdated(servers, api.getId(), cacheKey, HttpServletStatelessServerTransport::close);

    return servers.computeIfAbsent(
        cacheKey,
        key -> {
          McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(mcpObjectMapper);
          HttpServletStatelessServerTransport transport =
              HttpServletStatelessServerTransport.builder()
                  .jsonMapper(jsonMapper)
                  .messageEndpoint("/mcp")
                  .contextExtractor(
                      request ->
                          request.getAttribute(McpServer.REQUEST_CONTEXT)
                                  instanceof ApiRequestContext requestContext
                              ? McpTransportContext.create(
                                  Map.of(McpServer.REQUEST_CONTEXT, requestContext))
                              : McpTransportContext.EMPTY)
                  .build();
          try {
            McpStatelessSyncServer server = createServer(api, transport, jsonMapper);
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }

          return transport;
        });
  }

  // the transport writes an error of a request as an McpError, which is an exception; without a
  // serializer, the response would contain the stack trace, so only the JSON-RPC error is written
  // the mapper for the messages and tool results: the null values in a tool result are kept, for
  // example the geometry of a feature without geometry, which GeoJSON requires; the messages of the
  // SDK set their own inclusion; without indentation, as a tool result is sent twice (as text and
  // as structured content) and indentation would make large results about a third larger
  static ObjectMapper mcpObjectMapper(ObjectMapper objectMapper) {
    return withMcpErrorSerializer(objectMapper)
        .disable(SerializationFeature.INDENT_OUTPUT)
        .setDefaultPropertyInclusion(JsonInclude.Value.construct(Include.ALWAYS, Include.ALWAYS));
  }

  static ObjectMapper withMcpErrorSerializer(ObjectMapper objectMapper) {
    return objectMapper
        .copy()
        .registerModule(
            new SimpleModule()
                .addSerializer(
                    McpError.class,
                    new StdSerializer<>(McpError.class) {
                      @Override
                      public void serialize(
                          McpError error, JsonGenerator gen, SerializerProvider provider)
                          throws IOException {
                        gen.writeStartObject();
                        gen.writeStringField(
                            "jsonrpc", io.modelcontextprotocol.spec.McpSchema.JSONRPC_VERSION);
                        gen.writeNullField("id");
                        gen.writeFieldName("error");
                        provider.defaultSerializeValue(error.getJsonRpcError(), gen);
                        gen.writeEndObject();
                      }
                    }));
  }

  private McpStatelessSyncServer createServer(
      OgcApi api, McpStatelessServerTransport transport, McpJsonMapper jsonMapper)
      throws IOException {
    McpSchema mcpSchema = getSchema(api);
    JsonSchemaValidator jsonSchemaValidator = new DefaultJsonSchemaValidator(objectMapper);
    List<SyncToolSpecification> toolSpecifications = new ArrayList<>();

    for (McpTool tool : mcpSchema.getTools()) {
      Map<String, Object> inputSchema = toMap(tool.getInputSchema());
      Map<String, Object> outputSchema = toMap(tool.getOutputSchema());

      // the SDK rejects the whole server if a single tool schema is invalid
      Optional<String> schemaError =
          Stream.of(inputSchema, outputSchema)
              .map(jsonSchemaValidator::validateSchema)
              .filter(response -> !response.valid())
              .map(ValidationResponse::errorMessage)
              .findFirst();
      if (schemaError.isPresent()) {
        LOGGER.warn(
            "MCP tool '{}' of API '{}' is not available, its schema is not a valid JSON Schema 2020-12 document: {}",
            tool.getId(),
            api.getId(),
            schemaError.get());
        continue;
      }

      toolSpecifications.add(
          new SyncToolSpecification(
              Tool.builder(tool.getId(), inputSchema)
                  .title(tool.getName())
                  .description(tool.getDescription())
                  .outputSchema(outputSchema)
                  .annotations(new ToolAnnotations(tool.getName(), true, false, true, false, false))
                  .build(),
              (context, request) -> {
                try {
                  if (tool.getId().startsWith(STORED_QUERY_PREFIX)) {
                    String queryId = tool.getId().substring(STORED_QUERY_PREFIX.length());

                    String result =
                        handleStoredQuery(
                            api,
                            mcpRequest(context),
                            queryId,
                            request.arguments(),
                            tool.getQueryParameters());
                    Map<String, Object> resultAsMap =
                        forToolResult(mcpObjectMapper.readValue(result, Map.class));
                    return CallToolResult.builder()
                        .addTextContent(mcpObjectMapper.writeValueAsString(resultAsMap))
                        .isError(false)
                        .structuredContent(resultAsMap)
                        .build();

                  } else if (tool.getId().startsWith(COLLECTION_QUERY_PREFIX)) {
                    String collectionId = tool.getId().substring(COLLECTION_QUERY_PREFIX.length());
                    String result =
                        handleCollectionQuery(
                            api,
                            mcpRequest(context),
                            collectionId,
                            request.arguments(),
                            tool.getQueryParameters());

                    Map<String, Object> resultAsMap =
                        forToolResult(mcpObjectMapper.readValue(result, Map.class));
                    return CallToolResult.builder()
                        .addTextContent(mcpObjectMapper.writeValueAsString(resultAsMap))
                        .isError(false)
                        .structuredContent(resultAsMap)
                        .build();
                  }
                } catch (Throwable e) {
                  LogContext.errorAsDebug(LOGGER, e, "Error executing MCP tool '{}'", tool.getId());

                  return CallToolResult.builder()
                      .addTextContent(
                          "Error executing tool '" + tool.getId() + "': " + e.getMessage())
                      .isError(true)
                      .build();
                }

                return CallToolResult.builder()
                    .addTextContent("Unknown tool id: " + tool.getId())
                    .isError(true)
                    .build();
              }));
    }

    return io.modelcontextprotocol.server.McpServer.sync(transport)
        .serverInfo(appContext.getName(), appContext.getVersion())
        // the background shared by all tools, the tool descriptions start with what the tool is for
        .instructions(
            api.getData()
                .getDescription()
                .map(description -> api.getData().getLabel() + ": " + description)
                .orElse(api.getData().getLabel()))
        .capabilities(ServerCapabilities.builder().tools(true).build())
        .jsonMapper(jsonMapper)
        .jsonSchemaValidator(jsonSchemaValidator)
        // tool names are derived from collection and query ids, which are not restricted to the
        // characters recommended by MCP; warn instead of failing the server
        .strictToolNameValidation(false)
        .tools(toolSpecifications)
        .build();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> toMap(Schema<?> schema) throws IOException {
    return objectMapper.readValue(Json.pretty(schema), Map.class);
  }

  private static Optional<ApiRequestContext> mcpRequest(McpTransportContext context) {
    return context.get(McpServer.REQUEST_CONTEXT) instanceof ApiRequestContext requestContext
        ? Optional.of(requestContext)
        : Optional.empty();
  }

  // the context of an internal request to a resource of the API; it is derived from the MCP request
  // like a request to the API (forwarded host and prefix), so the links in the response are the
  // same as in the API; the parameters of the tool call are the query parameters of the request, so
  // that the link to the next page keeps them
  private ImmutableStaticRequestContext.Builder internalRequest(
      OgcApi api,
      Optional<ApiRequestContext> mcpRequest,
      Map<String, String> parameters,
      String... path) {
    List<String> pathSegments =
        Stream.concat(api.getData().getSubPath().stream(), Arrays.stream(path)).toList();
    ImmutableStaticRequestContext.Builder builder =
        new ImmutableStaticRequestContext.Builder().webContext(appContext).api(api);

    URICustomizer uri =
        mcpRequest
            .map(request -> request.getBaseUriCustomizer().copy())
            .orElseGet(() -> new URICustomizer(api.getUri()))
            .setPathSegments(pathSegments);
    parameters.forEach(uri::setParameter);
    mcpRequest.ifPresent(request -> builder.basePathSegments(request.getBasePathSegments()));

    return builder.requestUri(URI.create(uri.toString()));
  }

  // of the links in a response, only those to other pages are useful for a client of a tool; a
  // client cannot follow a link, so the offset of the next page is stated as nextOffset, at the
  // start of the result (see McpToolUtils.PAGING_HINT)
  static Map<String, Object> forToolResult(Map<String, Object> result) {
    if (!(result.get("links") instanceof List<?> links)) {
      return result;
    }
    List<?> pagingLinks =
        links.stream()
            .filter(link -> link instanceof Map<?, ?> map && PAGING_RELS.contains(map.get("rel")))
            .toList();
    Optional<Long> nextOffset =
        pagingLinks.stream()
            .map(link -> (Map<?, ?>) link)
            .filter(link -> "next".equals(link.get("rel")) && link.get("href") instanceof String)
            .flatMap(
                link ->
                    new URICustomizer(URI.create((String) link.get("href")))
                        .getQueryParams().stream()
                            .filter(param -> "offset".equals(param.getName()))
                            .map(param -> Long.parseLong(param.getValue())))
            .findFirst();

    Map<String, Object> forTool = new LinkedHashMap<>();
    result.forEach(
        (key, value) -> {
          if (!"links".equals(key)) {
            forTool.put(key, value);
          } else if (!pagingLinks.isEmpty()) {
            forTool.put(key, pagingLinks);
          }
          if ("type".equals(key)) {
            nextOffset.ifPresent(offset -> forTool.put("nextOffset", offset));
          }
        });
    return forTool;
  }

  private String handleCollectionQuery(
      OgcApi api,
      Optional<ApiRequestContext> mcpRequest,
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
                          param != null ? param.getSchema(api.getData(), collectionId) : null;
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
            parameterSet,
            false);

    FeaturesCoreQueriesHandler.QueryInputFeatures queryInput =
        new ImmutableQueryInputFeatures.Builder()
            .collectionId(collectionId)
            .query(query)
            .featureProvider(featureProvider)
            .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
            .defaultPageSize(Optional.of(defaultPageSize))
            .profiles(Profile.of(extensionRegistry, ProfileRelAsKeyTitle.ID))
            .sendResponseAsStream(false)
            .build();

    ApiRequestContext requestContext =
        internalRequest(api, mcpRequest, stringParams, "collections", collectionId, "items")
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
      Optional<ApiRequestContext> mcpRequest,
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

    // references without their URI, unless the stored query selects how references are written
    boolean referencesSelected =
        Profile.of(extensionRegistry, executableQuery.getProfiles().toArray(String[]::new)).stream()
            .anyMatch(profile -> ProfileRelAsKeyTitle.PROFILE_SET.equals(profile.getProfileSet()));
    if (!referencesSelected) {
      executableQuery =
          new ImmutableQueryExpression.Builder()
              .from(executableQuery)
              .addProfiles(ProfileRelAsKeyTitle.ID)
              .build();
    }

    SearchQueriesHandler.QueryInputQuery queryInput =
        new ImmutableQueryInputQuery.Builder()
            .query(executableQuery)
            .featureProvider(providers.getFeatureProviderOrThrow(apiData))
            .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
            .minimumPageSize(Optional.ofNullable(coreConfiguration.getMinimumPageSize()))
            .defaultPageSize(Optional.ofNullable(coreConfiguration.getDefaultPageSize()))
            .maximumPageSize(Optional.ofNullable(coreConfiguration.getMaximumPageSize()))
            .isStoredQuery(true)
            .build();

    ApiRequestContext requestContext =
        internalRequest(api, mcpRequest, stringParams, "search", queryId)
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
    servers.values().forEach(HttpServletStatelessServerTransport::close);

    AppLifeCycle.super.onStop();
  }

  @Override
  public McpSchema getSchema(OgcApi api) {
    OgcApiDataV2 apiData = api.getData();
    String cacheKey = getCacheKey(apiData);

    if (!schemas.containsKey(cacheKey)) {
      removeOutdated(schemas, apiData.getId(), cacheKey, schema -> {});
      LOGGER.debug("Creating MCP schema for API {}", apiData.getId());

      McpConfiguration mcpConfiguration =
          apiData.getExtension(McpConfiguration.class).orElseThrow();

      List<StoredQueryExpression> storedQueries = storedQueryRepository.getAll(apiData);

      List<McpTool> storedQueryTools =
          McpToolUtils.filterAndCreateStoredQueries(
              storedQueries, mcpConfiguration, extensionRegistry, apiData);

      List<McpTool> collectionTools =
          McpToolUtils.filterAndCreateCollections(
              mcpConfiguration,
              api,
              extensionRegistry,
              List.of(
                  "bbox",
                  "datetime",
                  "ids",
                  "limit",
                  "offset",
                  "sortby",
                  "properties",
                  "exclude-properties",
                  "filter",
                  "zoom-level"));

      schemas.put(
          cacheKey,
          new ImmutableMcpSchema.Builder()
              .addAllTools(
                  Stream.concat(collectionTools.stream(), storedQueryTools.stream())
                      .collect(Collectors.toList()))
              .build());
    }
    return schemas.get(cacheKey);
  }

  // the tools are derived from the API and its stored queries; the stored queries can change
  // without a change of the API, for example when they are reloaded
  private String getCacheKey(OgcApiDataV2 apiData) {
    return String.format(
        "%s/%s/%d",
        apiData.getId(), apiData.getStableHash(), storedQueryRepository.getAll(apiData).hashCode());
  }

  // the server and the schema for a previous state of the API are no longer used
  private static <T> void removeOutdated(
      Map<String, T> cache, String apiId, String cacheKey, Consumer<T> onRemove) {
    cache
        .keySet()
        .removeIf(
            key -> {
              if (key.startsWith(apiId + "/") && !key.equals(cacheKey)) {
                Optional.ofNullable(cache.get(key)).ifPresent(onRemove);
                return true;
              }
              return false;
            });
  }
}
