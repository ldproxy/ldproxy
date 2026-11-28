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
import de.ii.ogcapi.collections.domain.CollectionExtension;
import de.ii.ogcapi.collections.domain.Collections;
import de.ii.ogcapi.collections.domain.CollectionsFormatExtension;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ClassSchemaCache;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
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
public class CollectionsFormatJson implements CollectionsFormatExtension, ConformanceClass {

  private final Schema<?> schemaCollections;
  private final Map<String, Schema<?>> referencedSchemasCollections;
  private final ExtensionRegistry extensionRegistry;

  @Inject
  public CollectionsFormatJson(
      ClassSchemaCache classSchemaCache, ExtensionRegistry extensionRegistry) {
    this.extensionRegistry = extensionRegistry;
    schemaCollections = classSchemaCache.getSchema(Collections.class);
    referencedSchemasCollections = classSchemaCache.getReferencedSchemas(Collections.class);
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    if (!CollectionExtension.areNestedCollectionsIdentical(extensionRegistry, apiData)) {
      return ImmutableList.of();
    }
    return ImmutableList.of("https://www.opengis.net/spec/ogcapi-common-2/1.0/conf/json");
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(schemaCollections)
        .schemaRef(Collections.SCHEMA_REF)
        .referencedSchemas(referencedSchemasCollections)
        .ogcApiMediaType(getMediaType())
        .build();
  }

  @Override
  public ApiMediaType getMediaType() {
    return ApiMediaType.JSON_MEDIA_TYPE;
  }

  @Override
  public Object getEntity(Collections collections, OgcApi api, ApiRequestContext requestContext) {
    return collections;
  }
}
