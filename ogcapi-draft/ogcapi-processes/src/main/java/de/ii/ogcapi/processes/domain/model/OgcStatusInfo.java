/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import de.ii.xtraplatform.jobs.domain.JobV2;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * See the following link for its OpenAPI 3.0 schema
 * https://raw.githubusercontent.com/opengeospatial/ogcapi-processes/master/openapi/schemas/processes-core/statusInfo.yaml
 */
public interface OgcStatusInfo {

  enum Api {
    OGC_API_PROCESSES,
    OPENEO,
    OGC_API_FEATURES,
    OGC_API_COVERAGES,
    OGC_API_EDR,
    OGC_API_TILES,
    OGC_API_MOVING_FEATURES,
    OGC_API_SENSOR_THINGS,
    OGC_API_RECORDS,
    OGC_API_DGGS,
    STAC_API
  }

  String getId();

  String getProcessId();

  // The type of entity that created the job and is doing the processing.
  @Value.Default
  default Api getProcessingEntityType() {
    return Api.OGC_API_PROCESSES;
  }

  // The type of entity requesting this status information.  This may be different than the
  // processing entity.  For example, the processing entity may be OGC API Processes but the status
  // information is requested via the OpenEO API.
  @Value.Default
  default Api getProfileEntityType() {
    return Api.OGC_API_PROCESSES;
  }

  Optional<OgcExecute> getRequest();

  JobV2.Status getStatus();

  Optional<String> getMessage();

  Optional<OgcException> getException();

  Optional<Instant> getCreated();

  Optional<Instant> getStarted();

  Optional<Instant> getFinished();

  Optional<Instant> getUpdated();

  @Min(0)
  @Max(100)
  Optional<Integer> getProgress();
}
