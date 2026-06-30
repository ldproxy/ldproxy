/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app.json;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ClassSchemaCache;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.processes.domain.ProcessDescriptionFormatExtension;
import de.ii.ogcapi.processes.domain.model.ProcessOgcApi;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;

/**
 * @title JSON
 */
@Singleton
@AutoBind
public class ProcessDescriptionFormatJson implements ProcessDescriptionFormatExtension {

  private final Schema<?> schemaStyleProcessDescription;
  private final Map<String, Schema<?>> referencedSchemasProcessDescription;

  @Inject
  public ProcessDescriptionFormatJson(ClassSchemaCache classSchemaCache) {
    schemaStyleProcessDescription = classSchemaCache.getSchema(ProcessOgcApi.class);
    referencedSchemasProcessDescription =
        classSchemaCache.getReferencedSchemas(ProcessOgcApi.class);
  }

  @Override
  public ApiMediaType getMediaType() {
    return ApiMediaType.JSON_MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(schemaStyleProcessDescription)
        .schemaRef(ProcessOgcApi.SCHEMA_REF)
        .referencedSchemas(referencedSchemasProcessDescription)
        .ogcApiMediaType(getMediaType())
        .build();
  }

  @Override
  public Object getEntity(
      ProcessOgcApi processDescriptionOgcApi, OgcApi api, ApiRequestContext requestContext) {
    return processDescriptionOgcApi;
  }
}
