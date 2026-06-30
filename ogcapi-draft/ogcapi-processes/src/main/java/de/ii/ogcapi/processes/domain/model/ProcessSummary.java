/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import de.ii.ogcapi.processes.domain.model.ProcessData.JOB_CONTROL_OPTIONS;
import java.util.List;
import java.util.Optional;

public interface ProcessSummary extends DescriptionType {
  String getId();

  String getVersion();

  Optional<List<JOB_CONTROL_OPTIONS>> getJobControlOptions();
}
