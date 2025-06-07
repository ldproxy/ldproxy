/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.jsonfg.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.FeatureTransformationContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriter;
import de.ii.ogcapi.features.jsonfg.domain.JsonFgGeometryType;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.domain.SchemaConstraints;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class JsonFgWriterPlace implements GeoJsonWriter {

  public static String JSON_KEY = "place";

  @Inject
  JsonFgWriterPlace() {}

  @Override
  public JsonFgWriterPlace create() {
    return new JsonFgWriterPlace();
  }

  Map<String, Boolean> collectionMap;
  boolean isEnabled;
  private boolean geometryOpen;
  private boolean additionalArray;
  private boolean hasPlaceGeometry;
  private boolean hasSecondaryGeometry;
  private boolean suppressPlace;
  private TokenBuffer json;

  @Override
  public int getSortPriority() {
    return 140;
  }

  private void reset(EncodingAwareContextGeoJson context) {
    this.geometryOpen = false;
    this.hasPlaceGeometry = false;
    this.additionalArray = false;
    this.json = new TokenBuffer(new ObjectMapper(), false);
    if (context.encoding().getPrettify()) {
      json.useDefaultPrettyPrinter();
    }
  }

  @Override
  public void onStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    collectionMap = getCollectionMap(context.encoding());

    next.accept(context);
  }

  @Override
  public void onFeatureStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    isEnabled = Objects.requireNonNullElse(collectionMap.get(context.type()), false);

    hasSecondaryGeometry =
        context.schema().map(schema -> schema.getSecondaryGeometry().isPresent()).orElse(false);
    boolean primaryGeometryIsSimpleFeature =
        context
            .schema()
            .flatMap(schema -> schema.getPrimaryGeometry().map(SchemaBase::isSimpleFeatureGeometry))
            .orElse(false);

    // set 'place' to null, if the geometry is in WGS84 (in this case it is in "geometry")
    // and a simple feature geometry type unless a separate property is used for place
    suppressPlace =
        !hasSecondaryGeometry
            && primaryGeometryIsSimpleFeature
            && context.encoding().getTargetCrs().equals(context.encoding().getDefaultCrs());

    if (isEnabled) reset(context);

    next.accept(context);
  }

  @Override
  public void onObjectStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (isEnabled
        && !suppressPlace
        && context.schema().filter(SchemaBase::isSpatial).isPresent()
        && context.geometryType().isPresent()
        && isPlaceGeometry(context.schema().get())) {
      String type =
          JsonFgGeometryType.forSimpleFeatureType(
                  context.geometryType().get(),
                  context
                      .schema()
                      .flatMap(s -> s.getConstraints().flatMap(SchemaConstraints::getComposite))
                      .orElse(false),
                  context
                      .schema()
                      .flatMap(s -> s.getConstraints().flatMap(SchemaConstraints::getClosed))
                      .orElse(false))
              .toString();

      json.writeFieldName(JSON_KEY);
      json.writeStartObject();
      json.writeStringField("type", type);
      json.writeFieldName("coordinates");

      if (type.equals("Polyhedron")) {
        json.writeStartArray();
        additionalArray = true;
      }

      geometryOpen = true;
      hasPlaceGeometry = true;
    }

    next.accept(context);
  }

  @Override
  public void onArrayStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {

    if (geometryOpen) {
      json.writeStartArray();
    }

    next.accept(context);
  }

  @Override
  public void onArrayEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {

    if (geometryOpen) {
      json.writeEndArray();
    }

    next.accept(context);
  }

  @Override
  public void onObjectEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (context.schema().filter(SchemaBase::isSpatial).isPresent() && geometryOpen) {

      this.geometryOpen = false;

      if (additionalArray) {
        additionalArray = false;
        json.writeEndArray();
      }

      // close geometry object
      json.writeEndObject();
    }

    next.accept(context);
  }

  @Override
  public void onValue(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {

    if (geometryOpen) {
      json.writeRawValue(context.value());
    }

    next.accept(context);
  }

  @Override
  public void onFeatureEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {

    if (isEnabled) {
      json.serialize(context.encoding().getJson());
      json.flush();
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
                builder.put(collectionId, writeJsonFgExtensions(transformationContext)));
    return builder.build();
  }

  private boolean hasSecondaryGeometry(FeatureSchema schema) {
    return schema.getProperties().stream()
        .filter(SchemaBase::isSecondaryGeometry)
        .findFirst()
        .map(property -> true)
        .or(() -> schema.getProperties().stream().map(this::hasSecondaryGeometry).findFirst())
        .orElse(false);
  }

  private boolean isPlaceGeometry(FeatureSchema property) {
    return (hasSecondaryGeometry && property.isSecondaryGeometry())
        || (!hasSecondaryGeometry && property.isPrimaryGeometry());
  }
}
