/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app;

import de.ii.ogcapi.processes.domain.ProcessDescription;
import de.ii.ogcapi.processes.domain.ProcessDescriptionData;
import de.ii.ogcapi.processes.domain.ProcessDescriptionData.JOB_CONTROL_OPTIONS;
import java.util.List;

public class ProcessDescriptionImpl implements ProcessDescription {

  ProcessDescriptionData data;

  public ProcessDescriptionImpl(ProcessDescriptionData data) {
    this.data = data;
  }

  @Override
  public String getId() {
    return data.getId();
  }

  @Override
  public String getTitle() {
    return data.getTitle();
  }

  @Override
  public String getDescription() {
    return data.getDescription();
  }

  @Override
  public String getVersion() {
    return data.getVersion();
  }

  @Override
  public List<JOB_CONTROL_OPTIONS> getJobControlOptions() {
    return data.getJobControlOptions();
  }
}
