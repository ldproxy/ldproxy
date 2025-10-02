/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.styles.domain.StyleFormatExtension;
import de.ii.ogcapi.styles.domain.StylesheetContent;
import de.ii.xtraplatform.tiles.domain.TileMatrixSet;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;

/**
 * @title SLD 1.1
 */
@Singleton
@AutoBind
public class StyleFormatSld11 implements ConformanceClass, StyleFormatExtension {

  static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("application", "vnd.ogc.sld+xml", ImmutableMap.of("version", "1.1")))
          .label("SLD 1.1")
          .parameter("sld11")
          .fileExtension("sld")
          .build();

  @Inject
  StyleFormatSld11() {}

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of("http://www.opengis.net/spec/ogcapi-styles-1/0.0/conf/sld-11");
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  @Override
  public boolean canSupportTransactions() {
    return true;
  }

  @Override
  public ApiMediaType getMediaType() {
    return MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(OBJECT_SCHEMA)
        .schemaRef(OBJECT_SCHEMA_REF)
        .ogcApiMediaType(getMediaType())
        .build();
  }

  @Override
  public String getFileExtension() {
    return "sld11";
  }

  @Override
  public String getSpecification() {
    return "https://www.ogc.org/standards/sld";
  }

  @Override
  public String getVersion() {
    return "1.1";
  }

  @Override
  public Object getStyleEntity(
      StylesheetContent stylesheetContent,
      OgcApi api,
      Optional<String> collectionId,
      String styleId,
      Optional<TileMatrixSet> tileMatrixSet,
      ApiRequestContext requestContext) {
    return stylesheetContent.getContent();
  }
}
