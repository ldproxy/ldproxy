/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The log of the executions of workflows, to analyse a workflow: with the level DEBUG each step
 * with its request, status code and duration, with TRACE also the content of the request and the
 * beginning of the response. Nested workflows are indented. Headers are never logged, they may
 * contain credentials, and neither are the values of the inputs that the server sets or that have
 * the format password.
 */
final class WorkflowLog {

  private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowLog.class);
  private static final int MAXIMUM_CONTENT = 2000;

  private final Set<String> hiddenInputs;

  /**
   * @param hiddenInputs the names of inputs whose values are not logged
   */
  WorkflowLog(Set<String> hiddenInputs) {
    this.hiddenInputs = hiddenInputs;
  }

  /**
   * @param passwords the names of the inputs with the format password in the input schema; their
   *     values are not logged either
   */
  void started(String workflowId, int depth, Map<String, Object> inputs, Set<String> passwords) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "{}workflow '{}' started, inputs {}",
          indent(depth),
          workflowId,
          inputs.entrySet().stream()
              .map(
                  input ->
                      input.getKey()
                          + "="
                          + (hiddenInputs.contains(input.getKey())
                                  || passwords.contains(input.getKey())
                              ? "***"
                              : shorten(RuntimeExpressions.stringify(input.getValue()))))
              .collect(Collectors.joining(", ", "{", "}")));
    }
  }

  void finished(String workflowId, int depth, long millis, int steps) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "{}workflow '{}' finished in {} ms, {} steps in this execution so far",
          indent(depth),
          workflowId,
          millis,
          steps);
    }
  }

  void failed(String workflowId, long millis, String message) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("workflow '{}' failed after {} ms: {}", workflowId, millis, message);
    }
  }

  void request(
      String workflowId,
      String stepId,
      int depth,
      StepRequest request,
      StepResponse response,
      long millis,
      boolean success) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "{}{}/{}: {} {} -> {} in {} ms{}",
          indent(depth),
          workflowId,
          stepId,
          request.method(),
          url(request),
          response.statusCode(),
          millis,
          success ? "" : ", the step failed");
    }
    if (LOGGER.isTraceEnabled()) {
      request
          .body()
          .ifPresent(
              body ->
                  LOGGER.trace(
                      "{}  request content: {}",
                      indent(depth),
                      shorten(RuntimeExpressions.stringify(body))));
      response
          .body()
          .ifPresent(
              body ->
                  LOGGER.trace(
                      "{}  response content: {}",
                      indent(depth),
                      shorten(RuntimeExpressions.stringify(body))));
    }
  }

  void callsWorkflow(String workflowId, String stepId, int depth, String target, int items) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "{}{}/{}: calls workflow '{}'{}",
          indent(depth),
          workflowId,
          stepId,
          target,
          items < 0 ? "" : String.format(" for %d items", items));
    }
  }

  void item(int depth, int index, int count, Object item) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "{}  item {}/{}: {}",
          indent(depth),
          index,
          count,
          shorten(RuntimeExpressions.stringify(item)));
    }
  }

  void stepFailed(String workflowId, String stepId, int depth) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("{}{}/{}: the step failed", indent(depth), workflowId, stepId);
    }
  }

  void retry(String workflowId, String stepId, int depth, long millis) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("{}{}/{}: retry after {} ms", indent(depth), workflowId, stepId, millis);
    }
  }

  // the requests to the APIs of this server have no server, the path starts with the API id
  private static String url(StepRequest request) {
    return request.server().isPresent() ? request.url() : "/" + request.apiId() + request.url();
  }

  private static String indent(int depth) {
    return "  ".repeat(depth);
  }

  private static String shorten(String value) {
    return value.length() > MAXIMUM_CONTENT
        ? value.substring(0, MAXIMUM_CONTENT) + "... (" + value.length() + " characters)"
        : value;
  }
}
