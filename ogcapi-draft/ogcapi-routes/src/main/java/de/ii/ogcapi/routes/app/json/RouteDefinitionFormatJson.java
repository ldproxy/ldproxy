/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.routes.app.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriterRegistry;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ClassSchemaCache;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.routes.domain.RouteDefinition;
import de.ii.ogcapi.routes.domain.RouteDefinitionFormatExtension;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title JSON
 */
@Singleton
@AutoBind
public class RouteDefinitionFormatJson implements RouteDefinitionFormatExtension {

  private static final Logger LOGGER = LoggerFactory.getLogger(RouteDefinitionFormatJson.class);

  public static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(MediaType.APPLICATION_JSON_TYPE)
          .label("JSON")
          .parameter("json")
          .build();

  private final Schema<?> schemaRouteDefinition;
  private final Map<String, Schema<?>> referencedSchemas;

  @Inject
  public RouteDefinitionFormatJson(
      ClassSchemaCache classSchemaCache, GeoJsonWriterRegistry geoJsonWriterRegistry) {
    this.schemaRouteDefinition = classSchemaCache.getSchema(RouteDefinition.class);
    referencedSchemas = classSchemaCache.getReferencedSchemas(RouteDefinition.class);
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(schemaRouteDefinition)
        .schemaRef(RouteDefinition.SCHEMA_REF)
        .referencedSchemas(referencedSchemas)
        .ogcApiMediaType(MEDIA_TYPE)
        .build();
  }

  @Override
  public ApiMediaType getMediaType() {
    return MEDIA_TYPE;
  }

  @Override
  public byte[] getRouteDefinitionAsByteArray(
      RouteDefinition routeDefinition, OgcApiDataV2 apiData, ApiRequestContext requestContext) {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new Jdk8Module());
    try {
      return mapper.writeValueAsBytes(routeDefinition);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not write route definition.", e);
    }
  }
}
