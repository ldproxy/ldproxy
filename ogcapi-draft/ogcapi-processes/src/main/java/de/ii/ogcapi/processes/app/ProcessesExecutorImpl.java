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

/** For now this just simulates the execution of processes. */
@Singleton
@AutoBind
public class ProcessesExecutorImpl implements ProcessesExecutor {

  // ToDo Handle memory leak
  private final Map<String, StatusCode> jobsMap = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Object>> resultsMap = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);

  @Inject
  ProcessesExecutorImpl() {}

  @Override
  public Object executeSync(String processId, Map<String, Object> inputs) {
    return answerProcess();
  }

  @Override
  public Map<String, Object> executeSyncN(String processId, Map<String, Object> inputs) {
    return echoProcess(inputs);
  }

  @Override
  public String executeAsync(String processId, Map<String, Object> inputs) {
    String jobId = LogContext.generateRandomUuid().toString();
    jobsMap.put(jobId, StatusCode.ACCEPTED);

    scheduler.schedule(
        () -> {
          setStatus(jobId, StatusCode.RUNNING);
          scheduler.schedule(
              () -> {
                if ("EchoProcess".equals(processId)) {
                  resultsMap.put(jobId, echoProcess(inputs));
                  setStatus(jobId, Math.random() < 0.9 ? StatusCode.SUCCESSFUL : StatusCode.FAILED);
                } else {
                  throw new IllegalStateException(
                      "Process '"
                          + processId
                          + "' does not support Async computing or does not exist.");
                }
              },
              5,
              TimeUnit.SECONDS);
        },
        5,
        TimeUnit.SECONDS);

    return jobId;
  }

  @Override
  public Optional<StatusCode> status(String jobId) {
    if (jobsMap.containsKey(jobId)) {
      return Optional.of(jobsMap.get(jobId));
    } else {
      return Optional.empty();
    }
  }

  @Override
  public Map<String, Object> result(String jobId) {
    if (jobsMap.get(jobId) == StatusCode.SUCCESSFUL) {
      return resultsMap.get(jobId);
    } else throw new IllegalStateException("Job '" + jobId + " ' did not finish.");
  }

  private void setStatus(String jobId, StatusCode status) {
    if (jobsMap.containsKey(jobId)) {
      if (jobsMap.get(jobId) != StatusCode.DISMISSED) {
        jobsMap.put(jobId, status);
      }
    }
  }

  private Map<String, Object> echoProcess(Map<String, Object> inputs) {
    return inputs;
  }

  private int answerProcess() {
    try {
      Thread.sleep(1000);
    } catch (InterruptedException ex) {
      // ignore
    }
    return 42;
  }
}
