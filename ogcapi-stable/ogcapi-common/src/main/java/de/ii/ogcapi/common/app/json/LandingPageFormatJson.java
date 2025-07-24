/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.app.json;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.common.domain.ImmutableLandingPage;
import de.ii.ogcapi.common.domain.LandingPage;
import de.ii.ogcapi.common.domain.LandingPageFormatExtension;
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
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title JSON
 */
@Singleton
@AutoBind
public class LandingPageFormatJson implements LandingPageFormatExtension, ConformanceClass {

  private final Schema<?> schemaLandingPage;
  private final Map<String, Schema<?>> referencedSchemasLandingPage;

  @Inject
  public LandingPageFormatJson(ClassSchemaCache classSchemaCache) {
    schemaLandingPage = classSchemaCache.getSchema(LandingPage.class);
    referencedSchemasLandingPage = classSchemaCache.getReferencedSchemas(LandingPage.class);
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of("http://www.opengis.net/spec/ogcapi-common-1/1.0/conf/json");
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(schemaLandingPage)
        .schemaRef(LandingPage.SCHEMA_REF)
        .referencedSchemas(referencedSchemasLandingPage)
        .ogcApiMediaType(getMediaType())
        .build();
  }

  @Override
  public ApiMediaType getMediaType() {
    return ApiMediaType.JSON_MEDIA_TYPE;
  }

  @Override
  public Object getEntity(
      LandingPage apiLandingPage, OgcApi api, ApiRequestContext requestContext) {
    return new ImmutableLandingPage.Builder()
        .from(apiLandingPage)
        .extensions(
            apiLandingPage.getExtensions().entrySet().stream()
                .filter(entry -> !entry.getKey().equals("datasetDownloadLinks"))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)))
        .build();
  }
}
