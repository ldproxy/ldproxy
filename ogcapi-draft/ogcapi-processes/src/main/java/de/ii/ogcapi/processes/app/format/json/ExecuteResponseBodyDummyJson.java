/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app.format.json;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ClassSchemaCache;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.processes.domain.format.ExecuteResponseBodyFormatExtension;
import de.ii.ogcapi.processes.domain.model.ExecuteResponseBodyDummy;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;

/**
 * @title JSON
 */
@Singleton
@AutoBind
public class ExecuteResponseBodyDummyJson implements ExecuteResponseBodyFormatExtension {

  private final Schema<?> schemaStyleProcess;
  private final Map<String, Schema<?>> referencedSchemasProcess;

  @Inject
  public ExecuteResponseBodyDummyJson(ClassSchemaCache classSchemaCache) {
    schemaStyleProcess = classSchemaCache.getSchema(ExecuteResponseBodyDummy.class);
    referencedSchemasProcess =
        classSchemaCache.getReferencedSchemas(ExecuteResponseBodyDummy.class);
  }

  @Override
  public ApiMediaType getMediaType() {
    return ApiMediaType.JSON_MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(schemaStyleProcess)
        .schemaRef(ExecuteResponseBodyDummy.SCHEMA_REF)
        .referencedSchemas(referencedSchemasProcess)
        .ogcApiMediaType(getMediaType())
        .build();
  }

  @Override
  public Object getEntity(
      ExecuteResponseBodyDummy responseBody, OgcApi api, ApiRequestContext requestContext) {
    return responseBody;
  }
}
