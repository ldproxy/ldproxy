/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.app.xml;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.collections.domain.Collections;
import de.ii.ogcapi.collections.domain.CollectionsFormatExtension;
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
public class CollectionsFormatXml implements CollectionsFormatExtension {

  @Inject
  public CollectionsFormatXml() {}

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
  public Object getEntity(Collections collections, OgcApi api, ApiRequestContext requestContext) {
    return new OgcApiCollectionsXml(collections);
  }
}
