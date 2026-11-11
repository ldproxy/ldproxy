/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.ii.ogcapi.processes.domain.AllowedUris;
import de.ii.xtraplatform.web.domain.HttpClient;
import de.ii.xtraplatform.web.domain.HttpClient.HttpResponse;
import de.ii.xtraplatform.xtralink.domain.Jobs;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Calls external APIs over HTTP, only at the allowed URIs: the OpenAPI documents, the requests of
 * the steps and the targets of redirects. The credentials of the caller of the workflow are never
 * sent, a request has only the headers of the parameters of its step.
 */
final class HttpExternalApis implements ExternalApis {

  static final int MAXIMUM_REDIRECTS = 5;

  private static final ObjectMapper JSON = Jobs.DEFAULT_MAPPER;
  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
  private static final String ACCEPT = "accept";
  private static final String CONTENT_TYPE = "content-type";
  private static final String AUTHORIZATION = "authorization";
  private static final String LOCATION = "location";
  private static final String OPENAPI_ACCEPT =
      "application/vnd.oai.openapi+json, application/vnd.oai.openapi;q=0.9, application/json;q=0.8,"
          + " application/yaml;q=0.7, */*;q=0.1";
  private static final String STEP_ACCEPT = "application/json, application/geo+json, */*;q=0.1";
  private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);
  private static final String GET = "GET";

  private final Sender client;
  private final List<String> allowedUris;

  /**
   * @param allowedUris the URIs that requests may use, see {@link AllowedUris}
   */
  HttpExternalApis(Sender client, List<String> allowedUris) {
    this.client = client;
    this.allowedUris = allowedUris;
  }

  /** Sends a request without following redirects, {@link HttpClient#send}. */
  @FunctionalInterface
  interface Sender {
    HttpResponse send(
        String method,
        String url,
        Map<String, String> headers,
        Optional<byte[]> body,
        long maximumSize);
  }

  @Override
  public ExternalApi api(String url, int maximumResponseSize) {
    checkAllowed(
        url,
        "The source description '%s' is not an API of this server and not an allowed external API.");
    Received received;
    try {
      received =
          send(GET, url, Map.of(ACCEPT, OPENAPI_ACCEPT), Optional.empty(), maximumResponseSize);
    } catch (IllegalArgumentException e) {
      throw new WorkflowException(
          WorkflowException.Kind.PROBLEM,
          String.format("The OpenAPI document '%s' cannot be read: %s", url, e.getMessage()),
          e);
    }
    if (!received.response().isSuccessful()) {
      throw WorkflowException.problem(
          "The OpenAPI document '%s' cannot be read, the status code is %d.",
          url, received.response().getStatus());
    }
    Map<?, ?> document;
    try {
      // YAML includes JSON
      document = YAML.readValue(received.response().getBody(), Map.class);
    } catch (IOException e) {
      throw new WorkflowException(
          WorkflowException.Kind.PROBLEM,
          String.format("The OpenAPI document '%s' is neither JSON nor YAML.", url),
          e);
    }
    return ExternalApi.of(url, received.url(), Objects.requireNonNullElse(document, Map.of()));
  }

  @Override
  public StepResponse dispatch(StepRequest request, int maximumResponseSize) {
    String url = request.url();
    checkAllowed(url, "The request of the step to '%s' is not allowed.");
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put(ACCEPT, STEP_ACCEPT);
    headers.putAll(request.headers());
    Optional<byte[]> body = Optional.empty();
    if (request.body().isPresent()) {
      try {
        body = Optional.of(JSON.writeValueAsBytes(request.body().get()));
      } catch (JsonProcessingException e) {
        throw new WorkflowException(
            WorkflowException.Kind.PROBLEM, "The content of the request cannot be written.", e);
      }
      headers.putIfAbsent(CONTENT_TYPE, "application/json");
    }
    try {
      HttpResponse response =
          send(request.method(), url, headers, body, maximumResponseSize).response();
      return new StepResponse(response.getStatus(), response.getHeaders(), body(response));
    } catch (IllegalArgumentException e) {
      return error(
          502, String.format("The request of the step to '%s' failed: %s", url, e.getMessage()));
    }
  }

  private void checkAllowed(String url, String message) {
    if (!AllowedUris.isAllowed(url, allowedUris)) {
      throw WorkflowException.problem(message, url);
    }
  }

  private record Received(String url, HttpResponse response) {}

  private record Request(
      String method, String url, Map<String, String> headers, Optional<byte[]> body) {}

  // follows redirects to allowed URIs
  private Received send(
      String method,
      String url,
      Map<String, String> headers,
      Optional<byte[]> body,
      int maximumResponseSize) {
    Request request = new Request(method, url, headers, body);
    for (int redirects = 0; ; redirects++) {
      HttpResponse response =
          client.send(
              request.method(),
              request.url(),
              request.headers(),
              request.body(),
              maximumResponseSize);
      String location = response.getHeaders().get(LOCATION);
      if (!REDIRECTS.contains(response.getStatus()) || Objects.isNull(location)) {
        return new Received(request.url(), response);
      }
      if (redirects == MAXIMUM_REDIRECTS) {
        throw new IllegalArgumentException(
            String.format("There are more than %d redirects.", MAXIMUM_REDIRECTS));
      }
      request = redirected(request, response.getStatus(), location);
    }
  }

  // a redirect to another host drops the authorization; like browsers, a 303, and a 301 or 302
  // after a POST, continue with a GET without content
  private Request redirected(Request request, int status, String location) {
    URI current = URI.create(request.url());
    URI target = current.resolve(location);
    if (!AllowedUris.isAllowed(target.toString(), allowedUris)) {
      throw new IllegalArgumentException(
          String.format("The redirect to '%s' is not allowed.", target));
    }
    Map<String, String> headers = new LinkedHashMap<>(request.headers());
    if (!Objects.equals(current.getHost(), target.getHost())) {
      headers.remove(AUTHORIZATION);
    }
    if (status == 303 || (status == 301 || status == 302) && !GET.equals(request.method())) {
      headers.remove(CONTENT_TYPE);
      return new Request(GET, target.toString(), headers, Optional.empty());
    }
    return new Request(request.method(), target.toString(), headers, request.body());
  }

  private static Optional<Object> body(HttpResponse response) {
    byte[] content = response.getBody();
    if (content.length == 0) {
      return Optional.empty();
    }
    try {
      return Optional.of(JSON.readValue(content, Object.class));
    } catch (IOException e) {
      return Optional.of(new String(content, StandardCharsets.UTF_8));
    }
  }

  private static StepResponse error(int status, String detail) {
    Map<String, Object> problem = new LinkedHashMap<>();
    problem.put("status", status);
    problem.put("detail", detail);
    return new StepResponse(
        status, Map.of(CONTENT_TYPE, "application/problem+json"), Optional.of(problem));
  }
}
