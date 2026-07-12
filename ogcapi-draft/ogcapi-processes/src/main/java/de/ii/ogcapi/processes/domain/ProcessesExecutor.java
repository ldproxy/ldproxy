/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain;

import de.ii.ogcapi.processes.domain.model.ogc.OgcExecute;
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
   * @param executeRequest details about the requested exeuction
   * @return the process results
   */
  Map<String, Object> executeSync(String processId, OgcExecute executeRequest);

  /**
   * Executes a process asynchronously and returns a job ID for retrieving status and results later.
   *
   * @param processId the process to execute
   * @param executeRequest details about the requested exeuction
   * @return a unique job ID
   */
  String executeAsync(String processId, OgcExecute executeRequest);

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
