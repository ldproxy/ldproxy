/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain;

import de.ii.ogcapi.processes.app.ProcessDescriptionImpl;
import de.ii.ogcapi.processes.domain.ProcessDescriptionData.JOB_CONTROL_OPTIONS;
import java.util.List;

public interface ProcessDescription {

  static ProcessDescription custom(ProcessDescriptionData data) {
    return new ProcessDescriptionImpl(data);
  }

  String getId();

  String getTitle();

  String getDescription();

  String getVersion();

  List<JOB_CONTROL_OPTIONS> getJobControlOptions();
}
