/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain;

import de.ii.ogcapi.common.domain.async.OgcApiJobStatus;
import de.ii.ogcapi.processes.domain.model.OgcExecute;
import de.ii.ogcapi.processes.domain.model.ProcessData;
import java.util.Map;

public interface ProcessesExecutor {

  default Map<String, Object> executeSync(
      String apiId, ProcessData process, OgcExecute executeRequest, long timeoutMillis) {
    return executeSync(apiId, process, executeRequest, ExecutionRequestContext.NONE, timeoutMillis);
  }

  /**
   * @param requestContext the context of the request, which the job carries to the steps
   */
  Map<String, Object> executeSync(
      String apiId,
      ProcessData process,
      OgcExecute executeRequest,
      ExecutionRequestContext requestContext,
      long timeoutMillis);

  default OgcApiJobStatus executeAsync(
      String apiId,
      ProcessData process,
      OgcExecute executeRequest,
      int callbackRetries,
      long ttlSeconds) {
    return executeAsync(
        apiId, process, executeRequest, ExecutionRequestContext.NONE, callbackRetries, ttlSeconds);
  }

  /**
   * @param requestContext the context of the request, which the job carries to the steps
   */
  OgcApiJobStatus executeAsync(
      String apiId,
      ProcessData process,
      OgcExecute executeRequest,
      ExecutionRequestContext requestContext,
      int callbackRetries,
      long ttlSeconds);
}
