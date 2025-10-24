/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import static de.ii.ogcapi.foundation.domain.ApiMediaType.JSON_SCHEMA_MEDIA_TYPE;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.SchemaFormatExtension;
import de.ii.ogcapi.features.core.domain.SchemaType;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaDocument;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class SchemaFormatJsonSchema implements SchemaFormatExtension {

  @Inject
  SchemaFormatJsonSchema() {}

  @Override
  public ApiMediaType getMediaType() {
    return JSON_SCHEMA_MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return SCHEMA_CONTENT;
  }

  // @Override
  public Object getEntity(
      JsonSchemaDocument schema,
      SchemaType type,
      List<Link> links,
      String collectionId,
      OgcApi api,
      ApiRequestContext requestContext) {
    return schema;
  }
}
