/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain;

import de.ii.ogcapi.processes.domain.model.Process;
import de.ii.ogcapi.processes.domain.model.StatusInfo;
import de.ii.ogcapi.processes.domain.model.ogc.OgcExecute;
import java.util.Map;
import java.util.Optional;

public interface ProcessesExecutor {

  Map<String, Object> executeSync(Process process, OgcExecute executeRequest);

  StatusInfo executeAsync(Process process, OgcExecute executeRequest);

  Optional<StatusInfo> getStatusInfo(String jobId);

  Optional<Map<String, Object>> getResults(String jobId);

  Optional<StatusInfo> dismissJob(String jobId);
}
