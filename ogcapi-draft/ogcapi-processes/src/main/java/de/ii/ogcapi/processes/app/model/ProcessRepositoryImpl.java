/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app.model;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.processes.domain.model.OgcProcess;
import de.ii.ogcapi.processes.domain.model.ProcessData;
import de.ii.ogcapi.processes.domain.model.ProcessRepository;
import de.ii.xtraplatform.base.domain.AppLifeCycle;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatile;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.values.domain.Identifier;
import de.ii.xtraplatform.values.domain.ValueStore;
import de.ii.xtraplatform.values.domain.Values;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class ProcessRepositoryImpl extends AbstractVolatile
    implements ProcessRepository, AppLifeCycle {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessRepositoryImpl.class);
  private final Values<ProcessData> customProcessStore;
  private final Map<String, Map<String, ProcessData>> apiProcessesCache;
  private final Map<String, ProcessData> globalProcessesCache;
  private final VolatileRegistry volatileRegistry;

  @Inject
  public ProcessRepositoryImpl(ValueStore valueStore, VolatileRegistry volatileRegistry) {
    super(volatileRegistry, "app/processes");
    this.customProcessStore = valueStore.forType(ProcessData.class);
    this.apiProcessesCache = new ConcurrentHashMap<>();
    this.globalProcessesCache = new ConcurrentHashMap<>();
    this.volatileRegistry = volatileRegistry;
  }

  @Override
  public CompletionStage<Void> onStart(boolean isStartupAsync) {
    onVolatileStart();
    return volatileRegistry.onAvailable(customProcessStore).thenRun(this::initCache);
  }

  private void initCache() {
    List<Identifier> processes = customProcessStore.identifiers();

    // Generate the global cache
    processes.stream()
        .filter(identifier -> identifier.path().isEmpty())
        .forEach(
            identifier -> {
              globalProcessesCache.put(identifier.id(), customProcessStore.get(identifier));
            });

    // Generate the API-cache
    processes.stream()
        .filter(identifier -> !identifier.path().isEmpty())
        .forEach(
            identifier -> {
              String apiId = identifier.path().get(0);
              String processId = identifier.id();
              if (globalProcessesCache.containsKey(processId)) {
                if (LOGGER.isWarnEnabled()) {
                  LOGGER.warn(
                      "Process '{}' of API '{}' is already defined globally and will therefore be ignored!",
                      processId,
                      apiId);
                }
                return;
              }
              apiProcessesCache
                  .computeIfAbsent(apiId, k -> new ConcurrentHashMap<>())
                  .put(processId, customProcessStore.get(identifier));
            });

    setState(State.AVAILABLE);
  }

  @Override
  public OgcProcess getDirect(OgcApiDataV2 apiData, String processId) {
    return get(apiData, processId)
        .orElseThrow(
            () -> new NotFoundException("No process found with process id '" + processId + "'."));
  }

  @Override
  public Optional<OgcProcess> get(OgcApiDataV2 apiData, String processId) {
    if (globalProcessesCache.containsKey(processId)) {
      return Optional.of(globalProcessesCache.get(processId));
    }

    Map<String, ProcessData> apiProcesses = apiProcessesCache.get(apiData.getId());
    if (apiProcesses != null && apiProcesses.containsKey(processId)) {
      return Optional.of(apiProcesses.get(processId));
    }

    return Optional.empty();
  }

  @Override
  public Map<String, OgcProcess> getAll(OgcApiDataV2 apiData) {
    Map<String, OgcProcess> result = new LinkedHashMap<>(globalProcessesCache);
    Map<String, ProcessData> apiMap = apiProcessesCache.get(apiData.getId());
    if (apiMap != null) {
      result.putAll(apiMap);
    }
    return result;
  }
}
