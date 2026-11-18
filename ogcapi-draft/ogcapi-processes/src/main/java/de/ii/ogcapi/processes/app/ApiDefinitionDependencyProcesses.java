/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiDefinitionDependency;
import de.ii.ogcapi.processes.domain.model.ProcessData;
import de.ii.xtraplatform.values.domain.ValueStore;
import de.ii.xtraplatform.values.domain.Values;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * The processes defined as values: the ids of the processes are part of the API definition, for
 * example as values of the path parameter `processId`, which is therefore derived again when the
 * processes change.
 */
@Singleton
@AutoBind
public class ApiDefinitionDependencyProcesses implements ApiDefinitionDependency {

  private final Values<ProcessData> processes;

  @Inject
  public ApiDefinitionDependencyProcesses(ValueStore valueStore) {
    this.processes = valueStore.forType(ProcessData.class);
  }

  @Override
  public long getRevision() {
    return processes.getRevision();
  }
}
