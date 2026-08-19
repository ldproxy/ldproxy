/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.xtralink.jobs.JobConfiguration;
import de.ii.xtraplatform.xtralink.domain.JobContext.JobContextEntity;
import de.ii.xtraplatform.xtralink.domain.JobInputs;
import de.ii.xtraplatform.xtralink.domain.Jobs;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * Inputs of an asynchronous search job. The (resolved) query expression is stored directly on the
 * job; the result document is always written to the resources store since feature collections can
 * be large.
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
      @Nullable String language) {
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
        new JobContextEntity(apiId),
        null);
  }

  String getApiId();

  /** The original request URI; per-feature and paging links are derived from it. */
  String getRequestUri();

  /** The resolved query expression (stored-query parameters already applied), as JSON. */
  String getQueryExpression();

  boolean getIsStoredQuery();

  String getMediaType();

  String getMediaTypeLabel();

  String getMediaTypeParameter();

  @Nullable
  String getLanguage();
}
