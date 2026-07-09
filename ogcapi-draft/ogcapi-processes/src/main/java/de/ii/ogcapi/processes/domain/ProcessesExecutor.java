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

  Map<String, Object> executeSync(String processId, Map<String, Object> inputs);

  String executeAsync(String processId, Map<String, Object> inputs);

  Optional<StatusCode> status(String jobId);

  Optional<String> result(String jobId);
}
