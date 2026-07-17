/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app.format.json;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ClassSchemaCache;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.processes.domain.format.StatusInfoFormatExtension;
import de.ii.ogcapi.processes.domain.model.ogc.OgcStatusInfo;
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
public class StatusInfoFormatJson implements StatusInfoFormatExtension, ConformanceClass {

  private final Schema<?> schemaStatusInfo;
  private final Map<String, Schema<?>> referencedSchemasStatusInfo;

  @Inject
  public StatusInfoFormatJson(ClassSchemaCache classSchemaCache) {
    schemaStatusInfo = classSchemaCache.getSchema(OgcStatusInfo.class);
    referencedSchemasStatusInfo = classSchemaCache.getReferencedSchemas(OgcStatusInfo.class);
  }

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
        .schema(schemaStatusInfo)
        .schemaRef(OgcStatusInfo.SCHEMA_REF)
        .referencedSchemas(referencedSchemasStatusInfo)
        .ogcApiMediaType(getMediaType())
        .build();
  }

  @Override
  public Object getEntity(OgcStatusInfo response, OgcApi api, ApiRequestContext requestContext) {
    return response;
  }
}
