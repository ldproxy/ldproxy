/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.async.app.format;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.async.app.AsyncBuildingBlock;
import de.ii.ogcapi.async.domain.AsyncConfiguration;
import de.ii.ogcapi.common.domain.async.JobResultsFormatExtension;
import de.ii.ogcapi.common.domain.async.OgcApiJobResults;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import io.swagger.v3.oas.models.media.BinarySchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;

/**
 * The media type of a job result document is only known per job, but content negotiation happens
 * per endpoint before the job is resolved. This format lets any media type through, the results
 * handler then negotiates against the actual result.
 *
 * @title Any
 */
@Singleton
@AutoBind
public class JobResultsFormatAny implements JobResultsFormatExtension {

  public static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(MediaType.WILDCARD_TYPE)
          .label("Any")
          .parameter("any")
          .qs(100)
          .build();

  private static final ApiMediaTypeContent CONTENT =
      new ImmutableApiMediaTypeContent.Builder()
          .schema(new BinarySchema())
          .schemaRef("#/components/schemas/JobResultDocument")
          .ogcApiMediaType(MEDIA_TYPE)
          .build();

  @Inject
  JobResultsFormatAny() {}

  @Override
  public ApiMediaType getMediaType() {
    return MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return CONTENT;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return AsyncConfiguration.class;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return AsyncBuildingBlock.isAsyncEnabled(apiData);
  }

  @Override
  public Object getEntity(OgcApiJobResults results, OgcApi api, ApiRequestContext requestContext) {
    throw new IllegalStateException("Job results are never encoded in an arbitrary media type");
  }
}
