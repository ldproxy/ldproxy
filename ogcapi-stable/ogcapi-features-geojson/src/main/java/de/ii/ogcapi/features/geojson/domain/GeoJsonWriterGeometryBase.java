/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.ogcapi.features.geojson.domain.FeatureTransformationContextGeoJson.FeatureState;
import de.ii.ogcapi.features.geojson.domain.FeatureTransformationContextGeoJson.GeometryState;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

@AutoMultiBind
public abstract class GeoJsonWriterGeometryBase implements GeoJsonWriter {

  protected boolean writeJsonFgExtensions;
  protected boolean writeSecondaryGeometry;

  @Override
  public abstract GeoJsonWriterGeometryBase create();

  @Override
  public void onStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    writeJsonFgExtensions = writeJsonFgExtensions(context.encoding());
    writeSecondaryGeometry = writeSecondaryGeometry(context.encoding());

    next.accept(context);
  }

  @Override
  public void onObjectStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (isEnabled(context)) {
      if (context.schema().map(SchemaBase::isSpatial).orElse(false)
          && context.geometryType().isPresent()) {
        boolean isGeom = isGeomProperty(context.schema(), context.encoding());
        if (isGeom) {
          FeatureState featureState = context.encoding().getBuffer().get();
          featureState.hasGeometry = true;
          context.encoding().pauseBuffering();

          context.encoding().getJson().writeFieldName(geomPropertyName());

          String type = getGeometryType(context);
          if (type == null || type.isEmpty()) {
            context.encoding().getJson().writeNull();
            context.encoding().getJson().flush();
            context.encoding().continueBuffering();
          } else {
            featureState.geometryState = geometryState();

            context.encoding().getJson().writeStartObject();
            context.encoding().getJson().writeStringField("type", type);
            context.encoding().getJson().writeFieldName("coordinates");

            if (type.equals("Polyhedron")) {
              context.encoding().getJson().writeStartArray();
              featureState.isPolyhedron = true;
            }
          }
        }
      }
    }

    next.accept(context);
  }

  @Override
  public void onArrayStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (isEnabled(context)
        && context.encoding().getBuffer().get().geometryState == geometryState()) {
      context.encoding().getJson().writeStartArray();
    }

    next.accept(context);
  }

  @Override
  public void onArrayEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (isEnabled(context)
        && context.encoding().getBuffer().get().geometryState == geometryState()) {
      context.encoding().getJson().writeEndArray();
    }

    next.accept(context);
  }

  @Override
  public void onObjectEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (isEnabled(context)) {
      FeatureState featureState = context.encoding().getBuffer().get();
      if (featureState.geometryState == geometryState()
          && context.schema().map(SchemaBase::isSpatial).orElse(false)) {
        featureState.geometryState = GeometryState.NOT_IN_GEOMETRY;

        if (featureState.isPolyhedron) {
          featureState.isPolyhedron = false;
          context.encoding().getJson().writeEndArray();
        }

        // close geometry object
        context.encoding().getJson().writeEndObject();
        context.encoding().getJson().flush();

        context.encoding().continueBuffering();
      } else if (context.encoding().inEmbeddedFeature()
          && context.schema().map(SchemaBase::isEmbeddedFeature).orElse(false)) {

        context.encoding().pauseBuffering();

        if (!featureState.hasGeometry && writeNull()) {
          // write null geometry if none was written for this embedded feature
          context.encoding().getJson().writeFieldName(geomPropertyName());
          context.encoding().getJson().writeNull();
        }
        context.encoding().continueBuffering();
      }
    }

    next.accept(context);
  }

  @Override
  public void onValue(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (isEnabled(context)
        && context.encoding().getBuffer().get().geometryState == geometryState()) {
      context.encoding().getJson().writeRawValue(context.value());
    }

    next.accept(context);
  }

  @Override
  public void onFeatureEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (isEnabled(context)) {
      context.encoding().pauseBuffering();

      if (!context.encoding().getBuffer().get().hasGeometry && writeNull()) {
        // write null geometry if none was written for this feature
        context.encoding().getJson().writeFieldName(geomPropertyName());
        context.encoding().getJson().writeNull();
      }
      context.encoding().continueBuffering();

      context.encoding().getJson().flush();
    }

    next.accept(context);
  }

  private boolean isGeomProperty(
      Optional<FeatureSchema> property, FeatureTransformationContextGeoJson transformationContext) {
    return transformationContext.inEmbeddedFeature()
        ? property
            .map(
                p1 ->
                    getEmbeddedFeatureProperty(
                            transformationContext.getBuffer().get().schema, transformationContext)
                        .stream()
                        .anyMatch(p1::equals))
            .orElse(false)
        : property
            .map(
                p1 ->
                    getProperty(
                            transformationContext.getBuffer().get().schema, transformationContext)
                        .stream()
                        .anyMatch(p1::equals))
            .orElse(false);
  }

  protected abstract boolean isEnabled(EncodingAwareContextGeoJson context);

  protected boolean writeNull() {
    return false;
  }

  protected abstract GeometryState geometryState();

  protected abstract String geomPropertyName();

  protected abstract Set<FeatureSchema> getProperty(
      FeatureSchema schema, FeatureTransformationContext transformationContext);

  protected abstract Set<FeatureSchema> getEmbeddedFeatureProperty(
      FeatureSchema schema, FeatureTransformationContext transformationContext);

  protected abstract String getGeometryType(EncodingAwareContextGeoJson context);
}
