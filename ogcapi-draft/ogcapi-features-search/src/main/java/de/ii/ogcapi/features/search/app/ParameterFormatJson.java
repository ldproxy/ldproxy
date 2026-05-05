/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.search.domain.Parameter;
import de.ii.ogcapi.features.search.domain.ParameterFormat;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ClassSchemaCache;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;

@Singleton
@AutoBind
public class ParameterFormatJson implements ParameterFormat {

  static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(MediaType.valueOf("application/schema+json"))
          .label("JSON Schema")
          .parameter("json")
          .fileExtension("json")
          .build();

  private final Schema<?> schema;

  @Inject
  public ParameterFormatJson(ClassSchemaCache classSchemaCache) {
    this.schema = new ObjectSchema();
    classSchemaCache.registerSchema(Parameter.class, schema, ImmutableList.of());
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(schema)
        .schemaRef(Parameter.SCHEMA_REF)
        .ogcApiMediaType(MEDIA_TYPE)
        .build();
  }

  @Override
  public ApiMediaType getMediaType() {
    return MEDIA_TYPE;
  }

  @Override
  public Object getEntity(
      Parameter parameter, OgcApiDataV2 apiData, ApiRequestContext requestContext) {
    return parameter.getSchema();
  }
}
