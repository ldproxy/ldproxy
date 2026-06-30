/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app.model;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.processes.domain.model.Process;
import de.ii.ogcapi.processes.domain.model.ProcessData;
import de.ii.ogcapi.processes.domain.model.ProcessRepository;
import de.ii.xtraplatform.base.domain.AppLifeCycle;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatile;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.values.domain.ValueStore;
import de.ii.xtraplatform.values.domain.Values;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

@Singleton
@AutoBind
public class ProcessRepositoryImpl extends AbstractVolatile
    implements ProcessRepository, AppLifeCycle {

  private final Values<ProcessData> customProcessStore;
  private final Map<String, Process> processMap;
  private final VolatileRegistry volatileRegistry;

  /** set data directory */
  @Inject
  public ProcessRepositoryImpl(ValueStore valueStore, VolatileRegistry volatileRegistry) {
    super(volatileRegistry, "app/processes");
    this.customProcessStore = valueStore.forType(ProcessData.class);
    this.processMap = new LinkedHashMap<>();
    this.volatileRegistry = volatileRegistry;
  }

  @Override
  public CompletionStage<Void> onStart(boolean isStartupAsync) {
    onVolatileStart();
    return volatileRegistry.onAvailable(customProcessStore).thenRun(this::initCache);
  }

  private void initCache() {
    customProcessStore
        .identifiers()
        .forEach(
            identifier -> {
              ProcessData processData = customProcessStore.get(identifier);

              processMap.put(processData.getId(), Process.custom(processData));
            });

    setState(State.AVAILABLE);
  }

  @Override
  public Optional<Process> get(String processId) {
    return Optional.ofNullable(processMap.get(processId));
  }

  @Override
  public Map<String, Process> getAll() {
    return new ImmutableMap.Builder<String, Process>().putAll(processMap).build();
  }
}
