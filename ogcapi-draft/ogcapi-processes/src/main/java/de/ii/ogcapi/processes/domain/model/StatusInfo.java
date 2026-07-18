/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import de.ii.ogcapi.processes.domain.model.ogc.OgcExecute;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.Optional;

// ToDo Add missing properties
public interface StatusInfo {

  enum StatusCode {
    ACCEPTED,
    RUNNING,
    SUCCESSFUL,
    FAILED,
    DISMISSED
  }

  String getId();

  String getProcessId();

  // Optional<ProcessingEntityType> getProcessingEntityType;

  // Optional<ProfileEntityType> getProfileEntityType;

  Optional<OgcExecute> getRequest();

  StatusCode getStatus();

  Optional<String> getMessage();

  // Optional<Exception> getException();

  Optional<Instant> getCreated();

  Optional<Instant> getStarted();

  Optional<Instant> getFinished();

  Optional<Instant> getUpdated();

  @Min(0)
  @Max(100)
  Optional<Integer> getProgress();
}
