/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.filter.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.filter.domain.FunctionsFormatExtension;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApi;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title JSON
 */
@Singleton
@AutoBind
public class FunctionsFormatJson implements FunctionsFormatExtension {

  private static final ApiMediaTypeContent FUNCTIONS_CONTENT =
      new ImmutableApiMediaTypeContent.Builder()
          .schema(
              new ObjectSchema()
                  .addRequiredItem("functions")
                  .addProperty(
                      "functions",
                      new ArraySchema()
                          .items(
                              new ObjectSchema()
                                  .addRequiredItem("name")
                                  .addRequiredItem("returns")
                                  .addProperty("name", new StringSchema())
                                  .addProperty("description", new StringSchema())
                                  .addProperty(
                                      "arguments",
                                      new ArraySchema()
                                          .items(
                                              new ObjectSchema()
                                                  .addProperty("name", new StringSchema())
                                                  .addProperty("description", new StringSchema())
                                                  .addRequiredItem("type")
                                                  .addProperty(
                                                      "type",
                                                      new ArraySchema().items(new StringSchema()))))
                                  .addProperty(
                                      "returns", new ArraySchema().items(new StringSchema())))))
          .schemaRef(FormatExtension.OBJECT_SCHEMA_REF)
          .ogcApiMediaType(ApiMediaType.JSON_MEDIA_TYPE)
          .build();

  @Inject
  public FunctionsFormatJson() {}

  @Override
  public ApiMediaType getMediaType() {
    return ApiMediaType.JSON_MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return FUNCTIONS_CONTENT;
  }

  @Override
  public Object getEntity(
      List<Map<String, Object>> functionDefinitions, OgcApi api, ApiRequestContext requestContext) {
    Map<String, Object> entity = new LinkedHashMap<>();
    entity.put("functions", functionDefinitions);
    return entity;
  }
}
