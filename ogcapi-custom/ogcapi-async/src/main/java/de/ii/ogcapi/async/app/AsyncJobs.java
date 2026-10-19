/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.async.app;

import de.ii.ogcapi.common.domain.async.OgcApiJobStatus;
import java.util.Map;
import java.util.Optional;

public interface AsyncJobs {

  Optional<OgcApiJobStatus> getStatusInfo(String jobId);

  Map<String, Object> getResults(String jobId);

  Object getResultsSpecific(String jobId, String outputId);

  Object getResultsSpecificN(String jobId, String outputId, int index);

  Optional<OgcApiJobStatus> dismissJob(String jobId);
}
