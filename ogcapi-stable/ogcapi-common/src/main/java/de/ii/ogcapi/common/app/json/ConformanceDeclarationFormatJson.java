/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.app.json;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.common.domain.ConformanceDeclaration;
import de.ii.ogcapi.common.domain.ConformanceDeclarationFormatExtension;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ClassSchemaCache;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApi;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;

/**
 * @title JSON
 */
@Singleton
@AutoBind
public class ConformanceDeclarationFormatJson implements ConformanceDeclarationFormatExtension {

  private final Schema<?> schemaConformance;
  private final Map<String, Schema<?>> referencedSchemasConformance;

  @Inject
  public ConformanceDeclarationFormatJson(ClassSchemaCache classSchemaCache) {
    schemaConformance = classSchemaCache.getSchema(ConformanceDeclaration.class);
    referencedSchemasConformance =
        classSchemaCache.getReferencedSchemas(ConformanceDeclaration.class);
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(schemaConformance)
        .schemaRef(ConformanceDeclaration.SCHEMA_REF)
        .referencedSchemas(referencedSchemasConformance)
        .ogcApiMediaType(getMediaType())
        .build();
  }

  @Override
  public ApiMediaType getMediaType() {
    return ApiMediaType.JSON_MEDIA_TYPE;
  }

  @Override
  public Object getEntity(
      ConformanceDeclaration conformanceDeclaration, OgcApi api, ApiRequestContext requestContext) {
    return conformanceDeclaration;
  }
}
