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
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.features.core.domain.FeaturesQuery;
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler;
import de.ii.ogcapi.features.search.domain.StoredQueryRepository;
import de.ii.ogcapi.foundation.domain.ApiMetadata;
import de.ii.ogcapi.foundation.domain.ApiRequestAuthorizer;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.mcp.domain.ImmutableMcpSchema;
import de.ii.ogcapi.mcp.domain.McpConfiguration;
import de.ii.ogcapi.mcp.domain.McpSchema;
import de.ii.ogcapi.mcp.domain.McpServer;
import de.ii.ogcapi.mcp.domain.McpTool;
import de.ii.ogcapi.processes.domain.ProcessesExecutor;
import de.ii.ogcapi.processes.domain.model.ProcessRepository;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.base.domain.AppLifeCycle;
import de.ii.xtraplatform.base.domain.Jackson;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.base.domain.resiliency.VolatileUnavailableException;
import de.ii.xtraplatform.cql.domain.Cql;
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
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.ResourceLink;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServlet;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status.Family;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class McpServerImpl implements McpServer, AppLifeCycle {

  private static final Logger LOGGER = LoggerFactory.getLogger(McpServerImpl.class);
  private static final Set<String> KEPT_RELS = Set.of("self", "next", "prev");
  private static final String HTML = "text/html";

  private final Map<String, McpSchema> schemas = new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper;
  private final ObjectMapper mcpObjectMapper;
  private final Map<String, HttpServletStatelessServerTransport> servers =
      new ConcurrentHashMap<>();
  // the order of the sources is the order of the tools
  private final List<McpToolSource> toolSources;

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
      Cql cql,
      ProcessRepository processRepository,
      ProcessesExecutor processesExecutor,
      ApiRequestAuthorizer apiRequestAuthorizer) {
    this.objectMapper = jackson.getDefaultObjectMapper();
    this.mcpObjectMapper = mcpObjectMapper(objectMapper);
    this.toolSources =
        List.of(
            new McpToolsCollections(
                appContext,
                extensionRegistry,
                providers,
                ogcApiFeaturesQuery,
                featuresCoreQueriesHandler,
                apiRequestAuthorizer,
                mcpObjectMapper),
            new McpToolsStoredQueries(
                appContext,
                extensionRegistry,
                storedQueryRepository,
                providers,
                searchQueriesHandler,
                cql,
                apiRequestAuthorizer,
                mcpObjectMapper),
            new McpToolsProcesses(
                appContext,
                extensionRegistry,
                processRepository,
                processesExecutor,
                apiRequestAuthorizer,
                objectMapper));
  }

  @Override
  public HttpServlet getServlet(OgcApi api) {
    String cacheKey = getCacheKey(api);
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
    Optional<Integer> maxResultSize =
        api.getData()
            .getExtension(McpConfiguration.class)
            .flatMap(McpConfiguration::getMaxResultSize);

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
            "MCP tool '{}' of API '{}' is not available, its schema is not a valid JSON Schema"
                + " 2020-12 document: {}",
            tool.getId(),
            api.getId(),
            schemaError.get());
        continue;
      }

      Optional<McpToolSource> source = toolSource(tool);
      if (source.isEmpty()) {
        LOGGER.warn("MCP tool '{}' of API '{}' has no source", tool.getId(), api.getId());
        continue;
      }

      toolSpecifications.add(
          new SyncToolSpecification(
              Tool.builder(tool.getId(), inputSchema)
                  .title(tool.getName())
                  .description(tool.getDescription())
                  .outputSchema(outputSchema)
                  .annotations(annotations(tool))
                  .build(),
              (context, request) -> {
                try {
                  Map<String, Object> result =
                      source
                          .get()
                          .call(
                              api,
                              tool,
                              Objects.requireNonNullElse(request.arguments(), Map.of()),
                              mcpRequest(context));
                  String text = mcpObjectMapper.writeValueAsString(result);
                  Optional<String> tooLarge = tooLarge(tool, result, text.length(), maxResultSize);
                  if (tooLarge.isPresent()) {
                    if (LOGGER.isDebugEnabled()) {
                      LOGGER.debug(
                          "Result of MCP tool '{}' not returned, {} characters exceed the limit of"
                              + " {}",
                          tool.getId(),
                          text.length(),
                          maxResultSize.get());
                    }
                    CallToolResult.Builder errorResult =
                        CallToolResult.builder().addTextContent(tooLarge.get()).isError(true);
                    resourceLink(tool, result).ifPresent(errorResult::addContent);
                    return errorResult.build();
                  }
                  CallToolResult.Builder toolResult =
                      CallToolResult.builder()
                          .addTextContent(text)
                          .isError(false)
                          .structuredContent(result);
                  resourceLink(tool, result).ifPresent(toolResult::addContent);
                  return toolResult.build();
                } catch (Throwable e) {
                  return CallToolResult.builder()
                      .addTextContent(toolError(tool.getId(), e))
                      .isError(true)
                      .build();
                }
              }));
    }

    return io.modelcontextprotocol.server.McpServer.sync(transport)
        .serverInfo(serverInfo(api.getData(), api.getUri()))
        .instructions(instructions(api.getData()))
        .capabilities(ServerCapabilities.builder().tools(true).build())
        .jsonMapper(jsonMapper)
        .jsonSchemaValidator(jsonSchemaValidator)
        // tool names are derived from collection and query ids, which are not restricted to the
        // characters recommended by MCP; warn instead of failing the server
        .strictToolNameValidation(false)
        .tools(toolSpecifications)
        .build();
  }

  private Optional<McpToolSource> toolSource(McpTool tool) {
    return toolSources.stream()
        .filter(source -> tool.getId().startsWith(source.getPrefix()))
        .findFirst();
  }

  // the tools of features and stored queries only read data; for other tools, such as a process,
  // the effects are unknown, the hints are left to their defaults
  private static ToolAnnotations annotations(McpTool tool) {
    return tool.isReadOnly()
        ? new ToolAnnotations(tool.getName(), true, false, true, false, false)
        : new ToolAnnotations(tool.getName(), null, null, null, null, null);
  }

  // the server is the API, not the software: a client sees which API it is connected to and which
  // version of it, the version is the one in the OpenAPI definition, with the same default
  static Implementation serverInfo(OgcApiDataV2 apiData, URI landingPage) {
    return new Implementation(
        apiData.getId(),
        apiData.getLabel(),
        apiVersion(apiData).orElse("1.0.0"),
        null,
        null,
        landingPage.toString());
  }

  // the background shared by all tools, the tool descriptions start with what the tool is for; the
  // server information does not reach the model, so a configured version is also stated here
  static String instructions(OgcApiDataV2 apiData) {
    String instructions =
        apiData
            .getDescription()
            .map(description -> apiData.getLabel() + ": " + description)
            .orElse(apiData.getLabel());

    return apiVersion(apiData)
        .map(version -> instructions + "\n\nVersion of the API: " + version)
        .orElse(instructions);
  }

  private static Optional<String> apiVersion(OgcApiDataV2 apiData) {
    return apiData.getMetadata().flatMap(ApiMetadata::getVersion);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> toMap(Schema<?> schema) throws IOException {
    return McpSchemaVersions.toJsonSchema2020(
        objectMapper.readValue(Json.pretty(schema), Map.class));
  }

  private static Optional<ApiRequestContext> mcpRequest(McpTransportContext context) {
    return context.get(McpServer.REQUEST_CONTEXT) instanceof ApiRequestContext requestContext
        ? Optional.of(requestContext)
        : Optional.empty();
  }

  // the error of a tool call as the model sees it: the message of an error in the arguments, so the
  // call can be corrected; for an error of the server only that it is one, like the API does, since
  // its message (for example of the database) does not help the client and may disclose internals
  static String toolError(String toolId, Throwable error) {
    String prefix = "Error executing tool '" + toolId + "': ";
    for (Throwable e = error; Objects.nonNull(e); e = e.getCause() == e ? null : e.getCause()) {
      if (e instanceof IllegalArgumentException
          || e instanceof WebApplicationException webError
              && webError.getResponse().getStatusInfo().getFamily() == Family.CLIENT_ERROR) {
        // an error in the request, not of the server: like the API, only the message, without the
        // stacktrace that LogContext would log regardless of the level
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("Error in the call of MCP tool '{}': {}", toolId, e.getMessage());
        }
        return prefix + e.getMessage();
      }
      if (e instanceof VolatileUnavailableException) {
        LogContext.errorAsWarn(LOGGER, error, "Error executing MCP tool '{}'", toolId);
        return prefix + "the data is currently not available. Please try again later.";
      }
    }
    long id = ThreadLocalRandom.current().nextLong();
    LogContext.error(
        LOGGER,
        error,
        "Error executing MCP tool '{}', error ID {}",
        toolId,
        String.format("%016x", id));
    return prefix
        + String.format(
            "an error of the server, not caused by the arguments of the call; it has been logged"
                + " (error ID %016x). The data may be temporarily unavailable, trying again later"
                + " may help.",
            id);
  }

  // a result above the configured size would fill the context of the model, or exceed it, so the
  // call fails instead with a message the model can act on: how large the result is, how to ask for
  // less, and where the complete result is, for a client that can load it without the model
  static Optional<String> tooLarge(
      McpTool tool, Map<String, Object> result, int size, Optional<Integer> maxResultSize) {
    if (maxResultSize.isEmpty() || size <= maxResultSize.get()) {
      return Optional.empty();
    }
    Optional<Integer> features =
        result.get("features") instanceof List<?> list
            ? Optional.of(list.size())
            : Optional.empty();
    return Optional.of(
        "Error executing tool '"
            + tool.getId()
            + "': the result has "
            + size
            + " characters"
            + features.map(n -> " (" + n + " features)").orElse("")
            + ", more than the "
            + maxResultSize.get()
            + " characters this server returns. Ask for less: leave out properties that are not"
            + " needed, especially the geometry, select a smaller area or fewer objects, or use a"
            + " smaller limit and page through the result. For counts, sums or a distribution over"
            + " an area, use a tool that aggregates, if there is one."
            + resourceLink(tool, result)
                .map(
                    link ->
                        (isPaged(result)
                                ? " This page of the result is available at "
                                    + link.uri()
                                    + ", the other pages through the links \"next\" and \"prev\""
                                    + " of each page; a client can load them from there directly,"
                                : " The complete result is available at "
                                    + link.uri()
                                    + "; a client can load it from there directly,")
                            + " for example to show it on a map, without passing it through the"
                            + " model.")
                .orElse(""));
  }

  // a result is one page of several when it links a next or a previous page
  private static boolean isPaged(Map<String, Object> result) {
    return result.get("links") instanceof List<?> links
        && links.stream()
            .anyMatch(
                link ->
                    link instanceof Map<?, ?> map
                        && ("next".equals(map.get("rel")) || "prev".equals(map.get("rel"))));
  }

  // of the links in a response, a client of a tool needs those to other pages and those to the same
  // result in the HTTP API: as data, for a client that downloads the result instead of reading it,
  // and as HTML, for the person, who can open it on a map; a model cannot follow a link, so the
  // offset of the next page is also stated as nextOffset, at the start of the result (see
  // McpToolUtils.PAGING_HINT); the links of the features are left out
  static Map<String, Object> forToolResult(Map<String, Object> result) {
    List<?> keptLinks =
        result.get("links") instanceof List<?> links
            ? links.stream().filter(McpServerImpl::isKeptLink).toList()
            : List.of();
    Optional<Long> nextOffset =
        keptLinks.stream()
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
          if ("links".equals(key)) {
            if (!keptLinks.isEmpty()) {
              forTool.put(key, keptLinks);
            }
          } else if ("features".equals(key) && value instanceof List<?> features) {
            forTool.put(key, features.stream().map(McpServerImpl::withoutLinks).toList());
          } else {
            forTool.put(key, value);
          }
          if ("type".equals(key)) {
            nextOffset.ifPresent(offset -> forTool.put("nextOffset", offset));
          }
        });
    return forTool;
  }

  private static boolean isKeptLink(Object link) {
    return link instanceof Map<?, ?> map
        && (KEPT_RELS.contains(map.get("rel"))
            || "alternate".equals(map.get("rel")) && HTML.equals(map.get("type")));
  }

  // the result of the call in the HTTP API as a resource the client can read or store, for example
  // as a file, so that the data can be processed without copying it from the text content
  static Optional<ResourceLink> resourceLink(McpTool tool, Map<String, Object> result) {
    return result.get("links") instanceof List<?> links
        ? links.stream()
            .filter(link -> link instanceof Map<?, ?> map && "self".equals(map.get("rel")))
            .map(link -> (Map<?, ?>) link)
            .filter(link -> link.get("href") instanceof String)
            .findFirst()
            .map(
                link ->
                    ResourceLink.builder()
                        .name(tool.getId())
                        .title(tool.getName())
                        .uri((String) link.get("href"))
                        .mimeType(link.get("type") instanceof String type ? type : null)
                        .description(
                            "The result of this call from the HTTP API, for example to save it as"
                                + " a file.")
                        .build())
        : Optional.empty();
  }

  private static Object withoutLinks(Object feature) {
    if (!(feature instanceof Map<?, ?> map) || !map.containsKey("links")) {
      return feature;
    }
    Map<Object, Object> copy = new LinkedHashMap<>(map);
    copy.remove("links");
    return copy;
  }

  @Override
  public void onStop() {
    servers.values().forEach(HttpServletStatelessServerTransport::close);

    AppLifeCycle.super.onStop();
  }

  @Override
  public McpSchema getSchema(OgcApi api) {
    OgcApiDataV2 apiData = api.getData();
    String cacheKey = getCacheKey(api);

    if (!schemas.containsKey(cacheKey)) {
      removeOutdated(schemas, apiData.getId(), cacheKey, schema -> {});
      LOGGER.debug("Creating MCP schema for API {}", apiData.getId());

      McpConfiguration mcpConfiguration =
          apiData.getExtension(McpConfiguration.class).orElseThrow();

      schemas.put(
          cacheKey,
          new ImmutableMcpSchema.Builder()
              .addAllTools(
                  toolSources.stream()
                      .flatMap(source -> source.getTools(api, mcpConfiguration).stream())
                      .toList())
              .build());
    }
    return schemas.get(cacheKey);
  }

  // the tools are derived from the API and from values such as the stored queries, which can change
  // without a change of the API, for example when they are reloaded
  private String getCacheKey(OgcApi api) {
    return String.format(
        "%s/%s/%s",
        api.getId(),
        api.getData().getStableHash(),
        toolSources.stream()
            .map(source -> String.valueOf(source.getFingerprint(api)))
            .collect(Collectors.joining(",")));
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
