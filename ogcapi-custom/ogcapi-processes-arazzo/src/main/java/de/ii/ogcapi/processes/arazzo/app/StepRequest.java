/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * The request of a step to an operation of an API of this instance.
 *
 * @param apiId the id of the API
 * @param operationId the id of the operation in the OpenAPI document of the API
 * @param method the HTTP method
 * @param path the path below the API, with the values of the path parameters, e.g. {@code
 *     /collections/pand/items}
 * @param pathParameters the values of the path parameters
 * @param query the values of the query parameters
 * @param headers the values of the header parameters, the names in lower case
 * @param body the content of the request, a JSON value, if any
 */
// a class, not a record: the documentation tool of the build does not support public records
@SuppressWarnings("PMD.DataClass") // a value, a record if the documentation tool allowed it
public final class StepRequest {

  private final String apiId;
  private final String operationId;
  private final String method;
  private final String path;
  private final Map<String, String> pathParameters;
  private final Map<String, String> query;
  private final Map<String, String> headers;
  private final Optional<Object> body;

  public StepRequest(
      String apiId,
      String operationId,
      String method,
      String path,
      Map<String, String> pathParameters,
      Map<String, String> query,
      Map<String, String> headers,
      Optional<Object> body) {
    this.apiId = apiId;
    this.operationId = operationId;
    this.method = method;
    this.path = path;
    this.pathParameters = pathParameters;
    this.query = query;
    this.headers = headers;
    this.body = body;
  }

  public String apiId() {
    return apiId;
  }

  public String operationId() {
    return operationId;
  }

  public String method() {
    return method;
  }

  public String path() {
    return path;
  }

  public Map<String, String> pathParameters() {
    return pathParameters;
  }

  public Map<String, String> query() {
    return query;
  }

  public Map<String, String> headers() {
    return headers;
  }

  public Optional<Object> body() {
    return body;
  }

  /** The URL of the request, relative to the API, for {@code $url}. */
  public String url() {
    StringBuilder url = new StringBuilder(path);
    String separator = "?";
    for (Map.Entry<String, String> parameter : query.entrySet()) {
      url.append(separator)
          .append(encode(parameter.getKey()))
          .append('=')
          .append(encode(parameter.getValue()));
      separator = "&";
    }
    return url.toString();
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
