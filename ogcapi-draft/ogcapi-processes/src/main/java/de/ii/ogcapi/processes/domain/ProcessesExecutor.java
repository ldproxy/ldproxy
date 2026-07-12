/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain;

import java.util.Map;
import java.util.Optional;

public interface ProcessesExecutor {

  enum StatusCode {
    ACCEPTED,
    RUNNING,
    SUCCESSFUL,
    FAILED,
    DISMISSED
  }

  /**
   * Executes a process synchronously and returns the results immediately.
   *
   * @param processId the process to execute
   * @param inputs input parameters
   * @param outputs output selection.
   * @return the process results
   */
  Map<String, Object> executeSync(
      String processId, Map<String, Object> inputs, Optional<Map<String, String>> outputs);

  /**
   * Executes a process asynchronously and returns a job ID for retrieving status and results later.
   *
   * @param processId the process to execute
   * @param inputs input parameters
   * @param outputs requested output fields. If empty, all possible outputs are send and if an empty
   *     map, no respond body is generated.
   * @return a unique job ID
   */
  String executeAsync(
      String processId, Map<String, Object> inputs, Optional<Map<String, String>> outputs);

  /**
   * Returns the current status of a job, or empty if the job ID is unknown.
   *
   * @param jobId the job ID returned by {@link #executeAsync}
   */
  Optional<StatusCode> status(String jobId);

  /**
   * Returns the results of a successfully completed job.
   *
   * @param jobId the job ID returned by {@link #executeAsync}
   * @throws IllegalStateException if the job has not finished successfully
   */
  Map<String, Object> result(String jobId);
}
