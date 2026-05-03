/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.codelists.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.codelists.domain.Codelists;
import de.ii.ogcapi.codelists.domain.CodelistsFormatExtension;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ClassSchemaCache;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * @title JSON
 */
@Singleton
@AutoBind
public class CodelistsFormatJson implements CodelistsFormatExtension {

  private final Schema<?> schemaCodelists;
  private final Map<String, Schema<?>> referencedSchemas;

  @Inject
  public CodelistsFormatJson(ClassSchemaCache classSchemaCache) {
    schemaCodelists = classSchemaCache.getSchema(Codelists.class);
    referencedSchemas = classSchemaCache.getReferencedSchemas(Codelists.class);
  }

  @Override
  public ApiMediaType getMediaType() {
    return ApiMediaType.JSON_MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(schemaCodelists)
        .schemaRef(Codelists.SCHEMA_REF)
        .referencedSchemas(referencedSchemas)
        .ogcApiMediaType(getMediaType())
        .build();
  }

  @Override
  public Object getCodelistsEntity(
      Codelists codelists, OgcApiDataV2 apiData, ApiRequestContext requestContext) {
    return codelists;
  }
}
