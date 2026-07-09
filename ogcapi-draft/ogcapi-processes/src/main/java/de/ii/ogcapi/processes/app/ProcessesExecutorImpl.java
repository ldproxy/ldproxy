/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.processes.domain.ProcessesExecutor;
import de.ii.xtraplatform.base.domain.LogContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Singleton
@AutoBind
public class ProcessesExecutorImpl implements ProcessesExecutor {

  // ToDo Handle memory leak
  private final Map<String, StatusCode> jobMap = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);

  @Inject
  ProcessesExecutorImpl() {}

  @Override
  public String execute(String processId) {
    return execute(processId, "");
  }

  @Override
  public String execute(String processId, String input) {
    String jobId = LogContext.generateRandomUuid().toString();
    jobMap.put(jobId, StatusCode.ACCEPTED);

    // Simulate process execution
    scheduler.schedule(
        () -> {
          setStatus(jobId, StatusCode.RUNNING);
          scheduler.schedule(
              () ->
                  setStatus(jobId, Math.random() < 0.9 ? StatusCode.SUCCESSFUL : StatusCode.FAILED),
              5,
              TimeUnit.SECONDS);
        },
        5,
        TimeUnit.SECONDS);

    return jobId;
  }

  @Override
  public Optional<StatusCode> status(String jobId) {
    if (jobMap.containsKey(jobId)) {
      return Optional.of(jobMap.get(jobId));
    } else {
      return Optional.empty();
    }
  }

  @Override
  public Optional<String> result(String jobId) {
    if (jobMap.get(jobId) == StatusCode.SUCCESSFUL) {
      return Optional.of("42");
    }
    return Optional.empty();
  }

  private void setStatus(String jobId, StatusCode status) {
    if (jobMap.containsKey(jobId)) {
      if (jobMap.get(jobId) != StatusCode.DISMISSED) {
        jobMap.put(jobId, status);
      }
    }
  }
}
