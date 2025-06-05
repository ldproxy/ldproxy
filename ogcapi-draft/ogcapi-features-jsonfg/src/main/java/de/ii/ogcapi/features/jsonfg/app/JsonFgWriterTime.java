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
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriter;
import de.ii.ogcapi.features.jsonfg.domain.JsonFgConfiguration;
import de.ii.xtraplatform.features.domain.FeatureSchema;
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
  String currentIntervalStart;
  String currentIntervalEnd;
  String currentInstant;

  @Inject
  JsonFgWriterTime() {}

  @Override
  public JsonFgWriterTime create() {
    return new JsonFgWriterTime();
  }

  @Override
  public int getSortPriority() {
    return 100;
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
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next) {
    isEnabled = Objects.requireNonNullElse(collectionMap.get(context.type()), false);
    if (isEnabled) {
      currentIntervalStart = null;
      currentIntervalEnd = null;
      currentInstant = null;
    }

    // next chain for extensions
    next.accept(context);
  }

  @Override
  public void onFeatureEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    next.accept(context);

    if (isEnabled) {
      JsonGenerator json = context.encoding().getJson();
      if (Objects.nonNull(currentInstant)) {
        json.writeFieldName(JSON_KEY);
        json.writeStartObject();
        json.writeStringField("instant", currentInstant);
        json.writeEndObject();
      } else if (Objects.nonNull(currentIntervalStart) || Objects.nonNull(currentIntervalEnd)) {
        json.writeFieldName(JSON_KEY);
        json.writeStartObject();
        json.writeArrayFieldStart("interval");
        if (Objects.nonNull(currentIntervalStart)) json.writeString(currentIntervalStart);
        else json.writeNull();
        if (Objects.nonNull(currentIntervalEnd)) json.writeString(currentIntervalEnd);
        else json.writeNull();
        json.writeEndArray();
        json.writeEndObject();
      }
    }
  }

  @Override
  public void onValue(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (isEnabled
        && context.schema().filter(FeatureSchema::isValue).isPresent()
        && Objects.nonNull(context.value())) {
      final FeatureSchema schema = context.schema().get();
      if (schema.isPrimaryInstant()) currentInstant = context.value();
      else if (schema.isPrimaryIntervalStart()) currentIntervalStart = context.value();
      else if (schema.isPrimaryIntervalEnd()) currentIntervalEnd = context.value();
    }

    next.accept(context);
  }

  private Map<String, Boolean> getCollectionMap(
      FeatureTransformationContextGeoJson transformationContext) {
    ImmutableMap.Builder<String, Boolean> builder = ImmutableMap.builder();
    transformationContext
        .getFeatureSchemas()
        .keySet()
        .forEach(
            collectionId ->
                transformationContext
                    .getApiData()
                    .getExtension(JsonFgConfiguration.class, collectionId)
                    .ifPresentOrElse(
                        cfg -> {
                          boolean enabled = cfg.isEnabled();
                          /* FIXME
                                 && (cfg.getIncludeInGeoJson()
                                         .contains(JsonFgConfiguration.OPTION.time)
                                     || transformationContext
                                         .getMediaType()
                                         .equals(FeaturesFormatJsonFg.MEDIA_TYPE)
                                     || transformationContext
                                         .getMediaType()
                                         .equals(FeaturesFormatJsonFgCompatibility.MEDIA_TYPE));
                          */
                          builder.put(collectionId, enabled);
                        },
                        () -> builder.put(collectionId, false)));
    return builder.build();
  }
}
