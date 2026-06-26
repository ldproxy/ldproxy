/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import de.ii.ogcapi.processes.app.model.ProcessDescriptionImpl;
import de.ii.ogcapi.processes.domain.model.ProcessDescriptionData.JOB_CONTROL_OPTIONS;
import java.util.List;
import java.util.Optional;

public interface ProcessDescription {

  static ProcessDescription custom(ProcessDescriptionData data) {
    return new ProcessDescriptionImpl(data);
  }

  String getId();

  String getVersion();

  Optional<String> getTitle();

  Optional<String> getDescription();

  Optional<List<JOB_CONTROL_OPTIONS>> getJobControlOptions();

  Optional<List<String>> getKeywords();
}
