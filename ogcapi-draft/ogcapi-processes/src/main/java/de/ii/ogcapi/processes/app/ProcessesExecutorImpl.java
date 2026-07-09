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
  public Map<String, Object> executeSync(String processId, Map<String, Object> inputs) {
    if ("AnswerProcess".equals(processId)) {
      return answerProcess();
    } else {
      return echoProcess(inputs);
    }
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
                if ("AnswerProcess".equals(processId)) {
                  resultsMap.put(jobId, answerProcess());
                } else {
                  resultsMap.put(jobId, echoProcess(inputs));
                }
                setStatus(jobId, Math.random() < 0.9 ? StatusCode.SUCCESSFUL : StatusCode.FAILED);
              },
              5,
              TimeUnit.SECONDS);
        },
        5,
        TimeUnit.SECONDS);

    return jobId;
  }

  private Map<String, Object> echoProcess(Map<String, Object> inputs) {
    return inputs;
  }

  private Map<String, Object> answerProcess() {
    // Support for single output return is stil missing
    Map<String, Object> answer = new ConcurrentHashMap<>();
    try {
      Thread.sleep(1000);
    } catch (InterruptedException ex) {
      // ignore
    }
    answer.put("answer", 42);
    return answer;
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
}
