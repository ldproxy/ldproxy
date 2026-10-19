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
import de.ii.ogcapi.common.domain.async.JobResultsSelector;
import de.ii.ogcapi.common.domain.async.OgcApiJobResults;
import de.ii.ogcapi.common.domain.async.OgcApiJobStatus;
import de.ii.ogcapi.common.domain.async.OgcApiJobStatusInfo;
import de.ii.xtralink.jobs.Identifiers.Status;
import de.ii.xtralink.jobs.Job;
import de.ii.xtraplatform.base.domain.Jackson;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.web.domain.Http;
import de.ii.xtraplatform.web.domain.HttpClient;
import de.ii.xtraplatform.xtralink.domain.Jobs;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import java.io.InputStream;
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

  @Inject
  AsyncJobsImpl(
      Jobs jobs, Jackson jackson, Http http, Lazy<Set<JobResultsSelector>> resultsSelectors) {
    this.jobs = jobs;
    this.mapper = jackson.getDefaultObjectMapper();
    this.httpClient = http.getDefaultClient();
    this.resultsSelectors = resultsSelectors;
  }

  private Optional<Job> getAsyncJob(String jobId) {
    return jobs.get(jobId).filter(j -> JobContextAsync.is(j.context()));
  }

  private Job getAsyncJobCheck(String jobId) {
    Optional<Job> job = getAsyncJob(jobId);

    if (job.isEmpty()) {
      throw new NotFoundException("No job found with job id '" + jobId + "'.");
    }

    if (job.get().status() != Status.SUCCESSFUL) {
      throw new BadRequestException("Status of job '" + jobId + "' is not SUCCESSFUL");
    }

    return job.get();
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
    Job job = getAsyncJobCheck(jobId);

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
    Job job = getAsyncJobCheck(jobId);

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
