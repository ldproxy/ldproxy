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
import de.ii.ogcapi.processes.domain.model.Process;
import de.ii.ogcapi.processes.domain.model.ProcessSummary.JobControlOptions;
import de.ii.ogcapi.processes.domain.model.StatusInfo;
import de.ii.ogcapi.processes.domain.model.ogc.ImmutableOgcResults;
import de.ii.ogcapi.processes.domain.model.ogc.ImmutableOgcStatusInfo;
import de.ii.ogcapi.processes.domain.model.ogc.OgcExecute;
import de.ii.ogcapi.processes.domain.model.ogc.OgcResults;
import de.ii.ogcapi.processes.domain.model.ogc.OgcStatusInfo;
import de.ii.ogcapi.processes.domain.model.ogc.OgcSubscriber;
import de.ii.xtraplatform.base.domain.Jackson;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.jobs.domain.JobQueueV2;
import de.ii.xtraplatform.jobs.domain.JobV2;
import de.ii.xtraplatform.jobs.domain.JobV2.Status;
import de.ii.xtraplatform.web.domain.Http;
import de.ii.xtraplatform.web.domain.HttpClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class ProcessesExecutorImpl implements ProcessesExecutor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessesExecutorImpl.class);

  private final ObjectMapper mapper;
  private final JobQueueV2 jobQueue;

  // ToDo Move to config
  private final int maxCallbackRetries = 3;

  private final HttpClient httpClient;

  @Inject
  ProcessesExecutorImpl(Http http, Jackson jackson, JobQueueV2 jobQueue) {
    this.httpClient = http.getDefaultClient();
    this.mapper = jackson.getDefaultObjectMapper();
    this.jobQueue = jobQueue;
  }

  @Override
  public Map<String, Object> executeSync(Process process, OgcExecute executeRequest) {

    Map<String, Object> inputs = executeRequest.getInputs();
    // ToDo Filter return value using outputsSelection
    Optional<Map<String, String>> outputsSelection = executeRequest.getOutputs();

    List<JobControlOptions> options = process.getJobControlOptions();
    if (options.contains(JobControlOptions.ASYNC_EXECUTE)
        && !options.contains(JobControlOptions.SYNC_EXECUTE)) {
      throw new IllegalArgumentException(
          "Process '" + process.getId() + "' only supports async execution.");
    }

    // Create job
    JobV2 job = jobQueue.createJob(process.getId(), inputs);

    // Push it and wait for its results
    return jobQueue.push(job).join().getOutputs();
  }

  @Override
  public StatusInfo executeAsync(Process process, OgcExecute executeRequest) {

    Map<String, Object> inputs = executeRequest.getInputs();
    // ToDo Filter return value using outputsSelection
    Optional<Map<String, String>> outputsSelection = executeRequest.getOutputs();

    List<JobControlOptions> options = process.getJobControlOptions();
    if (!options.contains(JobControlOptions.ASYNC_EXECUTE)) {
      throw new IllegalArgumentException(
          "Process '" + process.getId() + "' does not support async execution.");
    }

    // Create job
    JobV2 job = jobQueue.createJob(process.getId(), inputs, executeRequest.getSubscriber());

    // Put job with callBack in queue
    jobQueue.push(job, this::callBack);

    return OgcStatusInfo.of(job);
  }

  @Override
  public Optional<StatusInfo> getStatusInfo(String jobId) {
    JobV2 job = jobQueue.get(jobId);
    if (job == null) {
      return Optional.empty();
    }
    return Optional.of(OgcStatusInfo.of(job));
  }

  @Override
  public Optional<Map<String, Object>> getResults(String jobId) {
    JobV2 job = jobQueue.get(jobId);
    if (job == null || (job.getStatus() != JobV2.Status.SUCCESSFUL)) {
      return Optional.empty();
    }

    return Optional.of(job.getOutputs());
  }

  @Override
  public Optional<StatusInfo> dismissJob(String jobId) {
    JobV2.Status currentStatus = getStatusInfoDirect(jobId).getStatus();

    // Only cancel job if it's accepted or running. Successful, dismissed and failed jobs keep their
    // status. Note: this behavior is intentionally different from Requirement 114!
    if (Status.ACCEPTED.equals(currentStatus) || Status.RUNNING.equals(currentStatus)) {
      jobQueue.cancel(jobId);
    }

    return getStatusInfo(jobId);
  }

  /***
   * Helper functions
   ***/

  private void callBack(JobV2 job) {
    Optional<OgcSubscriber> subscriber = (Optional<OgcSubscriber>) job.getDetails();
    if (subscriber.isEmpty()) {
      return;
    }

    String jobId = job.getId();
    JobV2.Status updatedStatus = job.getStatus();
    switch (updatedStatus) {
      case SUCCESSFUL -> callBackOnSuccess(jobId, subscriber.get());
      case FAILED -> callBackOnFailure(jobId, subscriber.get());
      case RUNNING -> callBackOnProgress(jobId, subscriber.get());
    }
  }

  private void callBackOnSuccess(String jobId, OgcSubscriber subscriber) {
    if (subscriber.successUri().isEmpty()) {
      return;
    }

    OgcResults results =
        new ImmutableOgcResults.Builder().additionalProperties(getResultsDirect(jobId)).build();

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
            subscriber.successUri().get(),
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

  private void callBackOnFailure(String jobId, OgcSubscriber subscriber) {
    subscriber.failedUri().ifPresent(uri -> postStatusInfo(jobId, uri, "failed"));
  }

  private void callBackOnProgress(String jobId, OgcSubscriber subscriber) {
    subscriber.inProgressUri().ifPresent(uri -> postStatusInfo(jobId, uri, "inProgress"));
  }

  private void postStatusInfo(String jobId, String uri, String type) {

    byte[] respond;
    try {
      StatusInfo statusInfo = getStatusInfoDirect(jobId);
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

  private StatusInfo getStatusInfoDirect(String jobId) {
    JobV2 job = jobQueue.get(jobId);
    if (job == null) {
      throw new NotFoundException("No job found with job id '" + jobId + "'.");
    }

    return OgcStatusInfo.of(job);
  }

  private Map<String, Object> getResultsDirect(String jobId) {
    JobV2 job = jobQueue.get(jobId);
    if (job == null || (job.getStatus() != JobV2.Status.SUCCESSFUL)) {
      throw new NotFoundException("No results found for job '" + jobId + "'.");
    }

    return job.getOutputs();
  }
}
