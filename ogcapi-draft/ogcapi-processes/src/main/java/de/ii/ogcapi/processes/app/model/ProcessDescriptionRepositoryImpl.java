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
import de.ii.ogcapi.processes.domain.model.ProcessDescriptionRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class ProcessDescriptionRepositoryImpl extends AbstractVolatile
    implements ProcessDescriptionRepository, AppLifeCycle {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessDescriptionRepositoryImpl.class);

  private final Values<ProcessData> customProcessDescriptionsStore;
  private final Map<String, Process> processDescriptions;
  private final VolatileRegistry volatileRegistry;

  /** set data directory */
  @Inject
  public ProcessDescriptionRepositoryImpl(
      ValueStore valueStore, VolatileRegistry volatileRegistry) {
    super(volatileRegistry, "app/processes");
    this.customProcessDescriptionsStore = valueStore.forType(ProcessData.class);
    this.processDescriptions = new LinkedHashMap<>();
    this.volatileRegistry = volatileRegistry;
  }

  @Override
  public CompletionStage<Void> onStart(boolean isStartupAsync) {
    LOGGER.debug("PROCESSES: ProcessDescriptionRepositoryImpl Singleton onStart");
    onVolatileStart();
    return volatileRegistry.onAvailable(customProcessDescriptionsStore).thenRun(this::initCache);
  }

  private void initCache() {
    customProcessDescriptionsStore
        .identifiers()
        .forEach(
            identifier -> {
              ProcessData processData = customProcessDescriptionsStore.get(identifier);

              processDescriptions.put(processData.getId(), Process.custom(processData));
            });

    setState(State.AVAILABLE);
    LOGGER.debug("PROCESSES: processDescriptions " + processDescriptions.toString());
  }

  @Override
  public Optional<Process> get(String processId) {
    return Optional.ofNullable(processDescriptions.get(processId));
  }

  @Override
  public Map<String, Process> getAll() {
    return new ImmutableMap.Builder<String, Process>().putAll(processDescriptions).build();
  }
}
