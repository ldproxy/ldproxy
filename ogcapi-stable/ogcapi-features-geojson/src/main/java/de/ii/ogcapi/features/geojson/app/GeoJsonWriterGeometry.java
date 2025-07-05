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
import de.ii.xtraplatform.features.json.domain.GeoJsonGeometryType;
import de.ii.xtraplatform.geometries.domain.SimpleFeatureGeometry;
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
    return 32;
  }

  @Override
  protected String geomPropertyName() {
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
      FeatureSchema schema, FeatureTransformationContext transformationContext) {
    if (writeJsonFgExtensions
        && !(transformationContext.getTargetCrs().equals(OgcCrs.CRS84)
            || transformationContext.getTargetCrs().equals(OgcCrs.CRS84h))) {
      return writeSecondaryGeometry
          ? schema.getAllNestedProperties().stream()
              .filter(SchemaBase::isSecondaryGeometry)
              .filter(SchemaBase::isSimpleFeatureGeometry)
              .collect(Collectors.toSet())
          : Set.of();
    }

    return schema.getAllNestedProperties().stream()
        .filter(SchemaBase::isPrimaryGeometry)
        .filter(SchemaBase::isSimpleFeatureGeometry)
        .collect(Collectors.toSet());
  }

  @Override
  protected Set<FeatureSchema> getEmbeddedFeatureProperty(
      FeatureSchema schema, FeatureTransformationContext transformationContext) {
    if (writeJsonFgExtensions
        && !(transformationContext.getTargetCrs().equals(OgcCrs.CRS84)
            || transformationContext.getTargetCrs().equals(OgcCrs.CRS84h))) {
      return schema.getAllNestedProperties().stream()
          .filter(SchemaBase::isEmbeddedSecondaryGeometry)
          .filter(SchemaBase::isSimpleFeatureGeometry)
          .collect(Collectors.toSet());
    }

    Set<FeatureSchema> set =
        schema.getAllNestedProperties().stream()
            .filter(SchemaBase::isEmbeddedPrimaryGeometry)
            .filter(SchemaBase::isSimpleFeatureGeometry)
            .collect(Collectors.toSet());
    if (set.isEmpty()) {
      set =
          schema.getAllNestedProperties().stream()
              .filter(SchemaBase::isEmbeddedSecondaryGeometry)
              .filter(SchemaBase::isSimpleFeatureGeometry)
              .collect(Collectors.toSet());
    }
    return set;
  }

  @Override
  protected String getGeometryType(EncodingAwareContextGeoJson context) {
    SimpleFeatureGeometry sfGeometryType = context.geometryType().get();
    GeoJsonGeometryType geoJsonGeometryType =
        GeoJsonGeometryType.forSimpleFeatureType(sfGeometryType);
    if (!geoJsonGeometryType.isSupported()) {
      if (LOGGER.isWarnEnabled()
          && !context.encoding().getState().getUnsupportedGeometries().contains(sfGeometryType)) {
        LOGGER.warn(
            "Ignoring one or more GeoJSON geometries since an unsupported geometry type was provided: '{}'. Writing a null geometry.",
            sfGeometryType);
        context.encoding().getState().addUnsupportedGeometries(sfGeometryType);
      }
      return null;
    }
    return geoJsonGeometryType.toString();
  }
}
