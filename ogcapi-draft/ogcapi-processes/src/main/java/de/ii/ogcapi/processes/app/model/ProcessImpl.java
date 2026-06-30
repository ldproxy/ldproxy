/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app.model;

import de.ii.ogcapi.processes.domain.model.Process;
import de.ii.ogcapi.processes.domain.model.ProcessData;
import java.util.List;
import java.util.Optional;

public class ProcessImpl implements Process {

  ProcessData data;

  public ProcessImpl(ProcessData data) {
    this.data = data;
  }

  @Override
  public String getId() {
    return data.getId();
  }

  @Override
  public String getVersion() {
    return data.getVersion();
  }

  @Override
  public Optional<List<JOB_CONTROL_OPTIONS>> getJobControlOptions() {
    return data.getJobControlOptions();
  }

  @Override
  public Optional<String> getTitle() {
    return data.getTitle();
  }

  @Override
  public Optional<String> getDescription() {
    return data.getDescription();
  }

  @Override
  public Optional<List<String>> getKeywords() {
    return data.getKeywords();
  }
}
