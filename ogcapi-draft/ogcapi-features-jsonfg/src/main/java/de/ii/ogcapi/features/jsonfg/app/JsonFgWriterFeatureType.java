/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.jsonfg.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.collections.schema.domain.SchemaConfiguration;
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.FeatureTransformationContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriter;
import de.ii.ogcapi.features.jsonfg.domain.JsonFgConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class JsonFgWriterFeatureType implements GeoJsonWriter {

  static final String OPEN_TEMPLATE = "{{";
  public static String JSON_KEY = "featureType";
  public static String JSON_KEY_SCHEMA = "featureSchema";

  Map<String, String> collectionMap;
  boolean isEnabled;
  boolean homogenous;
  String writeAtEnd;
  Map<String, String> schemaMap;
  Map<String, String> effectiveSchemas;

  @Inject
  JsonFgWriterFeatureType() {}

  @Override
  public JsonFgWriterFeatureType create() {
    return new JsonFgWriterFeatureType();
  }

  @Override
  public int getSortPriority() {
    return 24;
  }

  @Override
  public void onStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    collectionMap = getCollectionMap(context.encoding());
    isEnabled = collectionMap.values().stream().anyMatch(types -> !types.isEmpty());
    homogenous =
        collectionMap.values().stream().noneMatch(type -> type.contains(OPEN_TEMPLATE))
            && context.encoding().isFeatureCollection()
            && collectionMap.size() == 1;
    writeAtEnd = null;
    schemaMap = getSchemaMap(context.encoding());
    effectiveSchemas = new HashMap<>();

    if (isEnabled && homogenous) {
      writeType(context.encoding(), collectionMap.values().iterator().next());
      writeSingleSchema(context.encoding(), schemaMap.values().iterator().next());
    }

    // next chain for extensions
    next.accept(context);
  }

  @Override
  public void onFeatureStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (isEnabled && !homogenous) {
      String type = collectionMap.get(context.type());
      if (Objects.nonNull(type) && !type.isEmpty()) {
        if (type.contains(OPEN_TEMPLATE)) {
          writeAtEnd = type;
        } else {
          writeType(context.encoding(), type);
          if (schemaMap.containsKey(context.type())) {
            String schema = schemaMap.get(context.type());
            if (Objects.nonNull(schema)) {
              effectiveSchemas.put(type, schema);
            }
          }
        }
      }
    }

    // next chain for extensions
    next.accept(context);
  }

  @Override
  public void onValue(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (Objects.nonNull(writeAtEnd)
        && !writeAtEnd.isEmpty()
        && context.schema().filter(FeatureSchema::isValue).isPresent()
        && Objects.nonNull(context.value())) {

      FeatureSchema schema = context.schema().get();
      if (schema.isType()) {
        writeAtEnd = writeAtEnd.replace("{{type}}", context.value());
        if (schemaMap.containsKey(context.type()) && !effectiveSchemas.containsKey(writeAtEnd)) {
          effectiveSchemas.put(writeAtEnd, schemaMap.get(context.type()));
        }
      }
    }

    next.accept(context);
  }

  @Override
  public void onFeatureEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (Objects.nonNull(writeAtEnd) && !writeAtEnd.isEmpty()) {
      writeType(context.encoding(), writeAtEnd);
      writeAtEnd = null;
    }
    if (!context.encoding().isFeatureCollection()) {
      writeSchemas(context.encoding());
    }

    next.accept(context);
  }

  @Override
  public void onEnd(EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (context.encoding().isFeatureCollection()) {
      writeSchemas(context.encoding());
    }

    next.accept(context);
  }

  private Map<String, String> getCollectionMap(
      FeatureTransformationContextGeoJson transformationContext) {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    transformationContext
        .getFeatureSchemas()
        .forEach(
            (collectionId, schema) -> {
              if (writeJsonFgExtensions(transformationContext)) {
                transformationContext
                    .getApiData()
                    .getExtension(JsonFgConfiguration.class, collectionId)
                    .filter(ExtensionConfiguration::isEnabled)
                    .map(cfg -> cfg.getEffectiveFeatureType(schema))
                    .filter(Objects::nonNull)
                    .ifPresent(type -> builder.put(collectionId, type));
              }
            });
    return builder.build();
  }

  private void writeType(FeatureTransformationContextGeoJson transformationContext, String type)
      throws IOException {
    if (Objects.isNull(type) || type.isEmpty() || type.contains("{{type}}")) return;
    transformationContext.getJson().writeStringField(JSON_KEY, type);
  }

  private Map<String, String> getSchemaMap(
      FeatureTransformationContextGeoJson transformationContext) {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    transformationContext
        .getFeatureSchemas()
        .keySet()
        .forEach(
            collectionId -> {
              if (writeJsonFgExtensions(transformationContext)) {
                transformationContext
                    .getApiData()
                    .getExtension(SchemaConfiguration.class, collectionId)
                    .filter(ExtensionConfiguration::isEnabled)
                    .map(
                        ignore ->
                            String.format(
                                "%s/collections/%s/schema",
                                transformationContext.getServiceUrl(), collectionId))
                    .ifPresent(v -> builder.put(collectionId, v));
              }
            });
    return builder.build();
  }

  private void writeSingleSchema(
      FeatureTransformationContextGeoJson transformationContext, String schema) throws IOException {
    if (Objects.isNull(schema) || schema.isEmpty()) return;
    transformationContext.getJson().writeStringField(JSON_KEY_SCHEMA, schema);
  }

  private void writeSchemas(FeatureTransformationContextGeoJson transformationContext)
      throws IOException {
    if (effectiveSchemas.size() == 1) {
      transformationContext
          .getJson()
          .writeStringField(JSON_KEY_SCHEMA, effectiveSchemas.values().iterator().next());
    } else if (!effectiveSchemas.isEmpty()) {
      transformationContext.getJson().writeObjectField(JSON_KEY_SCHEMA, effectiveSchemas);
    }
  }
}
