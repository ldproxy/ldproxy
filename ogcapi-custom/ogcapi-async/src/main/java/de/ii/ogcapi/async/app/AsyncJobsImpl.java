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
    Job job = getAsyncJobCheck(jobId);
    Optional<JobResultFile> resultFile = JobResultFile.from(job.outputs());

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
            MediaType.valueOf(resultFile.get().getResultMediaType()), content));
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
    Status currentStatus = getStatusInfoCheck(jobId).getStatus();

    // Only cancel job if it's accepted or running. Successful, dismissed and failed jobs keep their
    // status. Note: this behavior is intentionally different from Requirement 114!
    if (Status.ACCEPTED.equals(currentStatus) || Status.RUNNING.equals(currentStatus)) {
      jobs.cancel(jobId);
    }

    return getStatusInfo(jobId);
  }

  @Override
  public void onProgress(Job job) {
    Optional<AsyncSubscriber> subscriber =
        JobContextAsync.asyncSubscriberFrom(job.context(), mapper);
    if (subscriber.isEmpty()) {
      return;
    }

    String jobId = job.id();
    Status status = job.status();
    int callbackRetries = subscriber.get().getMaxRetries().orElse(0);
    switch (status) {
      case SUCCESSFUL ->
          subscriber
              .get()
              .getSuccessUri()
              .ifPresent(uri -> postResults(jobId, uri, callbackRetries));
      case FAILED ->
          subscriber
              .get()
              .getFailedUri()
              .ifPresent(uri -> postStatusInfo(jobId, uri, callbackRetries, false));
      case RUNNING ->
          subscriber.get().getInProgressUri().ifPresent(uri -> postStatusInfo(jobId, uri, 0, true));
    }
  }

  private void postResults(String jobId, String uri, int callbackRetries) {
    byte[] payload = new byte[0];

    try {
      Map<String, Object> results = getResults(jobId);
      OgcApiJobResults resultsResponse =
          new ImmutableOgcApiJobResults.Builder().additionalProperties(results).build();
      payload = mapper.writeValueAsBytes(resultsResponse);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }

    postJson(jobId, uri, callbackRetries, false, payload);
  }

  private void postStatusInfo(String jobId, String uri, int callbackRetries, boolean ignoreError) {
    byte[] payload = new byte[0];

    try {
      OgcApiJobStatus statusInfo = getStatusInfoCheck(jobId);
      OgcApiJobStatusInfo statusInfoResponse =
          new ImmutableOgcApiJobStatusInfo.Builder().from(statusInfo).build();
      payload = mapper.writeValueAsBytes(statusInfoResponse);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }

    postJson(jobId, uri, callbackRetries, ignoreError, payload);
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
