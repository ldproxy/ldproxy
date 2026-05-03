/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tilematrixsets.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ClassSchemaCache;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetFormatExtension;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetOgcApi;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * @title JSON
 */
@Singleton
@AutoBind
public class TileMatrixSetFormatJson implements TileMatrixSetFormatExtension {

  private final Schema<?> schemaStyleTileMatrixSet;
  private final Map<String, Schema<?>> referencedSchemasTileMatrixSet;

  @Inject
  public TileMatrixSetFormatJson(ClassSchemaCache classSchemaCache) {
    schemaStyleTileMatrixSet = classSchemaCache.getSchema(TileMatrixSetOgcApi.class);
    referencedSchemasTileMatrixSet =
        classSchemaCache.getReferencedSchemas(TileMatrixSetOgcApi.class);
  }

  @Override
  public ApiMediaType getMediaType() {
    return ApiMediaType.JSON_MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(schemaStyleTileMatrixSet)
        .schemaRef(TileMatrixSetOgcApi.SCHEMA_REF)
        .referencedSchemas(referencedSchemasTileMatrixSet)
        .ogcApiMediaType(getMediaType())
        .build();
  }

  @Override
  public Object getEntity(
      TileMatrixSetOgcApi tileMatrixSet, OgcApi api, ApiRequestContext requestContext) {
    return tileMatrixSet;
  }
}
