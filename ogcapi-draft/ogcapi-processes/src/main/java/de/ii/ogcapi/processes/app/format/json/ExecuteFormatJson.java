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
import de.ii.ogcapi.processes.domain.format.ExecuteFormatExtension;
import de.ii.ogcapi.processes.domain.model.ogc.OgcExecute;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;

/**
 * @title JSON
 */
@Singleton
@AutoBind
public class ExecuteFormatJson implements ExecuteFormatExtension {

  private final Schema<?> schemaExecute;
  private final Map<String, Schema<?>> referencedSchemasExecute;

  @Inject
  public ExecuteFormatJson(ClassSchemaCache classSchemaCache) {
    schemaExecute = classSchemaCache.getSchema(OgcExecute.class);
    referencedSchemasExecute = classSchemaCache.getReferencedSchemas(OgcExecute.class);
  }

  @Override
  public ApiMediaType getMediaType() {
    return ApiMediaType.JSON_MEDIA_TYPE;
  }

  @Override
  public boolean canSupportTransactions() {
    return true;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(schemaExecute)
        .schemaRef(OgcExecute.SCHEMA_REF)
        .referencedSchemas(referencedSchemasExecute)
        .ogcApiMediaType(getMediaType())
        .build();
  }

  @Override
  public Object getEntity(OgcExecute execute, OgcApi api, ApiRequestContext requestContext) {
    return execute;
  }
}
