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
import de.ii.ogcapi.processes.domain.format.ValuesFormatExtension;
import de.ii.ogcapi.processes.domain.model.ogc.OgcValues;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;

/**
 * @title JSON
 */
@Singleton
@AutoBind
public class ValuesFormatJson implements ValuesFormatExtension {

  private final Schema<?> schemaValues;
  private final Map<String, Schema<?>> referencedSchemasValues;

  @Inject
  public ValuesFormatJson(ClassSchemaCache classSchemaCache) {
    schemaValues = classSchemaCache.getSchema(OgcValues.class);
    referencedSchemasValues = classSchemaCache.getReferencedSchemas(OgcValues.class);
  }

  @Override
  public ApiMediaType getMediaType() {
    return ApiMediaType.JSON_MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(schemaValues)
        .schemaRef(OgcValues.SCHEMA_REF)
        .referencedSchemas(referencedSchemasValues)
        .ogcApiMediaType(getMediaType())
        .build();
  }

  @Override
  public Object getEntity(OgcValues values, OgcApi api, ApiRequestContext requestContext) {
    return values;
  }
}
