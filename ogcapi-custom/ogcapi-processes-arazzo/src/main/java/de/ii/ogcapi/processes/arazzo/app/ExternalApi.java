/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import de.ii.ogcapi.processes.arazzo.app.StepDispatcher.Operation;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An external API, described by an OpenAPI 3 document: its operations and the URL of the server of
 * each operation. Only GET and POST operations can be steps of a workflow.
 */
final class ExternalApi {

  private static final Set<String> METHODS =
      Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");
  private static final Set<String> SUPPORTED_METHODS = Set.of("GET", "POST");
  private static final Pattern VARIABLE = Pattern.compile("\\{([^}]+)}");

  private final String url;
  private final List<Operation> operations;

  private ExternalApi(String url, List<Operation> operations) {
    this.url = url;
    this.operations = operations;
  }

  /**
   * @param url the URL of the source description, the id of the API in the operations
   * @param location the URL from which the document was read, after redirects; relative server URLs
   *     are relative to it
   * @param document the OpenAPI document
   * @throws WorkflowException if the document is not an OpenAPI 3 document
   */
  static ExternalApi of(String url, String location, Map<?, ?> document) {
    if (!(document.get("openapi") instanceof String version) || !version.startsWith("3.")) {
      throw WorkflowException.problem(
          "The source description '%s' is not an OpenAPI 3 document.", url);
    }
    URI base = URI.create(location);
    Optional<String> documentServer = server(document, base);
    List<Operation> operations = new ArrayList<>();
    if (document.get("paths") instanceof Map<?, ?> paths) {
      paths.forEach(
          (path, item) -> {
            if (item instanceof Map<?, ?> pathItem) {
              operations.addAll(
                  operations(url, String.valueOf(path), pathItem, documentServer, base));
            }
          });
    }
    return new ExternalApi(url, List.copyOf(operations));
  }

  // the operations of a path item, the server of the path item replaces the one of the document
  private static List<Operation> operations(
      String url, String path, Map<?, ?> pathItem, Optional<String> documentServer, URI base) {
    Optional<String> pathServer = server(pathItem, base).or(() -> documentServer);
    List<Operation> operations = new ArrayList<>();
    pathItem.forEach(
        (method, value) -> {
          if (METHODS.contains(String.valueOf(method)) && value instanceof Map<?, ?> operation) {
            operations.add(
                operation(
                    url,
                    path,
                    String.valueOf(method),
                    operation,
                    server(operation, base).or(() -> pathServer),
                    base));
          }
        });
    return operations;
  }

  private static Operation operation(
      String url,
      String path,
      String method,
      Map<?, ?> operation,
      Optional<String> server,
      URI base) {
    String httpMethod = method.toUpperCase(Locale.ROOT);
    return new Operation(
        url,
        operation.get("operationId") instanceof String id ? id : "",
        httpMethod,
        path,
        SUPPORTED_METHODS.contains(httpMethod),
        // without servers, the server is the root of the location of the document
        Optional.of(server.orElse(base.resolve("/").toString())));
  }

  // the first server of the object, the variables replaced by their defaults
  private static Optional<String> server(Map<?, ?> object, URI base) {
    if (!(object.get("servers") instanceof List<?> servers)
        || servers.isEmpty()
        || !(servers.get(0) instanceof Map<?, ?> server)
        || !(server.get("url") instanceof String serverUrl)) {
      return Optional.empty();
    }
    Map<?, ?> variables = server.get("variables") instanceof Map<?, ?> map ? map : Map.of();
    return Optional.of(base.resolve(withDefaults(serverUrl, variables)).toString());
  }

  private static String withDefaults(String serverUrl, Map<?, ?> variables) {
    Matcher matcher = VARIABLE.matcher(serverUrl);
    StringBuilder resolved = new StringBuilder();
    while (matcher.find()) {
      Object value =
          variables.get(matcher.group(1)) instanceof Map<?, ?> variable
              ? variable.get("default")
              : null;
      if (Objects.isNull(value)) {
        throw WorkflowException.problem(
            "The server variable '%s' of '%s' has no default value.", matcher.group(1), serverUrl);
      }
      matcher.appendReplacement(resolved, Matcher.quoteReplacement(String.valueOf(value)));
    }
    matcher.appendTail(resolved);
    return resolved.toString();
  }

  String url() {
    return url;
  }

  Optional<Operation> operation(String operationId) {
    return operations.stream().filter(op -> op.operationId().equals(operationId)).findFirst();
  }

  Optional<Operation> operation(String method, String path) {
    return operations.stream()
        .filter(op -> op.method().equalsIgnoreCase(method) && op.path().equals(path))
        .findFirst();
  }
}
