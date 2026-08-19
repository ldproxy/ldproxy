/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.ii.ogcapi.features.search.domain.QueryExpression;
import de.ii.ogcapi.features.search.domain.SearchConfiguration;
import de.ii.ogcapi.features.search.domain.SearchJob;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.HeaderPrefer;
import de.ii.xtralink.jobs.Identifiers;
import de.ii.xtralink.jobs.Job;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import de.ii.xtraplatform.xtralink.domain.Jobs;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Submits an asynchronous search job: the resolved query expression is stored directly on the job
 * and the response is 202 with the job id. A {@code wait} preference defers the response for up to
 * {@code min(wait, maxWait)} seconds — if the job finishes in time, the result document is returned
 * synchronously and its stored copy is deleted.
 */
final class SearchAsync {

  private static final Logger LOGGER = LoggerFactory.getLogger(SearchAsync.class);

  private SearchAsync() {}

  static Response submit(
      Jobs jobs,
      ResourceStore resultStore,
      ApiRequestContext requestContext,
      List<String> prefer,
      QueryExpression query,
      boolean storedQuery) {
    String queryJson;
    try {
      queryJson = QueryExpression.MAPPER.writeValueAsString(query);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not serialize the query expression", e);
    }

    Job job =
        jobs.push(
            SearchJob.of(
                requestContext.getApi().getData().getId(),
                // parameters are kept: paging links replace offset/f/lang but must preserve the
                // stored query's own parameters (e.g. /search/q?owner=foo)
                requestContext.getUriCustomizer().copy().toString(),
                queryJson,
                storedQuery,
                requestContext.getMediaType().type().toString(),
                requestContext.getMediaType().label(),
                requestContext.getMediaType().parameter(),
                requestContext.getLanguage().map(Locale::toLanguageTag).orElse(null)));

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Accepted asynchronous search as job {}", job.id());
    }

    // honor the wait preference: defer the response for up to min(wait, maxWait) seconds and
    // answer synchronously when the job finishes in time
    Optional<Integer> sentWait = HeaderPrefer.parseWait(prefer).filter(seconds -> seconds > 0);
    if (sentWait.isPresent()) {
      int maxWait =
          Optional.ofNullable(
                  requestContext
                      .getApi()
                      .getData()
                      .getExtension(SearchConfiguration.class)
                      .map(SearchConfiguration::getMaxWait)
                      .orElse(null))
              .orElse(60);
      int waitSeconds = Math.min(sentWait.get(), maxWait);
      boolean waitCapped = waitSeconds < sentWait.get();
      try {
        Job finished = jobs.waitFor(job.id()).get(waitSeconds, TimeUnit.SECONDS);
        Optional<Response> syncResponse =
            syncResponse(finished, resultStore, requestContext, "wait=" + sentWait.get());
        if (syncResponse.isPresent()) {
          return syncResponse.get();
        }
      } catch (TimeoutException e) {
        // the job keeps running, fall through to 202
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (ExecutionException e) {
        LOGGER.warn("Waiting for search job {} failed", job.id(), e);
      }
      if (waitCapped) {
        // the client's wait was not fully honored, do not claim it
        sentWait = Optional.empty();
      }
    }

    // TODO: the jobs API is not yet available; the Location target ({apiUri}/jobs/{jobId})
    // returns 404 until it lands
    URI location =
        URI.create(
            requestContext.getApiUriCustomizer().appendPathSegments("jobs", job.id()).toString());

    return Response.accepted()
        .location(location)
        .header(
            "Preference-Applied",
            "respond-async" + sentWait.map(seconds -> ", wait=" + seconds).orElse(""))
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(Map.of("jobId", job.id()))
        .build();
  }

  /**
   * The regular synchronous response, streamed from the stored result document, which is deleted
   * once the response is complete. Empty when the job failed or has no result — the client then
   * gets the 202 and can inspect the job.
   */
  private static Optional<Response> syncResponse(
      Job finished,
      ResourceStore resultStore,
      ApiRequestContext requestContext,
      String preferenceApplied) {
    if (finished.status() != Identifiers.Status.SUCCESSFUL) {
      return Optional.empty();
    }
    Object resultPath = finished.outputs().get("resultPath");
    if (resultPath == null) {
      return Optional.empty();
    }
    Path path = Path.of(resultPath.toString());

    StreamingOutput streamingOutput =
        output -> {
          Optional<InputStream> result = resultStore.content(path);
          if (result.isEmpty()) {
            throw new IllegalStateException("The stored search result is missing: " + path);
          }
          try (InputStream in = result.get()) {
            in.transferTo(output);
          }
          // the response is delivered synchronously, the stored copy is no longer needed
          try {
            resultStore.delete(path);
          } catch (IOException e) {
            LOGGER.warn("Could not delete the result of search job {}", finished.id());
          }
        };

    return Optional.of(
        Response.ok(streamingOutput)
            .type(requestContext.getMediaType().type())
            .header("Preference-Applied", preferenceApplied)
            .build());
  }
}
