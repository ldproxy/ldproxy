/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.styles.domain.StyleFormatExtension;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title ArcGIS Pro
 */
@Singleton
@AutoBind
public class StyleFormatArcGisProLayer implements StyleFormatExtension {

  private static final Logger LOGGER = LoggerFactory.getLogger(StyleFormatArcGisProLayer.class);

  static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("application", "vnd.esri.lyrx"))
          .label("ArcGIS Pro")
          .parameter("lyrx")
          .build();

  @Inject
  StyleFormatArcGisProLayer() {}

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
        .schema(BINARY_SCHEMA)
        .schemaRef(BINARY_SCHEMA_REF)
        .ogcApiMediaType(getMediaType())
        .build();
  }

  @Override
  public String getFileExtension() {
    return "lyr";
  }

  @Override
  public String getSpecification() {
    return "https://www.esri.com/";
  }

  @Override
  public String getVersion() {
    return "n/a";
  }
}
