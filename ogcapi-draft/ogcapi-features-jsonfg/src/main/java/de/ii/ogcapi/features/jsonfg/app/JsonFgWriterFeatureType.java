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
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.FeatureTransformationContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriter;
import de.ii.ogcapi.features.jsonfg.domain.JsonFgConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import java.io.IOException;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class JsonFgWriterFeatureType implements GeoJsonWriter {

  static final String OPEN_TEMPLATE = "{{";
  public static String JSON_KEY = "featureType";
  public static String JSON_KEY_SCHEMA = "featureSchema";

  Map<String, String> collectionMap;
  Map<String, String> typeMap;
  boolean isEnabled;
  boolean homogenous;
  Map<String, String> schemaMap;
  Map<String, String> effectiveSchemas;
  private boolean isFeatureCollection;

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
    typeMap = getTypeMap(context.encoding(), collectionMap);
    isEnabled = !collectionMap.isEmpty();
    isFeatureCollection = context.encoding().isFeatureCollection();
    homogenous =
        collectionMap.values().stream().noneMatch(type -> type.contains(OPEN_TEMPLATE))
            && isFeatureCollection
            && collectionMap.size() == 1;
    schemaMap = getSchemaMap(context.encoding());
    effectiveSchemas = new HashMap<>();

    if (isEnabled && homogenous) {
      writeType(context, collectionMap.values().iterator().next());
      writeSingleSchema(context, schemaMap.values().iterator().next());
      isEnabled = false; // disable further processing
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
      if (Objects.nonNull(type)) {
        if (type.contains(OPEN_TEMPLATE)) {
          context.encoding().getFeatureState().get().typeTemplate = type;
        } else {
          writeType(context, type);
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
    if (isEnabled
        && Objects.nonNull(context.encoding().getFeatureState().get().typeTemplate)
        && context.schema().filter(FeatureSchema::isValue).isPresent()
        && Objects.nonNull(context.value())
        && !context.value().isEmpty()) {

      FeatureSchema schema = context.schema().get();
      if (schema.isType()) {
        String type =
            context
                .encoding()
                .getFeatureState()
                .get()
                .typeTemplate
                .replace("{{type}}", context.value());
        context.encoding().pauseBuffering();
        writeType(context, type);
        context.encoding().continueBuffering();
        context.encoding().getFeatureState().get().typeTemplate = null;

        if (schemaMap.containsKey(context.type())) {
          effectiveSchemas.putIfAbsent(type, schemaMap.get(context.type()));
        }
      }
    }

    next.accept(context);
  }

  @Override
  public void onFeatureEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (isEnabled && !isFeatureCollection) {
      context.encoding().pauseBuffering();
      writeSchemas(context);
      context.encoding().continueBuffering();
      effectiveSchemas = new HashMap<>();
    }

    next.accept(context);
  }

  @Override
  public void onEnd(EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (isEnabled && isFeatureCollection) {
      writeSchemas(context);
    }

    next.accept(context);
  }

  @Override
  public void onObjectStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (isEnabled) {
      if (context.schema().map(SchemaBase::isEmbeddedFeature).orElse(false)) {
        String type = typeMap.get(context.schema().get().getName());
        if (Objects.nonNull(type)) {
          if (type.contains(OPEN_TEMPLATE)) {
            context.encoding().getFeatureState().get().typeTemplate = type;
          } else {
            context.encoding().pauseBuffering();
            writeType(context, type);
            context.encoding().continueBuffering();
            if (schemaMap.containsKey(context.type())) {
              String schema = schemaMap.get(context.type());
              if (Objects.nonNull(schema)) {
                effectiveSchemas.put(type, schema);
              }
            }
          }
        }
      }
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
                    .filter(type -> !type.isEmpty())
                    .ifPresent(type -> builder.put(collectionId, type));
              }
            });
    return builder.build();
  }

  private Map<String, String> getTypeMap(
      FeatureTransformationContextGeoJson transformationContext,
      Map<String, String> collectionMap) {
    return transformationContext.getApiData().getCollections().entrySet().stream()
        .map(
            entry -> {
              String featureType =
                  entry
                      .getValue()
                      .getExtension(FeaturesCoreConfiguration.class)
                      .map(cfg -> cfg.getFeatureType().orElse(entry.getKey()))
                      .orElse(entry.getKey());
              String typeValue = collectionMap.get(entry.getKey());
              return Objects.nonNull(typeValue)
                  ? new SimpleImmutableEntry<>(featureType, typeValue)
                  : null;
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  private void writeType(EncodingAwareContextGeoJson context, String type) throws IOException {
    context.encoding().getJson().writeStringField(JSON_KEY, type);
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

  private void writeSingleSchema(EncodingAwareContextGeoJson context, String schema)
      throws IOException {
    context.encoding().getJson().writeStringField(JSON_KEY_SCHEMA, schema);
  }

  private void writeSchemas(EncodingAwareContextGeoJson context) throws IOException {
    if (effectiveSchemas.size() == 1) {
      context
          .encoding()
          .getJson()
          .writeStringField(JSON_KEY_SCHEMA, effectiveSchemas.values().iterator().next());
    } else if (!effectiveSchemas.isEmpty()) {
      context.encoding().getJson().writeObjectField(JSON_KEY_SCHEMA, effectiveSchemas);
    }
  }
}
