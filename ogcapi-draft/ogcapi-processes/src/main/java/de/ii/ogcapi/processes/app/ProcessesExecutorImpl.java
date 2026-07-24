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
import de.ii.ogcapi.processes.domain.model.ExecuteNested;
import de.ii.ogcapi.processes.domain.model.ProcessRepository;
import de.ii.ogcapi.processes.domain.model.ProcessSummary.JobControlOptions;
import de.ii.ogcapi.processes.domain.model.StatusInfo;
import de.ii.ogcapi.processes.domain.model.StatusInfo.StatusCode;
import de.ii.ogcapi.processes.domain.model.ogc.ImmutableOgcResults.Builder;
import de.ii.ogcapi.processes.domain.model.ogc.ImmutableOgcStatusInfo;
import de.ii.ogcapi.processes.domain.model.ogc.ModifiableOgcStatusInfo;
import de.ii.ogcapi.processes.domain.model.ogc.OgcExecute;
import de.ii.ogcapi.processes.domain.model.ogc.OgcResults;
import de.ii.ogcapi.processes.domain.model.ogc.OgcStatusInfo;
import de.ii.ogcapi.processes.domain.model.ogc.OgcSubscriber;
import de.ii.xtraplatform.base.domain.Jackson;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.web.domain.Http;
import de.ii.xtraplatform.web.domain.HttpClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.MediaType;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
  private final Map<String, ModifiableOgcStatusInfo> jobsMap = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Object>> resultsMap = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);
  private final ProcessRepository processRepository;
  private final ObjectMapper mapper;

  // ToDo Move to config
  private final int maxCallbackRetries = 3;
  private final int maxResolveDepth = 10;

  private final HttpClient httpClient;

  private final Map<
          String,
          BiFunction<Map<String, Object>, Optional<Map<String, String>>, Map<String, Object>>>
      processesMap =
          Map.of(
              "AnswerProcess",
              this::answerProcess,
              "EchoProcess",
              this::echoProcess,
              "AdditionProcess",
              this::additionProcess);

  @Inject
  ProcessesExecutorImpl(ProcessRepository processRepository, Http http, Jackson jackson) {
    this.processRepository = processRepository;
    this.httpClient = http.getDefaultClient();
    this.mapper = jackson.getDefaultObjectMapper();
  }

  @Override
  public Map<String, Object> executeSync(String processId, OgcExecute executeRequest) {

    Map<String, Object> resolvedInputs = resolveInputs(executeRequest.getInputs());
    Optional<Map<String, String>> outputsSelection = executeRequest.getOutputs();

    List<JobControlOptions> options = getJobControlOptions(processId);
    if (options.contains(JobControlOptions.ASYNC_EXECUTE)
        && !options.contains(JobControlOptions.SYNC_EXECUTE)) {
      throw new IllegalArgumentException(
          "Process '" + processId + "' only supports async execution.");
    }

    return processesMap.get(processId).apply(resolvedInputs, outputsSelection);
  }

  @Override
  public StatusInfo executeAsync(String processId, OgcExecute executeRequest) {

    Map<String, Object> inputs = executeRequest.getInputs();
    Optional<Map<String, String>> outputsSelection = executeRequest.getOutputs();
    Optional<OgcSubscriber> subscriber = executeRequest.getSubscriber();

    List<JobControlOptions> options = getJobControlOptions(processId);
    if (!options.contains(JobControlOptions.ASYNC_EXECUTE)) {
      throw new IllegalArgumentException(
          "Process '" + processId + "' does not support async execution.");
    }

    String jobId = LogContext.generateRandomUuid().toString();

    ModifiableOgcStatusInfo statusInfo =
        ModifiableOgcStatusInfo.create()
            .setId(jobId)
            .setProcessId(processId)
            .setRequest(executeRequest)
            .setStatus(StatusCode.ACCEPTED)
            .setCreated(Instant.now());

    // Put job in queue
    jobsMap.put(jobId, statusInfo);

    // Start job
    scheduler.schedule(
        () -> {
          statusInfo.setStarted(Instant.now());
          setRunning(jobId, subscriber);
        },
        1,
        TimeUnit.SECONDS);

    // Update job
    scheduler.schedule(
        () -> {
          setProgress(jobId, 60, subscriber);
        },
        5,
        TimeUnit.SECONDS);

    // Finished job
    scheduler.schedule(
        () -> {
          try {
            Map<String, Object> results =
                processesMap.get(processId).apply(resolveInputs(inputs), outputsSelection);
            resultsMap.put(jobId, results);
            statusInfo.setFinished(Instant.now());
            setSuccess(jobId, subscriber);
          } catch (Exception e) {
            // jobsMap.get(jobId).setException...
            setFailed(jobId, subscriber);
          }
        },
        10,
        TimeUnit.SECONDS);

    return statusInfo;
  }

  @Override
  public Optional<StatusInfo> getStatusInfo(String jobId) {
    return Optional.ofNullable(jobsMap.get(jobId));
  }

  @Override
  public Optional<Map<String, Object>> getResults(String jobId) {
    if ((jobsMap.get(jobId).getStatus() != StatusCode.SUCCESSFUL) || !jobsMap.containsKey(jobId)) {
      return Optional.empty();
    }

    return Optional.of(resultsMap.get(jobId));
  }

  @Override
  public List<String> getJobs() {
    return jobsMap.keySet().stream().toList();
  }

  @Override
  public Optional<StatusInfo> dismissJob(String jobId) {
    if (!jobsMap.containsKey(jobId)) {
      return Optional.empty();
    }

    ModifiableOgcStatusInfo statusInfo = jobsMap.get(jobId);

    // Set to dismiss if job is currently accepted or running
    StatusCode currentStatus = statusInfo.getStatus();
    if (StatusCode.ACCEPTED.equals(currentStatus) || StatusCode.RUNNING.equals(currentStatus)) {
      statusInfo.setStatus(StatusCode.DISMISSED);
    }

    return Optional.of(statusInfo);
  }

  /***
   * Helper functions
   ***/

  /**
   * @param inputs The inputs Map
   * @return A new inputs Map in which each nested process is replaced by the return value of its
   *     execution
   */
  private Map<String, Object> resolveInputs(Map<String, Object> inputs) {
    return resolveInputs(inputs, 0);
  }

  private Map<String, Object> resolveInputs(Map<String, Object> inputs, int depth) {
    if (depth > maxResolveDepth) {
      throw new BadRequestException("Resolve depth limit reached.");
    }
    Map<String, Object> resolvedInput = new LinkedHashMap<>();

    // Simplified assumption: Processes are always at the top level
    for (String key : inputs.keySet()) {
      Object value = inputs.get(key);

      // Value is not a map, put and skip
      if (!(value instanceof Map map)) {
        resolvedInput.put(key, value);
        continue;
      }

      // Simplified assumption: Processes are passed directly with id
      // Map does not contain a process, put and skip
      if (!map.containsKey("process")) {
        resolvedInput.put(key, value);
        continue;
      }

      // Convert the process Map to an ExecuteReduced object
      ExecuteNested nested = mapper.convertValue(map, ExecuteNested.class);

      String nestedProcess = nested.getProcess();
      Object resultFromNested;

      // Get results from process execution
      if (nested.getOutputs().isEmpty() || nested.getOutputs().get().isEmpty()) {
        // If outputSelection is omitted or empty, pick the first result
        resultFromNested =
            processesMap
                .get(nestedProcess)
                .apply(resolveInputs(nested.getInputs(), depth + 1), Optional.empty())
                .values()
                .stream()
                .findFirst()
                .orElseThrow();
      } else {
        // Else pick all outputs in the outputSelection
        resultFromNested =
            processesMap
                .get(nestedProcess)
                .apply(resolveInputs(nested.getInputs(), depth + 1), nested.getOutputs());
      }
      resolvedInput.put(key, resultFromNested);
    }

    return resolvedInput;
  }

  private List<JobControlOptions> getJobControlOptions(String processId) {
    return processRepository.getDirect(processId).getJobControlOptions();
  }

  private ModifiableOgcStatusInfo getStatusInfoDirect(String jobId) {
    if (!jobsMap.containsKey(jobId)) {
      throw new NoSuchElementException("No job found with job id '" + jobId + "'.");
    }

    return jobsMap.get(jobId);
  }

  private Map<String, Object> getResultsDirect(String jobId) {
    if ((jobsMap.get(jobId).getStatus() != StatusCode.SUCCESSFUL) || !jobsMap.containsKey(jobId)) {
      throw new NoSuchElementException("No results found for job '" + jobId + "'.");
    }

    return resultsMap.get(jobId);
  }

  // ToDo Per Recommendation 24 the updated field should be updated whenever there is a status
  // change and not a progress change
  private void setRunning(String jobId, Optional<OgcSubscriber> subscriber) {
    ModifiableOgcStatusInfo statusInfo = getStatusInfoDirect(jobId);

    StatusCode currentStatusCode = statusInfo.getStatus();

    if (StatusCode.ACCEPTED.equals(currentStatusCode)) {
      statusInfo.setStatus(StatusCode.RUNNING);
      setProgress(jobId, 0, subscriber);
    }
  }

  private void callBackSuccess(String successUri, String jobId) {

    OgcResults results = new Builder().additionalProperties(getResultsDirect(jobId)).build();

    byte[] respond;
    try {
      respond = mapper.writeValueAsBytes(results);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }

    int currentRetries = 0;
    do {
      try {
        httpClient.postAsInputStream(
            successUri,
            respond,
            MediaType.APPLICATION_JSON_TYPE,
            Map.of("Accept", MediaType.APPLICATION_JSON));
        break;

      } catch (Exception e) {
        if (currentRetries < maxCallbackRetries) {
          int delay = 100 * (currentRetries + 1);
          if (LOGGER.isWarnEnabled()) {
            LOGGER.warn(
                "Failed send success callback for job '{}', retrying in {}ms", jobId, delay);
          }
          try {
            Thread.sleep(delay);
          } catch (InterruptedException ex) {
            // ignore
          }
        } else {
          LogContext.error(
              LOGGER,
              e,
              "Giving up writing sending success callback for job '{}' after {} retries",
              jobId,
              currentRetries + 1);
          LOGGER.error(
              "Failed sending the success callback for {}: {}",
              jobId,
              new String(respond, StandardCharsets.UTF_8));
        }
      }
      currentRetries++;
    } while (currentRetries <= maxCallbackRetries);
  }

  private void setSuccess(String jobId, Optional<OgcSubscriber> subscriber) {
    ModifiableOgcStatusInfo statusInfo = getStatusInfoDirect(jobId);

    StatusCode currentStatusCode = statusInfo.getStatus();

    if (StatusCode.ACCEPTED.equals(currentStatusCode)
        || StatusCode.RUNNING.equals(currentStatusCode)) {
      statusInfo.setStatus(StatusCode.SUCCESSFUL);
      setProgress(jobId, 100, subscriber);
      subscriber.flatMap(OgcSubscriber::successUri).ifPresent(uri -> callBackSuccess(uri, jobId));
    }
  }

  private void callBackStatusInfo(String type, String uri, String jobId) {

    byte[] respond;
    try {
      ModifiableOgcStatusInfo statusInfo = getStatusInfoDirect(jobId);
      OgcStatusInfo ogcStatusInfoResponse =
          new ImmutableOgcStatusInfo.Builder().from(statusInfo).build();
      respond = mapper.writeValueAsBytes(ogcStatusInfoResponse);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }

    int currentRetries = 0;
    do {
      try {
        httpClient.postAsInputStream(
            uri,
            respond,
            MediaType.APPLICATION_JSON_TYPE,
            Map.of("Accept", MediaType.APPLICATION_JSON));
        break;
      } catch (Exception e) {
        if (currentRetries < maxCallbackRetries) {
          int delay = 100 * (currentRetries + 1);
          if (LOGGER.isWarnEnabled()) {
            LOGGER.warn(
                "Failed send {} callback for job '{}', retrying in {}ms", type, jobId, delay);
          }
          try {
            Thread.sleep(delay);
          } catch (InterruptedException ex) {
            // ignore
          }
        } else {
          LogContext.error(
              LOGGER,
              e,
              "Giving up writing sending {} callback for job '{}' after {} retries",
              type,
              jobId,
              currentRetries + 1);
          LOGGER.error(
              "Failed sending the {} callback for {}: {}",
              type,
              jobId,
              new String(respond, StandardCharsets.UTF_8));
        }
      }
      currentRetries++;
    } while (currentRetries <= maxCallbackRetries);
  }

  private void setFailed(String jobId, Optional<OgcSubscriber> subscriber) {
    ModifiableOgcStatusInfo statusInfo = getStatusInfoDirect(jobId);

    StatusCode currentStatusCode = statusInfo.getStatus();
    if (StatusCode.DISMISSED.equals(currentStatusCode)) {
      return;
    }

    statusInfo.setStatus(StatusCode.FAILED);
    subscriber
        .flatMap(OgcSubscriber::failedUri)
        .ifPresent(uri -> callBackStatusInfo("failed", uri, jobId));
  }

  private void setProgress(String jobId, int progress, Optional<OgcSubscriber> subscriber) {
    ModifiableOgcStatusInfo statusInfo = getStatusInfoDirect(jobId);

    StatusCode currentStatusCode = statusInfo.getStatus();
    if (StatusCode.DISMISSED.equals(currentStatusCode)) {
      return;
    }

    if (progress < 0 || progress > 100) {
      return;
    }

    statusInfo.setProgress(progress);
    statusInfo.setUpdated(Instant.now());
    subscriber
        .flatMap(OgcSubscriber::inProgressUri)
        .ifPresent(uri -> callBackStatusInfo("progress", uri, jobId));
  }

  /***
   * Functions for faking the job queue
   * ToDo Remove after integrating the job queue
   ***/

  private Map<String, Object> echoProcess(
      Map<String, Object> inputs, Optional<Map<String, String>> outputsSelection) {
    return inputs;
  }

  private Map<String, Object> answerProcess(
      Map<String, Object> inputs, Optional<Map<String, String>> outputsSelection) {

    if (outputsSelection.isEmpty() || outputsSelection.get().containsKey("answer")) {
      return Map.of("answer", 42);
    }
    return Map.of();
  }

  private Map<String, Object> additionProcess(
      Map<String, Object> inputs, Optional<Map<String, String>> outputsSelection) {
    if (!inputs.containsKey("firstAddend") || !inputs.containsKey("secondAddend")) {
      throw new RuntimeException("Wrong inputs");
    }

    int firstAddend = (Integer) inputs.get("firstAddend");
    int secondAddend = (Integer) inputs.get("secondAddend");

    if (outputsSelection.isEmpty() || outputsSelection.get().containsKey("sum")) {
      return Map.of("sum", firstAddend + secondAddend);
    }

    return Map.of();
  }
}
