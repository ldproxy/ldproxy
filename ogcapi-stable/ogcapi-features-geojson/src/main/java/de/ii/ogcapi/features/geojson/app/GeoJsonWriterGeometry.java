/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.FeatureTransformationContextGeoJson.GeometryState;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriterGeometryBase;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.geometries.domain.Geometry;
import de.ii.xtraplatform.geometries.domain.GeometryType;
import de.ii.xtraplatform.geometries.domain.PolyhedralSurface;
import de.ii.xtraplatform.geometries.domain.transcode.json.GeoJsonGeometryType;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class GeoJsonWriterGeometry extends GeoJsonWriterGeometryBase {

  @Inject
  public GeoJsonWriterGeometry() {}

  @Override
  public GeoJsonWriterGeometry create() {
    return new GeoJsonWriterGeometry();
  }

  @Override
  public int getSortPriority() {
    return 35;
  }

  @Override
  protected String geomPropertyName(Optional<FeatureSchema> schema) {
    return "geometry";
  }

  @Override
  protected boolean isEnabled(EncodingAwareContextGeoJson context) {
    return true;
  }

  @Override
  protected boolean writeNull() {
    return true;
  }

  @Override
  protected GeometryState geometryState() {
    return GeometryState.IN_GEOMETRY;
  }

  @Override
  protected Set<FeatureSchema> getProperty(
      FeatureSchema schema,
      FeatureTransformationContext transformationContext,
      Geometry<?> geometry) {
    if (!geometry.getType().isSimpleFeature()) {
      return Set.of();
    }

    if (writeJsonFgExtensions
        && ((geometry.getType() == GeometryType.POLYHEDRAL_SURFACE
                && ((PolyhedralSurface) geometry).isClosed())
            || !(transformationContext.getTargetCrs().equals(OgcCrs.CRS84)
                || transformationContext.getTargetCrs().equals(OgcCrs.CRS84h)))) {
      return writeSecondaryGeometry
          ? schema.getAllNestedProperties().stream()
              .filter(SchemaBase::isSecondaryGeometry)
              .collect(Collectors.toSet())
          : Set.of();
    }

    return schema.getAllNestedProperties().stream()
        .filter(SchemaBase::isPrimaryGeometry)
        .collect(Collectors.toSet());
  }

  @Override
  protected Set<FeatureSchema> getEmbeddedFeatureProperty(
      FeatureSchema schema,
      FeatureTransformationContext transformationContext,
      Geometry<?> geometry) {
    if (!geometry.getType().isSimpleFeature()) {
      return Set.of();
    }

    if (writeJsonFgExtensions
        && ((geometry.getType() == GeometryType.POLYHEDRAL_SURFACE
                && ((PolyhedralSurface) geometry).isClosed())
            || !(transformationContext.getTargetCrs().equals(OgcCrs.CRS84)
                || transformationContext.getTargetCrs().equals(OgcCrs.CRS84h)))) {
      return schema.getAllNestedProperties().stream()
          .filter(SchemaBase::isEmbeddedSecondaryGeometry)
          .collect(Collectors.toSet());
    }

    Set<FeatureSchema> set =
        schema.getAllNestedProperties().stream()
            .filter(SchemaBase::isEmbeddedPrimaryGeometry)
            .collect(Collectors.toSet());
    if (set.isEmpty()) {
      set =
          schema.getAllNestedProperties().stream()
              .filter(SchemaBase::isEmbeddedSecondaryGeometry)
              .collect(Collectors.toSet());
    }
    return set;
  }

  @Override
  protected String getGeometryType(EncodingAwareContextGeoJson context, Geometry<?> geometry) {
    GeoJsonGeometryType geoJsonGeometryType = GeoJsonGeometryType.forGeometry(geometry);
    if (!geoJsonGeometryType.isSupported()) {
      if (LOGGER.isWarnEnabled()
          && !context
              .encoding()
              .getState()
              .getUnsupportedGeometries()
              .contains(geometry.getType())) {
        LOGGER.warn(
            "Ignoring one or more GeoJSON geometries since an unsupported geometry type was provided: '{}'. Writing a null geometry.",
            geometry.getType());
        context.encoding().getState().addUnsupportedGeometries(geometry.getType());
      }
      return null;
    }
    return geoJsonGeometryType.toString();
  }
}
