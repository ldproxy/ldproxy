/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.app.xml;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.common.domain.LandingPage;
import de.ii.ogcapi.common.domain.LandingPageFormatExtension;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * @title XML
 */
@Singleton
@AutoBind
public class LandingPageFormatXml implements LandingPageFormatExtension {

  @Inject
  public LandingPageFormatXml() {}

  @Override
  public ApiMediaType getMediaType() {
    return ApiMediaType.XML_MEDIA_TYPE;
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
  public boolean isEnabledByDefault() {
    return false;
  }

  @Override
  public Object getEntity(
      LandingPage apiLandingPage, OgcApi api, ApiRequestContext requestContext) {
    String title = requestContext.getApi().getData().getLabel();
    String description = requestContext.getApi().getData().getDescription().orElse(null);
    return new LandingPageXml(apiLandingPage.getLinks(), title, description);
  }
}
