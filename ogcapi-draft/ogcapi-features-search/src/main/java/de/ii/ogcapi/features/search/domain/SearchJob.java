/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.common.domain.async.JobContextAsync;
import de.ii.ogcapi.common.domain.async.OgcApiJobStatus.EntityType;
import de.ii.xtralink.jobs.JobConfiguration;
import de.ii.xtraplatform.xtralink.domain.JobInputs;
import de.ii.xtraplatform.xtralink.domain.JobProcessorSimple.Phase;
import de.ii.xtraplatform.xtralink.domain.Jobs;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * Inputs of an asynchronous search job. The result document is always written to the resources
 * store since feature collections can be large.
 *
 * <p>The query is given in one of two forms:
 *
 * <ul>
 *   <li>the resolved query expression, together with the request URI and the media type; this is
 *       how the API submits a request with {@code Prefer: respond-async}
 *   <li>the id of a stored query and its parameter values, as query parameters of {@code
 *       /search/{queryId}} including {@code f}; this is how a job is pushed by others, e.g. a
 *       workflow, without an HTTP request. The query is resolved when the job is executed.
 * </ul>
 */
@Value.Immutable
@JsonDeserialize(builder = ImmutableSearchJob.Builder.class)
public interface SearchJob extends JobInputs {

  String KIND = "feature-query";
  String LABEL = "Feature query";

  static JobConfiguration of(
      String apiId,
      String requestUri,
      String queryExpression,
      boolean storedQuery,
      String mediaType,
      String mediaTypeLabel,
      String mediaTypeParameter,
      @Nullable String language,
      long ttlSeconds) {
    ImmutableSearchJob searchJob =
        new ImmutableSearchJob.Builder()
            .apiId(apiId)
            .requestUri(requestUri)
            .queryExpression(queryExpression)
            .isStoredQuery(storedQuery)
            .mediaType(mediaType)
            .mediaTypeLabel(mediaTypeLabel)
            .mediaTypeParameter(mediaTypeParameter)
            .language(language)
            .build();
    return Jobs.create(
        KIND,
        1000,
        LABEL,
        String.format(" (API: %s)", apiId),
        searchJob,
        new JobContextAsync(apiId, EntityType.OGC_API_FEATURES, Optional.empty(), Optional.empty()),
        null,
        List.of(Phase.EXECUTE.getPartialKind(KIND)),
        Optional.of((int) ttlSeconds));
  }

  String getApiId();

  /**
   * The original request URI; per-feature and paging links are derived from it. Derived from the
   * stored query and its parameters, if not set.
   */
  @Nullable
  String getRequestUri();

  /** The resolved query expression (stored-query parameters already applied), as JSON. */
  @Nullable
  String getQueryExpression();

  /** The id of a stored query to execute, instead of a query expression. */
  @Nullable
  String getQueryId();

  /** The parameter values of the stored query, including {@code f}. */
  Map<String, String> getParameters();

  @Value.Default
  default boolean getIsStoredQuery() {
    return getQueryId() != null;
  }

  @Nullable
  String getMediaType();

  @Nullable
  String getMediaTypeLabel();

  @Nullable
  String getMediaTypeParameter();

  @Nullable
  String getLanguage();
}
