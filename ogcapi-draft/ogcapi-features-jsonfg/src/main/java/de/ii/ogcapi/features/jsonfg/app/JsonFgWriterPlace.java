/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.jsonfg.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.FeatureTransformationContextGeoJson.GeometryState;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriterGeometryBase;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.json.domain.JsonFgGeometryType;
import de.ii.xtraplatform.geometries.domain.Geometry;
import de.ii.xtraplatform.geometries.domain.GeometryType;
import de.ii.xtraplatform.geometries.domain.PolyhedralSurface;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class JsonFgWriterPlace extends GeoJsonWriterGeometryBase {

  private static final Logger LOGGER = LoggerFactory.getLogger(JsonFgWriterPlace.class);

  public static String JSON_KEY = "place";

  @Inject
  JsonFgWriterPlace() {}

  @Override
  public JsonFgWriterPlace create() {
    return new JsonFgWriterPlace();
  }

  @Override
  public int getSortPriority() {
    return 32;
  }

  @Override
  protected String geomPropertyName(Optional<FeatureSchema> schema) {
    return JSON_KEY;
  }

  @Override
  protected boolean isEnabled(EncodingAwareContextGeoJson context) {
    return writeJsonFgExtensions;
  }

  @Override
  protected GeometryState geometryState() {
    return GeometryState.IN_PLACE;
  }

  @Override
  protected Set<FeatureSchema> getProperty(
      FeatureSchema schema,
      FeatureTransformationContext transformationContext,
      Geometry<?> geometry) {
    if (!writeJsonFgExtensions) {
      return Set.of();
    }

    if (geometry.getType().isSimpleFeature()
        && (geometry.getType() != GeometryType.POLYHEDRAL_SURFACE
            || !((PolyhedralSurface) geometry).isClosed())
        && (transformationContext.getTargetCrs().equals(OgcCrs.CRS84)
            || transformationContext.getTargetCrs().equals(OgcCrs.CRS84h))) {
      return Set.of();
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
    if (!writeJsonFgExtensions) {
      return Set.of();
    }

    if (geometry.getType().isSimpleFeature()
        && (geometry.getType() != GeometryType.POLYHEDRAL_SURFACE
            || !((PolyhedralSurface) geometry).isClosed())
        && (transformationContext.getTargetCrs().equals(OgcCrs.CRS84)
            || transformationContext.getTargetCrs().equals(OgcCrs.CRS84h))) {
      return Set.of();
    }

    return schema.getAllNestedProperties().stream()
        .filter(SchemaBase::isEmbeddedPrimaryGeometry)
        .collect(Collectors.toSet());
  }

  @Override
  protected String getGeometryType(EncodingAwareContextGeoJson context, Geometry<?> geometry) {
    JsonFgGeometryType jsonFgGeometryType = JsonFgGeometryType.forGeometry(geometry);
    if (!jsonFgGeometryType.isSupported()) {
      //noinspection DataFlowIssue
      if (LOGGER.isWarnEnabled()
          && !context
              .encoding()
              .getState()
              .getUnsupportedGeometries()
              .contains(geometry.getType())) {
        LOGGER.warn(
            "Ignoring one or more JSON-FG geometries in 'place' since an unsupported geometry type was provided: '{}'. Writing a null geometry.",
            geometry.getType());
        //noinspection DataFlowIssue
        context.encoding().getState().addUnsupportedGeometries(geometry.getType());
      }
      return null;
    }

    return jsonFgGeometryType.toString();
  }
}
