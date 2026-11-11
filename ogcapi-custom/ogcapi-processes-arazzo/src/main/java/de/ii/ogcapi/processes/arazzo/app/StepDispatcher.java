/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import java.util.Optional;

/**
 * Runs the requests of the steps of workflows: every request of a step passes here, so that the
 * identity of the caller can be added in one place later. In version 1, the requests are internal
 * requests to the APIs of this instance, without the identity of a user: a step has the permissions
 * of an anonymous request.
 */
public interface StepDispatcher {

  /**
   * @param url the url of an OpenAPI source description, absolute or relative to the API
   * @param apiId the id of the API that executes the workflow
   * @return the id of the API of this instance that the OpenAPI document describes
   */
  Optional<String> apiOfSource(String url, String apiId);

  /**
   * @return the operation with the id in the API, if it exists
   */
  Optional<Operation> operation(String apiId, String operationId);

  /**
   * @param path the path of the operation in the OpenAPI document, e.g. {@code
   *     /collections/pand/items/{featureId}}
   * @return the operation with the method and path in the API, if it exists
   */
  Optional<Operation> operation(String apiId, String method, String path);

  /**
   * @param maximumResponseSize the maximum size of the content of the response in bytes
   * @return the response; a client error of the request, e.g. an invalid parameter or a missing
   *     permission, is a response with the status code of the error
   */
  StepResponse dispatch(StepRequest request, int maximumResponseSize);

  /**
   * An operation of an API.
   *
   * @param apiId the id of the API
   * @param operationId the id of the operation
   * @param method the HTTP method
   * @param path the path below the API, with the names of the path parameters in braces
   * @param isSupported whether the operation can be a step of a workflow
   * @param server the URL of the server of an external API, the path is relative to it; empty for
   *     an API of this server
   */
  // a class, not a record: the documentation tool of the build does not support public records
  @SuppressWarnings("PMD.DataClass") // a value, a record if the documentation tool allowed it
  final class Operation {

    private final String apiId;
    private final String operationId;
    private final String method;
    private final String path;
    private final boolean isSupported;
    private final Optional<String> server;

    public Operation(
        String apiId, String operationId, String method, String path, boolean isSupported) {
      this(apiId, operationId, method, path, isSupported, Optional.empty());
    }

    public Operation(
        String apiId,
        String operationId,
        String method,
        String path,
        boolean isSupported,
        Optional<String> server) {
      this.apiId = apiId;
      this.operationId = operationId;
      this.method = method;
      this.path = path;
      this.isSupported = isSupported;
      this.server = server;
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

    public boolean isSupported() {
      return isSupported;
    }

    public Optional<String> server() {
      return server;
    }
  }
}
