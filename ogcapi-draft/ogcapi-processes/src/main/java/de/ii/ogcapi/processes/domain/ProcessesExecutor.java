/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain;

import de.ii.ogcapi.processes.domain.model.StatusInfo;
import de.ii.ogcapi.processes.domain.model.ogc.OgcExecute;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProcessesExecutor {

  Map<String, Object> executeSync(String processId, OgcExecute executeRequest);

  StatusInfo executeAsync(String processId, OgcExecute executeRequest);

  Optional<StatusInfo> getStatusInfo(String jobId);

  Map<String, Object> getResults(String jobId);

  List<String> getJobs();

  Optional<StatusInfo> dismissJob(String jobId);
}
