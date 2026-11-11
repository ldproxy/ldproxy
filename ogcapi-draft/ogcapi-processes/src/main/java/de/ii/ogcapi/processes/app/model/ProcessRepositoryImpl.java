/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app.model;

import com.github.azahnen.dagger.annotations.AutoBind;
import dagger.Lazy;
import de.ii.ogcapi.processes.domain.model.ProcessContribution;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class ProcessRepositoryImpl extends AbstractVolatile
    implements ProcessRepository, AppLifeCycle {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessRepositoryImpl.class);
  // the processes are read from the store on every call, so that added or changed process
  // descriptions take effect with a reload of the values, without a restart
  private final Values<ProcessData> customProcessStore;
  private final VolatileRegistry volatileRegistry;
  private final Lazy<Set<ProcessContribution>> contributions;
  // the API processes that are ignored because of a global process with the same id, warned once
  private final Set<String> ignoredApiProcesses;

  @Inject
  public ProcessRepositoryImpl(
      ValueStore valueStore,
      VolatileRegistry volatileRegistry,
      Lazy<Set<ProcessContribution>> contributions) {
    super(volatileRegistry, "app/processes");
    this.customProcessStore = valueStore.forType(ProcessData.class);
    this.volatileRegistry = volatileRegistry;
    this.contributions = contributions;
    this.ignoredApiProcesses = ConcurrentHashMap.newKeySet();
  }

  @Override
  public CompletionStage<Void> onStart(boolean isStartupAsync) {
    onVolatileStart();
    return volatileRegistry
        .onAvailable(customProcessStore)
        .thenRun(() -> setState(State.AVAILABLE));
  }

  @Override
  public ProcessData getDirect(String apiId, String processId) {
    return get(apiId, processId)
        .orElseThrow(
            () -> new NotFoundException("No process found with process id '" + processId + "'."));
  }

  @Override
  public Optional<ProcessData> get(String apiId, String processId) {
    return Optional.ofNullable(getStored(apiId).get(processId))
        .or(
            () ->
                contributions.get().stream()
                    .map(contribution -> contribution.getProcesses(apiId).get(processId))
                    .filter(Objects::nonNull)
                    .findFirst());
  }

  @Override
  public Map<String, ProcessData> getAll(String apiId) {
    Map<String, ProcessData> result = getStored(apiId);
    // a process description in the values takes precedence over a contributed process
    contributions
        .get()
        .forEach(contribution -> contribution.getProcesses(apiId).forEach(result::putIfAbsent));
    return result;
  }

  // the global processes first, then those of the API; a global process wins over a process of
  // the API with the same id
  private Map<String, ProcessData> getStored(String apiId) {
    List<Identifier> identifiers = customProcessStore.identifiers();
    Map<String, ProcessData> result = new LinkedHashMap<>();
    identifiers.stream()
        .filter(identifier -> identifier.path().isEmpty())
        .forEach(identifier -> putIfPresent(result, identifier));
    identifiers.stream()
        .filter(
            identifier -> !identifier.path().isEmpty() && identifier.path().get(0).equals(apiId))
        .forEach(
            identifier -> {
              if (result.containsKey(identifier.id())) {
                warnIgnored(apiId, identifier.id());
              } else {
                putIfPresent(result, identifier);
              }
            });
    return result;
  }

  private void putIfPresent(Map<String, ProcessData> processes, Identifier identifier) {
    ProcessData process = customProcessStore.get(identifier);
    if (Objects.nonNull(process)) {
      processes.put(identifier.id(), process);
    }
  }

  private void warnIgnored(String apiId, String processId) {
    if (ignoredApiProcesses.add(apiId + "/" + processId) && LOGGER.isWarnEnabled()) {
      LOGGER.warn(
          "Process '{}' of API '{}' is already defined globally and will therefore be ignored!",
          processId,
          apiId);
    }
  }
}
