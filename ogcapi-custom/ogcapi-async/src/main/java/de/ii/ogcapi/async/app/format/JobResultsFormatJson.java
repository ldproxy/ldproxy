/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.async.app.format;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.common.domain.async.JobResultsFormatExtension;
import de.ii.ogcapi.common.domain.async.OgcApiJobResults;
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
public class JobResultsFormatJson implements JobResultsFormatExtension, ConformanceClass {

  private final Schema<?> schemaResults;
  private final Map<String, Schema<?>> referencedSchemasResults;

  @Inject
  public JobResultsFormatJson(ClassSchemaCache classSchemaCache) {
    schemaResults = classSchemaCache.getSchema(OgcApiJobResults.class);
    referencedSchemasResults = classSchemaCache.getReferencedSchemas(OgcApiJobResults.class);
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
        .schema(schemaResults)
        .schemaRef(OgcApiJobResults.SCHEMA_REF)
        .referencedSchemas(referencedSchemasResults)
        .ogcApiMediaType(getMediaType())
        .build();
  }

  @Override
  public Object getEntity(OgcApiJobResults results, OgcApi api, ApiRequestContext requestContext) {
    return results;
  }
}
