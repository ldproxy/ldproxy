/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain;

import de.ii.ogcapi.processes.domain.model.OgcProcess;
import de.ii.ogcapi.processes.domain.model.OgcStatusInfo;
import de.ii.ogcapi.processes.domain.model.web.ExecuteRequest;
import java.util.Map;
import java.util.Optional;

public interface ProcessesExecutor {

  Map<String, Object> executeSync(OgcProcess process, ExecuteRequest executeRequest);

  OgcStatusInfo executeAsync(OgcProcess process, ExecuteRequest executeRequest);

  Optional<OgcStatusInfo> getStatusInfo(String jobId);

  Optional<Map<String, Object>> getResults(String jobId);

  Optional<OgcStatusInfo> dismissJob(String jobId);
}
