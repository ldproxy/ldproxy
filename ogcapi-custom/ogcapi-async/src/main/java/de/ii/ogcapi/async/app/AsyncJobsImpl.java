/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.async.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.azahnen.dagger.annotations.AutoBind;
import dagger.Lazy;
import de.ii.ogcapi.common.domain.async.AsyncCallbacks;
import de.ii.ogcapi.common.domain.async.AsyncSubscriber;
import de.ii.ogcapi.common.domain.async.ImmutableOgcApiJobResults;
import de.ii.ogcapi.common.domain.async.ImmutableOgcApiJobStatusInfo;
import de.ii.ogcapi.common.domain.async.JobContextAsync;
import de.ii.ogcapi.common.domain.async.JobResultFile;
import de.ii.ogcapi.common.domain.async.JobResultsSelector;
import de.ii.ogcapi.common.domain.async.OgcApiJobResults;
import de.ii.ogcapi.common.domain.async.OgcApiJobStatus;
import de.ii.ogcapi.common.domain.async.OgcApiJobStatus.EntityType;
import de.ii.ogcapi.common.domain.async.OgcApiJobStatusInfo;
import de.ii.ogcapi.foundation.domain.TypedClientErrorException;
import de.ii.xtralink.jobs.Identifiers.Status;
import de.ii.xtralink.jobs.Job;
import de.ii.xtraplatform.base.domain.Jackson;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import de.ii.xtraplatform.web.domain.Http;
import de.ii.xtraplatform.web.domain.HttpClient;
import de.ii.xtraplatform.xtralink.domain.Jobs;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class AsyncJobsImpl implements AsyncJobs, AsyncCallbacks {

  private static final Logger LOGGER = LoggerFactory.getLogger(AsyncJobsImpl.class);

  private final Jobs jobs;
  private final ObjectMapper mapper;
  private final HttpClient httpClient;
  private final Lazy<Set<JobResultsSelector>> resultsSelectors;
  private final ResourceStore resourceStore;

  @Inject
  AsyncJobsImpl(
      Jobs jobs,
      Jackson jackson,
      Http http,
      Lazy<Set<JobResultsSelector>> resultsSelectors,
      ResourceStore resourceStore) {
    this.jobs = jobs;
    this.mapper = jackson.getDefaultObjectMapper();
    this.httpClient = http.getDefaultClient();
    this.resultsSelectors = resultsSelectors;
    this.resourceStore = resourceStore;
  }

  private Optional<Job> getAsyncJob(String jobId) {
    return jobs.get(jobId).filter(j -> JobContextAsync.is(j.context()));
  }

  private Job getAsyncJobCheck(String jobId) {
    return getAsyncJobCheck(jobId, false);
  }

  /**
   * @param failedWithResult whether a failed job passes, its result is the reason for the failure
   */
  private Job getAsyncJobCheck(String jobId, boolean failedWithResult) {
    Optional<Job> job = getAsyncJob(jobId);

    if (job.isEmpty()) {
      throw new NotFoundException("No job found with job id '" + jobId + "'.");
    }

    Status status = job.get().status();
    if (status == Status.ACCEPTED || status == Status.RUNNING) {
      throw new TypedClientErrorException(
          "The results of job '" + jobId + "' are not ready yet.",
          Response.Status.NOT_FOUND,
          JobContextAsync.entityTypeFrom(job.get().context())
              .exceptionType("result-not-ready")
              .orElse(null));
    }

    if (status == Status.FAILED && failedWithResult) {
      return job.get();
    }

    if (status != Status.SUCCESSFUL) {
      throw new BadRequestException("Status of job '" + jobId + "' is not SUCCESSFUL");
    }

    return job.get();
  }

  private Job getNamedOutputsJobCheck(String jobId) {
    boolean hasNamedOutputs =
        getAsyncJob(jobId)
            .map(job -> JobContextAsync.entityTypeFrom(job.context()))
            .filter(entityType -> entityType == EntityType.OGC_API_PROCESSES)
            .isPresent();

    if (!hasNamedOutputs) {
      throw new NotFoundException("No job with named outputs found with job id '" + jobId + "'.");
    }

    return getAsyncJobCheck(jobId);
  }

  @Override
  public Optional<OgcApiJobStatus> getStatusInfo(String jobId) {
    Optional<Job> job = getAsyncJob(jobId);

    return job.map(OgcApiJobStatusInfo::of);
  }

  private OgcApiJobStatus getStatusInfoCheck(String jobId) {
    return getStatusInfo(jobId)
        .orElseThrow(() -> new NotFoundException("No job found with job id '" + jobId + "'."));
  }

  @Override
  public Optional<ResultDocument> getResultDocument(String jobId) {
    Job job = getAsyncJobCheck(jobId, true);
    Optional<JobResultFile> resultFile = JobResultFile.from(job.outputs());

    // Requirement job-results-failed: an error status that corresponds to the reason, e.g. the
    // response document of a rejected transaction; otherwise the errors of the job
    if (job.status() == Status.FAILED
        && resultFile.filter(file -> file.getResultStatus() != null).isEmpty()) {
      return Optional.of(failure(job));
    }

    if (resultFile.isEmpty()) {
      return Optional.empty();
    }

    ResourceStore jobStore = resourceStore.with(Jobs.RESOURCE_TYPE, job.kind());
    Path path = resultFile.get().path();
    if (!hasResultFile(jobStore, path)) {
      throw new NotFoundException("The result of job '" + jobId + "' is no longer available.");
    }

    StreamingOutput content =
        output -> {
          try (InputStream in = jobStore.content(path).orElseThrow()) {
            in.transferTo(output);
          }
        };

    return Optional.of(
        ImmutableResultDocument.of(
                MediaType.valueOf(resultFile.get().getResultMediaType()), content)
            .withStatus(resultFile.get().status()));
  }

  private ResultDocument failure(Job job) {
    Map<String, Object> problem = new LinkedHashMap<>();
    problem.put("status", 500);
    problem.put("title", "Internal Server Error");
    problem.put(
        "detail",
        job.errors().isEmpty()
            ? "Job '%s' failed.".formatted(job.id())
            : "Job '%s' failed: %s".formatted(job.id(), String.join(" ", job.errors())));
    StreamingOutput content = output -> mapper.writeValue(output, problem);

    return ImmutableResultDocument.of(MediaType.valueOf("application/problem+json"), content)
        .withStatus(500);
  }

  private static boolean hasResultFile(ResourceStore jobStore, Path path) {
    try {
      return jobStore.has(path);
    } catch (IOException e) {
      throw new IllegalStateException("Could not access the job result " + path, e);
    }
  }

  @Override
  public Map<String, Object> getResults(String jobId) {
    Job job = getAsyncJobCheck(jobId);

    Map<String, Object> results = selectResults(job);

    // Requirement 59 of OGC API Processes, no outputs were requested
    if (results.isEmpty()
        && JobContextAsync.entityTypeFrom(job.context()) == EntityType.OGC_API_PROCESSES) {
      throw new TypedClientErrorException(
          "No results are available for job '%s'.".formatted(jobId),
          Response.Status.NOT_FOUND,
          EntityType.OGC_API_PROCESSES.exceptionType("result-not-available").orElseThrow());
    }

    return results;
  }

  private Map<String, Object> selectResults(Job job) {
    Map<String, Object> outputs = job.outputs();

    for (JobResultsSelector selector : resultsSelectors.get()) {
      if (selector.canHandle(job.context())) {
        return selector.selectResults(outputs, job.context());
      }
    }

    return outputs;
  }

  @Override
  public Object getResultsSpecific(String jobId, String outputId) {
    Job job = getNamedOutputsJobCheck(jobId);

    Map<String, Object> outputs = job.outputs();

    for (JobResultsSelector selector : resultsSelectors.get()) {
      if (selector.canHandle(job.context())) {
        return selector.selectSpecificResult(outputs, job.context(), job.kind(), outputId);
      }
    }

    return outputs.get(outputId);
  }

  @Override
  public Object getResultsSpecificN(String jobId, String resultKey, int resultIndex) {
    Job job = getNamedOutputsJobCheck(jobId);

    Map<String, Object> outputs = job.outputs();

    for (JobResultsSelector selector : resultsSelectors.get()) {
      if (selector.canHandle(job.context())) {
        return selector.selectSpecificResultN(
            outputs, job.context(), job.kind(), resultKey, resultIndex);
      }
    }

    Object value = outputs.get(resultKey);

    if (!(value instanceof List list)) {
      throw new BadRequestException("The output '%s' is not an Array".formatted(resultKey));
    }

    int size = list.size();
    if (size <= resultIndex) {
      throw new IllegalArgumentException(
          "Out of bound for '%d' (list size: %d)".formatted(resultIndex, size));
    }

    return list.get(resultIndex);
  }

  @Override
  public Optional<OgcApiJobStatus> dismissJob(String jobId) {
    OgcApiJobStatus statusInfo = getStatusInfoCheck(jobId);
    Status currentStatus = statusInfo.getStatus();

    // Requirement 115: an accepted or running job is cancelled, then any job is removed
    if (Status.ACCEPTED.equals(currentStatus) || Status.RUNNING.equals(currentStatus)) {
      cancelQuietly(jobId);
    }
    jobs.delete(jobId);

    return Optional.of(
        new ImmutableOgcApiJobStatusInfo.Builder()
            .from(statusInfo)
            .status(Status.DISMISSED)
            .build());
  }

  // the job is removed anyway, so a failed cancellation does not fail the dismissal
  private void cancelQuietly(String jobId) {
    try {
      jobs.cancel(jobId);
    } catch (RuntimeException e) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Could not cancel job '{}' before removing it", jobId, e);
      }
    }
  }

  @Override
  public void onProgress(Job job) {
    Optional<AsyncSubscriber> subscriber =
        JobContextAsync.asyncSubscriberFrom(job.context(), mapper);
    if (subscriber.isEmpty()) {
      return;
    }

    Status status = job.status();
    int callbackRetries = subscriber.get().getMaxRetries().orElse(0);
    switch (status) {
      case SUCCESSFUL ->
          subscriber.get().getSuccessUri().ifPresent(uri -> postResults(job, uri, callbackRetries));
      case FAILED ->
          subscriber
              .get()
              .getFailedUri()
              .ifPresent(uri -> postStatusInfo(job, uri, callbackRetries, false));
      case RUNNING ->
          subscriber.get().getInProgressUri().ifPresent(uri -> postStatusInfo(job, uri, 0, true));
    }
  }

  // the payloads are derived from the job of the event, a job fetched later may have moved on
  private void postResults(Job job, String uri, int callbackRetries) {
    byte[] payload = new byte[0];

    Map<String, Object> results = selectResults(job);
    if (!results.isEmpty()) {
      OgcApiJobResults resultsResponse =
          new ImmutableOgcApiJobResults.Builder().additionalProperties(results).build();
      payload = toJson(resultsResponse);
    }

    postJson(job.id(), uri, callbackRetries, false, payload);
  }

  private void postStatusInfo(Job job, String uri, int callbackRetries, boolean ignoreError) {
    OgcApiJobStatusInfo statusInfoResponse =
        new ImmutableOgcApiJobStatusInfo.Builder().from(OgcApiJobStatusInfo.of(job)).build();

    postJson(job.id(), uri, callbackRetries, ignoreError, toJson(statusInfoResponse));
  }

  // timestamps are encoded as in the responses of the API
  private byte[] toJson(Object payload) {
    try {
      return mapper
          .writer()
          .without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .writeValueAsBytes(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private void postJson(
      String jobId, String uri, int retries, boolean ignoreError, byte[] payload) {
    int currentRetries = 0;
    do {
      try (InputStream is =
          httpClient.postAsInputStream(
              uri,
              payload,
              MediaType.APPLICATION_JSON_TYPE,
              Map.of("Accept", MediaType.APPLICATION_JSON))) {
        break;
      } catch (Throwable e) {
        if (currentRetries < retries) {
          int delay = 100 * (currentRetries + 1);
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Failed sending callback for job '{}', retrying in {}ms", jobId, delay);
          }
          try {
            Thread.sleep(delay);
          } catch (InterruptedException ex) {
            // ignore
          }
        } else {
          if (!ignoreError) {
            LogContext.errorAsWarn(
                LOGGER,
                e,
                "Giving up sending callback for job '{}' after {} retries",
                jobId,
                currentRetries);
          }
        }
      }
      currentRetries++;
    } while (currentRetries <= retries);
  }
}
