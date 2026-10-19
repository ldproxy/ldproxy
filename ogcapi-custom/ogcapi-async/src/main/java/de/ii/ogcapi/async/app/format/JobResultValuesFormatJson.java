/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.async.app.format;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.async.domain.JobResultValuesFormatExtension;
import de.ii.ogcapi.common.domain.async.OgcApiJobResultValues;
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
public class JobResultValuesFormatJson implements JobResultValuesFormatExtension, ConformanceClass {

  private final Schema<?> schemaValues;
  private final Map<String, Schema<?>> referencedSchemasValues;

  @Inject
  public JobResultValuesFormatJson(ClassSchemaCache classSchemaCache) {
    schemaValues = classSchemaCache.getSchema(OgcApiJobResultValues.class);
    referencedSchemasValues = classSchemaCache.getReferencedSchemas(OgcApiJobResultValues.class);
  }

  // TODO
  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    if (isEnabledForApi(apiData)) {
      return ImmutableList.of("https://www.opengis.net/spec/ogcapi-processes-1/2.0/conf/json");
    }

    return ImmutableList.of();
  }

  @Override
  public ApiMediaType getMediaType() {
    return ApiMediaType.JSON_MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(schemaValues)
        .schemaRef(OgcApiJobResultValues.SCHEMA_REF)
        .referencedSchemas(referencedSchemasValues)
        .ogcApiMediaType(getMediaType())
        .build();
  }

  @Override
  public Object getEntity(
      OgcApiJobResultValues values, OgcApi api, ApiRequestContext requestContext) {
    return values;
  }
}
