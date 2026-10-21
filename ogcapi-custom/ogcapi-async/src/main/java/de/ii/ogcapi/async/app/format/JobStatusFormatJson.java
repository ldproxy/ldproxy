/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.async.app.format;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.common.domain.async.JobStatusFormatExtension;
import de.ii.ogcapi.common.domain.async.OgcApiJobStatus;
import de.ii.ogcapi.common.domain.async.OgcApiJobStatusInfo;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ClassSchemaCache;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;

/**
 * @title JSON
 */
@Singleton
@AutoBind
public class JobStatusFormatJson implements JobStatusFormatExtension, ConformanceClass {

  private final Schema<?> schemaStatusInfo;
  private final Map<String, Schema<?>> referencedSchemasStatusInfo;

  @Inject
  public JobStatusFormatJson(ClassSchemaCache classSchemaCache) {
    schemaStatusInfo = classSchemaCache.getSchema(OgcApiJobStatusInfo.class);
    referencedSchemasStatusInfo = classSchemaCache.getReferencedSchemas(OgcApiJobStatusInfo.class);
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return List.of("https://www.opengis.net/spec/ogcapi-processes-1/2.0/conf/json");
  }

  @Override
  public ApiMediaType getMediaType() {
    return ApiMediaType.JSON_MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(schemaStatusInfo)
        .schemaRef(OgcApiJobStatusInfo.SCHEMA_REF)
        .referencedSchemas(referencedSchemasStatusInfo)
        .ogcApiMediaType(getMediaType())
        .build();
  }

  @Override
  public Object getEntity(OgcApiJobStatus response, OgcApi api, ApiRequestContext requestContext) {
    return response;
  }
}
