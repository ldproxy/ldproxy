/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app;

import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestAuthorizer;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableStaticRequestContext;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.mcp.domain.McpTool;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.web.domain.URICustomizer;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.ws.rs.core.MediaType;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Helpers for the tools that execute a request to a resource of the API. */
final class McpInternalRequests {

  // the encoding of the features in the result of a tool
  static final ApiMediaType GEOJSON =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("application", "geo+json"))
          .label("GeoJSON")
          .parameter("json")
          .build();

  private McpInternalRequests() {}

  // the other encodings of the operation of the tool, as in a request to the API; the response then
  // links to them, for example to the HTML page with a map, and its self link keeps the query
  // parameters
  static Set<ApiMediaType> alternateMediaTypes(McpTool tool, ApiMediaType mediaType) {
    return tool.getOperation().flatMap(ApiOperation::getSuccess).stream()
        .flatMap(response -> response.getContent().values().stream())
        .map(ApiMediaTypeContent::getOgcApiMediaType)
        .filter(candidate -> !candidate.type().isCompatible(mediaType.type()))
        .collect(Collectors.toUnmodifiableSet());
  }

  // the context of an internal request to a resource of the API; it is derived from the MCP request
  // like a request to the API (forwarded host and prefix), so the links in the response are the
  // same as in the API; the parameters of the tool call are the query parameters of the request, so
  // that the link to the next page keeps them
  static ImmutableStaticRequestContext.Builder internalRequest(
      AppContext appContext,
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

    return builder
        .requestUri(URI.create(uri.toString()))
        .user(mcpRequest.flatMap(ApiRequestContext::getUser));
  }

  // a tool call requires the same permissions as a request to the operation of the tool; a policy
  // may change the request, e.g. add a filter, so the request that is returned is executed
  static ApiRequestContext authorize(
      ApiRequestAuthorizer authorizer,
      McpTool tool,
      ApiRequestContext internalRequest,
      Optional<byte[]> body) {
    return authorizer.checkAuthorization(
        internalRequest,
        tool.getOperation()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "The MCP tool '%s' has no operation.".formatted(tool.getId()))),
        internalRequest.getUser(),
        body);
  }

  // the arguments of a tool call as the values of query parameters
  static Map<String, String> queryParameterValues(
      Map<String, Object> arguments,
      List<OgcApiQueryParameter> queryParameters,
      Function<OgcApiQueryParameter, Schema<?>> schemaOf) {
    return arguments.entrySet().stream()
        .filter(e -> Objects.nonNull(e.getValue()))
        .collect(
            Collectors.toMap(
                Entry::getKey,
                e -> {
                  Schema<?> schema =
                      queryParameters.stream()
                          .filter(p -> p.getName().equals(e.getKey()))
                          .findFirst()
                          .map(schemaOf)
                          .orElse(null);
                  return parseParameterValue(schema, e.getValue());
                }));
  }

  private static String parseParameterValue(Schema<?> schema, Object value) {
    if (schema != null && "array".equals(schema.getType())) {
      if (value instanceof List<?> list) {
        return list.stream().map(Object::toString).collect(Collectors.joining(","));
      }
      if (value instanceof String str) {
        return str.replaceAll("^\\[|\\]$", "");
      }
    }
    return value.toString();
  }
}
