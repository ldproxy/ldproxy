/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.processes.domain.ProcessesExecutor;
import de.ii.ogcapi.processes.domain.model.ProcessRepository;
import de.ii.ogcapi.processes.domain.model.ProcessSummary.JobControlOptions;
import de.ii.ogcapi.processes.domain.model.Subscriber;
import de.ii.ogcapi.processes.domain.model.ogc.ImmutableOgcResults;
import de.ii.ogcapi.processes.domain.model.ogc.OgcExecute;
import de.ii.ogcapi.processes.domain.model.ogc.OgcResults;
import de.ii.xtraplatform.base.domain.Jackson;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.web.domain.Http;
import de.ii.xtraplatform.web.domain.HttpClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** For now this just simulates the execution of processes. It has many flaws. */
@Singleton
@AutoBind
public class ProcessesExecutorImpl implements ProcessesExecutor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessesExecutorImpl.class);

  // ToDo Handle memory leak
  private final Map<String, StatusCode> jobsMap = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Object>> resultsMap = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);
  private final ProcessRepository processRepository;
  private final ObjectMapper mapper;

  private final HttpClient httpClient;

  private final Map<
          String,
          BiFunction<Map<String, Object>, Optional<Map<String, String>>, Map<String, Object>>>
      processesMap = Map.of("AnswerProcess", this::answerProcess, "EchoProcess", this::echoProcess);

  @Inject
  ProcessesExecutorImpl(ProcessRepository processRepository, Http http, Jackson jackson) {
    this.processRepository = processRepository;
    this.httpClient = http.getDefaultClient();
    this.mapper = jackson.getDefaultObjectMapper();
  }

  @Override
  public Map<String, Object> executeSync(String processId, OgcExecute executeRequest) {

    Map<String, Object> inputs = executeRequest.getInputs();
    Optional<Map<String, String>> outputs = executeRequest.getOutputs();

    List<JobControlOptions> options = getJobControlOptions(processId);
    if (options.contains(JobControlOptions.ASYNC_EXECUTE)
        && !options.contains(JobControlOptions.SYNC_EXECUTE)) {
      throw new IllegalArgumentException(
          "Process '" + processId + "' only supports async execution.");
    }

    return processesMap.get(processId).apply(inputs, outputs);
  }

  @Override
  public String executeAsync(String processId, OgcExecute executeRequest) {

    Map<String, Object> inputs = executeRequest.getInputs();
    Optional<Map<String, String>> outputs = executeRequest.getOutputs();
    Optional<Subscriber> subscriber = executeRequest.getSubscriber();

    List<JobControlOptions> options = getJobControlOptions(processId);
    if (!options.contains(JobControlOptions.ASYNC_EXECUTE)) {
      throw new IllegalArgumentException(
          "Process '" + processId + "' does not support async execution.");
    }

    String jobId = LogContext.generateRandomUuid().toString();
    jobsMap.put(jobId, StatusCode.ACCEPTED);

    scheduler.schedule(
        () -> {
          resultsMap.put(jobId, processesMap.get(processId).apply(inputs, outputs));
          setStatus(jobId, StatusCode.SUCCESSFUL, subscriber);
        },
        2,
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

  /** Helper functions */
  private List<JobControlOptions> getJobControlOptions(String processId) {
    return processRepository.getDirect(processId).getJobControlOptions();
  }

  private void setStatus(String jobId, StatusCode status) {
    setStatus(jobId, status, Optional.empty());
  }

  private void setStatus(String jobId, StatusCode status, Optional<Subscriber> subscriber) {
    if (!jobsMap.containsKey(jobId)) {
      throw new NoSuchElementException("No job found with job id '" + jobId + "'.");
    }

    StatusCode currentStatus = jobsMap.get(jobId);
    if (currentStatus == StatusCode.DISMISSED) {
      return;
    }

    // Simple unfinished logic
    jobsMap.put(jobId, status);
    if (subscriber.isPresent()) {
      switch (status) {
        case ACCEPTED -> subscriber.get().inProgressUri().ifPresent(this::callBackInProgress);
        case SUCCESSFUL ->
            subscriber.get().successUri().ifPresent(uri -> callBackSuccess(uri, jobId));
        case FAILED -> subscriber.get().failedUri().ifPresent(this::callBackFailed);
      }
    }
  }

  private void callBackInProgress(String inProgressUri) {}

  private void callBackSuccess(String successUri, String jobId) {

    OgcResults results =
        new ImmutableOgcResults.Builder().additionalProperties(result(jobId)).build();

    try {
      byte[] respond = mapper.writeValueAsBytes(results);
      httpClient.postAsInputStream(
          successUri,
          respond,
          MediaType.APPLICATION_JSON_TYPE,
          Map.of("Accept", MediaType.APPLICATION_JSON));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private void callBackFailed(String failedUri) {}

  /** Functions for faking the job queue ToDo remove after integrating the job queue */
  private Map<String, Object> echoProcess(
      Map<String, Object> inputs, Optional<Map<String, String>> outputs) {
    return inputs;
  }

  private Map<String, Object> answerProcess(
      Map<String, Object> inputs, Optional<Map<String, String>> outputs) {
    if (outputs.isEmpty()) {
      return Map.of("answer", 42, "answerStr", "42");
    }
    Map<String, Object> results = new LinkedHashMap<>();
    outputs
        .get()
        .keySet()
        .forEach(
            k -> {
              if ("answer".equals(k)) results.put("answer", 42);
              if ("answerStr".equals(k)) results.put("answerStr", "42");
            });
    return results;
  }
}
