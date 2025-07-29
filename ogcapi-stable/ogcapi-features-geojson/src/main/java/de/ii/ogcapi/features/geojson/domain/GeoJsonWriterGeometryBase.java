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
import de.ii.ogcapi.features.geojson.domain.FeatureTransformationContextGeoJson.GeometryState;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.json.domain.GeometryEncoderJson;
import de.ii.xtraplatform.geometries.domain.Geometry;
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
  public void onGeometry(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (isEnabled(context)) {
      Geometry<?> geometry = context.geometry();
      boolean isGeom = isGeomProperty(context.schema(), context.encoding(), geometry);
      if (isGeom) {
        if (geometry != null) {
          String type = getGeometryType(context, geometry);
          if (type == null || type.isEmpty()) {
            writeNullIfNecessary(context);
          } else {
            context.encoding().pauseBuffering();
            context.encoding().getJson().writeFieldName(geomPropertyName(context.schema()));
            geometry.accept(
                new GeometryEncoderJson(
                    context.encoding().getJson(),
                    !writeJsonFgExtensions,
                    context.encoding().getGeometryPrecision()));
            context.encoding().getJson().flush();
            context.encoding().continueBuffering();
          }
        } else {
          writeNullIfNecessary(context);
        }
        if (writeNull()) {
          context.encoding().getFeatureState().get().hasGeometry = true;
        }
      }
    }

    next.accept(context);
  }

  @Override
  public void onFeatureEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (isEnabled(context)) {
      if (!context.encoding().getFeatureState().get().hasGeometry) {
        writeNullIfNecessary(context);
      }
    }

    next.accept(context);
  }

  private boolean isGeomProperty(
      Optional<FeatureSchema> property,
      FeatureTransformationContextGeoJson transformationContext,
      Geometry<?> geometry) {
    return transformationContext.inEmbeddedFeature()
        ? property
            .map(
                p1 ->
                    getEmbeddedFeatureProperty(
                            transformationContext.getFeatureState().get().schema,
                            transformationContext,
                            geometry)
                        .stream()
                        .anyMatch(p1::equals))
            .orElse(false)
        : property
            .map(
                p1 ->
                    getProperty(
                            transformationContext.getFeatureState().get().schema,
                            transformationContext,
                            geometry)
                        .stream()
                        .anyMatch(p1::equals))
            .orElse(false);
  }

  protected abstract boolean isEnabled(EncodingAwareContextGeoJson context);

  protected boolean writeNull() {
    return false;
  }

  protected abstract GeometryState geometryState();

  protected abstract String geomPropertyName(Optional<FeatureSchema> schema);

  protected abstract Set<FeatureSchema> getProperty(
      FeatureSchema schema,
      FeatureTransformationContext transformationContext,
      Geometry<?> geometry);

  protected abstract Set<FeatureSchema> getEmbeddedFeatureProperty(
      FeatureSchema schema,
      FeatureTransformationContext transformationContext,
      Geometry<?> geometry);

  protected abstract String getGeometryType(
      EncodingAwareContextGeoJson context, Geometry<?> geometry);

  private void writeNullIfNecessary(EncodingAwareContextGeoJson context) throws IOException {
    if (writeNull()) {
      context.encoding().pauseBuffering();
      context.encoding().getJson().writeFieldName(geomPropertyName(context.schema()));
      context.encoding().getJson().writeNull();
      context.encoding().getJson().flush();
      context.encoding().continueBuffering();
    }
  }
}
