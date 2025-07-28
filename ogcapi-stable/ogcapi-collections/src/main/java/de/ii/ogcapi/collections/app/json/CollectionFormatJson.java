/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.app.json;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.collections.domain.CollectionFormatExtension;
import de.ii.ogcapi.collections.domain.OgcApiCollection;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ClassSchemaCache;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title JSON
 */
@Singleton
@AutoBind
public class CollectionFormatJson implements CollectionFormatExtension, ConformanceClass {

  private final Schema<?> schemaCollection;
  private final Map<String, Schema<?>> referencedSchemasCollection;

  @Inject
  public CollectionFormatJson(ClassSchemaCache classSchemaCache) {
    schemaCollection = classSchemaCache.getSchema(OgcApiCollection.class);
    referencedSchemasCollection = classSchemaCache.getReferencedSchemas(OgcApiCollection.class);
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of("http://www.opengis.net/spec/ogcapi-common-2/0.0/conf/json");
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(schemaCollection)
        .schemaRef(OgcApiCollection.SCHEMA_REF)
        .referencedSchemas(referencedSchemasCollection)
        .ogcApiMediaType(getMediaType())
        .build();
  }

  @Override
  public ApiMediaType getMediaType() {
    return ApiMediaType.JSON_MEDIA_TYPE;
  }

  @Override
  public Object getEntity(
      OgcApiCollection ogcApiCollection, OgcApi api, ApiRequestContext requestContext) {
    return ogcApiCollection;
  }
}
