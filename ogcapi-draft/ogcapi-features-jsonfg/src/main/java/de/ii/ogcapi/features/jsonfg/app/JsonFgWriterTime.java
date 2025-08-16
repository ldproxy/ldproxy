/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.jsonfg.app;

import com.fasterxml.jackson.core.JsonGenerator;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.FeatureTransformationContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.FeatureTransformationContextGeoJson.FeatureState;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriter;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class JsonFgWriterTime implements GeoJsonWriter {

  public static String JSON_KEY = "time";

  Map<String, Boolean> collectionMap;
  boolean isEnabled;

  @Inject
  JsonFgWriterTime() {}

  @Override
  public JsonFgWriterTime create() {
    return new JsonFgWriterTime();
  }

  @Override
  public int getSortPriority() {
    return 40;
  }

  @Override
  public void onStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    collectionMap = getCollectionMap(context.encoding());
    isEnabled = collectionMap.values().stream().anyMatch(enabled -> enabled);

    // next chain for extensions
    next.accept(context);
  }

  @Override
  public void onFeatureStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    isEnabled = Objects.requireNonNullElse(collectionMap.get(context.type()), false);

    // next chain for extensions
    next.accept(context);
  }

  @Override
  public void onObjectEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (isEnabled) {
      if (context.schema().map(SchemaBase::isEmbeddedFeature).orElse(false)) {
        FeatureState featureState = context.encoding().getFeatureState().get();
        if ((!featureState.instantProperty.isEmpty()
                || !featureState.intervalStartProperty.isEmpty()
                || !featureState.intervalEndProperty.isEmpty())
            && !featureState.hasTime) {
          context.encoding().pauseBuffering();
          writeTime(
              context,
              featureState.currentInstant,
              featureState.currentIntervalStart,
              featureState.currentIntervalEnd);
          context.encoding().continueBuffering();
        }
      }
    }

    next.accept(context);
  }

  @Override
  public void onFeatureEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (isEnabled) {
      FeatureState featureState = context.encoding().getFeatureState().get();
      if ((!featureState.instantProperty.isEmpty()
              || !featureState.intervalStartProperty.isEmpty()
              || !featureState.intervalEndProperty.isEmpty())
          && !featureState.hasTime) {
        context.encoding().pauseBuffering();
        writeTime(
            context,
            featureState.currentInstant,
            featureState.currentIntervalStart,
            featureState.currentIntervalEnd);
        context.encoding().continueBuffering();
      }
    }

    next.accept(context);
  }

  @Override
  public void onValue(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    final FeatureSchema schema = context.schema().get();
    final FeatureState featureState = context.encoding().getFeatureState().get();
    if (isEnabled
        && !featureState.hasTime
        && (!featureState.instantProperty.isEmpty()
            || !featureState.intervalStartProperty.isEmpty()
            || !featureState.intervalEndProperty.isEmpty())
        && schema.isTemporal()
        && Objects.nonNull(context.value())) {
      featureState.setTimeValue(schema, context.value());
      if (featureState.timeIsComplete()) {
        context.encoding().pauseBuffering();
        writeTime(
            context,
            featureState.currentInstant,
            featureState.currentIntervalStart,
            featureState.currentIntervalEnd);
        featureState.hasTime = true;
        context.encoding().continueBuffering();
      }
    }

    next.accept(context);
  }

  private void writeTime(
      EncodingAwareContextGeoJson context,
      String currentInstant,
      String currentIntervalStart,
      String currentIntervalEnd)
      throws IOException {
    JsonGenerator json = context.encoding().getJson();
    if (Objects.nonNull(currentInstant)
        || Objects.nonNull(currentIntervalStart)
        || Objects.nonNull(currentIntervalEnd)) {
      json.writeFieldName(JSON_KEY);
      json.writeStartObject();
      if (Objects.nonNull(currentInstant)) {
        json.writeStringField("instant", currentInstant);
      }
      if (Objects.nonNull(currentIntervalStart) || Objects.nonNull(currentIntervalEnd)) {
        json.writeArrayFieldStart("interval");
        if (Objects.nonNull(currentIntervalStart)) {
          json.writeString(currentIntervalStart);
        } else {
          json.writeString("..");
        }
        if (Objects.nonNull(currentIntervalEnd)) {
          json.writeString(currentIntervalEnd);
        } else {
          json.writeString("..");
        }
        json.writeEndArray();
      }
      json.writeEndObject();
    }
  }

  private Map<String, Boolean> getCollectionMap(
      FeatureTransformationContextGeoJson transformationContext) {
    ImmutableMap.Builder<String, Boolean> builder = ImmutableMap.builder();
    transformationContext
        .getFeatureSchemas()
        .keySet()
        .forEach(
            collectionId ->
                builder.put(collectionId, writeJsonFgExtensions(transformationContext)));
    return builder.build();
  }
}
