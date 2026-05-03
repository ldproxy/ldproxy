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
import de.ii.ogcapi.features.search.domain.Parameters;
import de.ii.ogcapi.features.search.domain.ParametersFormat;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ClassSchemaCache;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

@Singleton
@AutoBind
public class ParametersFormatJson implements ParametersFormat {

  static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(MediaType.APPLICATION_JSON_TYPE)
          .label("JSON")
          .parameter("json")
          .fileExtension("json")
          .build();

  private final Schema<?> schema;
  private final Map<String, Schema<?>> referencedSchemas;

  @Inject
  public ParametersFormatJson(ClassSchemaCache classSchemaCache) {
    // Set the schema for JSON Schema.
    // Note that setting a schema in onStartup has no effect since
    // that is executed *after* the API definition has been compiled.
    // TODO find better solution
    classSchemaCache.registerSchema(
        Parameters.class,
        new ObjectSchema()
            .addProperties("title", new StringSchema())
            .addProperties("description", new StringSchema())
            .addProperties(
                "links", new ArraySchema().items(new Schema<>().$ref("#/components/schemas/Link")))
            .additionalProperties(new ObjectSchema()),
        ImmutableList.of(Link.class));
    this.schema = classSchemaCache.getSchema(Parameters.class);
    referencedSchemas = classSchemaCache.getReferencedSchemas(Parameters.class);
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(schema)
        .schemaRef(Parameters.SCHEMA_REF)
        .referencedSchemas(referencedSchemas)
        .ogcApiMediaType(MEDIA_TYPE)
        .build();
  }

  @Override
  public ApiMediaType getMediaType() {
    return MEDIA_TYPE;
  }

  @Override
  public Object getEntity(
      Parameters parameters, OgcApiDataV2 apiData, ApiRequestContext requestContext) {
    return parameters;
  }
}
