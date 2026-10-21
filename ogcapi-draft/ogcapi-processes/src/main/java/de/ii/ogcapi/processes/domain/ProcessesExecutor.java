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
import de.ii.ogcapi.processes.domain.model.OgcProcess;
import java.util.Map;

public interface ProcessesExecutor {

  Map<String, Object> executeSync(String apiId, OgcProcess process, OgcExecute executeRequest);

  OgcApiJobStatus executeAsync(
      String apiId,
      OgcProcess process,
      OgcExecute executeRequest,
      int callbackRetries,
      long ttlSeconds);
}
