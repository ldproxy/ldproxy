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
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class JsonFgWriterCrs implements GeoJsonWriter {

  public static String JSON_KEY = "coordRefSys";

  Map<String, Boolean> collectionMap;
  boolean isEnabled;
  boolean useCuries;

  @Inject
  JsonFgWriterCrs() {}

  @Override
  public JsonFgWriterCrs create() {
    return new JsonFgWriterCrs();
  }

  @Override
  public int getSortPriority() {
    return 130;
  }

  @Override
  public void onStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    collectionMap = getCollectionMap(context.encoding());
    isEnabled = collectionMap.values().stream().anyMatch(enabled -> enabled);
    useCuries =
        context
            .encoding()
            .getApiData()
            .getExtension(JsonFgConfiguration.class, context.encoding().getCollectionId())
            .map(cfg -> Objects.equals(cfg.getUseCuries(), Boolean.TRUE))
            .orElse(false);

    if (isEnabled && context.encoding().isFeatureCollection()) {
      writeCrs(context.encoding().getJson(), context.encoding().getTargetCrs());
    }

    // next chain for extensions
    next.accept(context);
  }

  @Override
  public void onFeatureStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (isEnabled
        && !context.encoding().isFeatureCollection()
        && Objects.requireNonNullElse(collectionMap.get(context.type()), false)) {
      writeCrs(context.encoding().getJson(), context.encoding().getTargetCrs());
    }

    // next chain for extensions
    next.accept(context);
  }

  private void writeCrs(JsonGenerator json, EpsgCrs crs) throws IOException {
    if (crs.getVerticalCode().isPresent()) {
      json.writeArrayFieldStart(JSON_KEY);
      List<String> values = useCuries ? crs.toSafeCuries() : crs.toUriStrings();
      for (String value : values) {
        json.writeString(value);
      }
      json.writeEndArray();
    } else {
      json.writeStringField(JSON_KEY, useCuries ? crs.toSafeCurie() : crs.toUriString());
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
                transformationContext
                    .getApiData()
                    .getExtension(JsonFgConfiguration.class, collectionId)
                    .ifPresentOrElse(
                        cfg -> {
                          boolean enabled =
                              cfg.isEnabled()
                                  && Objects.requireNonNullElse(cfg.getCoordRefSys(), false)
                                  && (cfg.getIncludeInGeoJson()
                                          .contains(JsonFgConfiguration.OPTION.coordRefSys)
                                      || transformationContext
                                          .getMediaType()
                                          .equals(FeaturesFormatJsonFg.MEDIA_TYPE)
                                      || transformationContext
                                          .getMediaType()
                                          .equals(FeaturesFormatJsonFgCompatibility.MEDIA_TYPE));
                          builder.put(collectionId, enabled);
                        },
                        () -> builder.put(collectionId, false)));
    return builder.build();
  }
}
