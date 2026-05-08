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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;

/**
 * @title QGIS
 */
@Singleton
@AutoBind
public class StyleFormatQgisQml implements StyleFormatExtension {

  static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("application", "vnd.qgis.qml"))
          .label("QGIS")
          .parameter("qml")
          .build();

  @Inject
  StyleFormatQgisQml() {}

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
    return "qml";
  }

  @Override
  public String getSpecification() {
    return "https://docs.qgis.org/3.16/en/docs/user_manual/appendices/qgis_file_formats.html#qml-the-qgis-style-file-format";
  }

  @Override
  public String getVersion() {
    return "3.16";
  }
}
