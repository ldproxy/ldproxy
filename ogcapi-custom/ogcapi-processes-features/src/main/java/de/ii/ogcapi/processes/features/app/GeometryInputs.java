/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.features.app;

import de.ii.xtraplatform.crs.domain.CoordinateTuple;
import de.ii.xtraplatform.crs.domain.CrsTransformer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/**
 * Geometries of the process inputs, given in CRS84, and the geometric values computed in the native
 * coordinate reference system of the features, which is projected.
 */
final class GeometryInputs {

  static final String INPUT_CLIP = "clip";
  static final String INPUT_POINT = "point";
  static final String INPUT_DISTANCE = "distance";
  static final String INPUT_GEOMETRY = "geometry";

  // the circle around a point is a polygon with 4 * 32 segments, its area is 0.04 percent smaller
  // than the area of the circle
  private static final int QUADRANT_SEGMENTS = 32;

  private static final GeometryFactory FACTORY = new GeometryFactory();

  private GeometryInputs() {}

  /**
   * The input {@code clip}: a point with a distance in meters, or a GeoJSON polygon or multi
   * polygon.
   *
   * @param toNative the transformation from CRS84 to the native coordinate reference system
   * @throws IllegalArgumentException if the input is invalid
   */
  static Geometry clip(Object input, CrsTransformer toNative) {
    if (!(input instanceof Map<?, ?> clip)) {
      throw new IllegalArgumentException("The input 'clip' must be an object.");
    }
    if (clip.containsKey(INPUT_POINT)) {
      Point center = point(clip.get(INPUT_POINT), toNative, "clip.point");
      return center.buffer(distance(clip.get(INPUT_DISTANCE), "clip.distance"), QUADRANT_SEGMENTS);
    }
    if (clip.get(INPUT_GEOMETRY) instanceof Map<?, ?> geometry) {
      return transform(polygonal(geometry), toNative);
    }
    throw new IllegalArgumentException(
        "The input 'clip' must have the members 'point' and 'distance' or the member 'geometry'.");
  }

  /**
   * A position {@code [lon, lat]} in CRS84, in the native coordinate reference system.
   *
   * @throws IllegalArgumentException if the input is invalid
   */
  static Point point(Object input, CrsTransformer toNative, String name) {
    if (input instanceof List<?> list
        && list.size() == 2
        && list.get(0) instanceof Number lon
        && list.get(1) instanceof Number lat) {
      if (Math.abs(lon.doubleValue()) > 180 || Math.abs(lat.doubleValue()) > 90) {
        throw new IllegalArgumentException(
            String.format("The input '%s' must be a position [lon, lat] in CRS84.", name));
      }
      CoordinateTuple point = toNative.transform(lon.doubleValue(), lat.doubleValue());
      return FACTORY.createPoint(new Coordinate(point.getX(), point.getY()));
    }
    throw new IllegalArgumentException(
        String.format("The input '%s' must be a position [lon, lat] in CRS84.", name));
  }

  /**
   * A distance in meters, which must be positive.
   *
   * @throws IllegalArgumentException if the input is invalid
   */
  static double distance(Object input, String name) {
    if (input instanceof Number number && number.doubleValue() > 0) {
      return number.doubleValue();
    }
    throw new IllegalArgumentException(
        String.format("The input '%s' must be a positive number of meters.", name));
  }

  /**
   * The values of the virtual properties of a feature: the area and the length of its geometry, or
   * of the part of it within the clip area.
   */
  static Map<String, Double> virtualValues(Geometry geometry, Optional<Geometry> clip) {
    Geometry part =
        clip.isPresent()
            ? OverlayNGRobust.overlay(geometry, clip.get(), OverlayNG.INTERSECTION)
            : geometry;
    Map<String, Double> values = new HashMap<>();
    values.put(Aggregation.AREA, part.getArea());
    values.put(Aggregation.LENGTH, part.getLength());
    return values;
  }

  private static Geometry polygonal(Map<?, ?> geometry) {
    Object type = geometry.get("type");
    Object coordinates = geometry.get("coordinates");
    if ("Polygon".equals(type)) {
      return polygon(coordinates);
    }
    if ("MultiPolygon".equals(type) && coordinates instanceof List<?> polygons) {
      List<Polygon> parts = new ArrayList<>();
      for (Object polygon : polygons) {
        parts.add(polygon(polygon));
      }
      return FACTORY.createMultiPolygon(parts.toArray(Polygon[]::new));
    }
    throw new IllegalArgumentException(
        "The geometry of the input 'clip' must be a GeoJSON Polygon or MultiPolygon.");
  }

  private static Polygon polygon(Object coordinates) {
    if (!(coordinates instanceof List<?> rings) || rings.isEmpty()) {
      throw new IllegalArgumentException("A polygon of the input 'clip' has no rings.");
    }
    List<LinearRing> linearRings = new ArrayList<>();
    for (Object ring : rings) {
      linearRings.add(ring(ring));
    }
    return FACTORY.createPolygon(
        linearRings.get(0), linearRings.subList(1, linearRings.size()).toArray(LinearRing[]::new));
  }

  private static LinearRing ring(Object positions) {
    if (!(positions instanceof List<?> list) || list.size() < 4) {
      throw new IllegalArgumentException(
          "A ring of a polygon of the input 'clip' must have at least four positions.");
    }
    List<Coordinate> coordinates = new ArrayList<>();
    for (Object position : list) {
      if (position instanceof List<?> xy
          && xy.size() >= 2
          && xy.get(0) instanceof Number x
          && xy.get(1) instanceof Number y) {
        coordinates.add(new Coordinate(x.doubleValue(), y.doubleValue()));
      } else {
        throw new IllegalArgumentException(
            "A position of a polygon of the input 'clip' must be [lon, lat].");
      }
    }
    if (!Objects.equals(coordinates.get(0), coordinates.get(coordinates.size() - 1))) {
      throw new IllegalArgumentException(
          "A ring of a polygon of the input 'clip' must end with its first position.");
    }
    return FACTORY.createLinearRing(coordinates.toArray(Coordinate[]::new));
  }

  private static Geometry transform(Geometry geometry, CrsTransformer transformer) {
    Geometry transformed = geometry.copy();
    transformed.apply(
        new CoordinateSequenceFilter() {
          @Override
          public void filter(CoordinateSequence sequence, int i) {
            CoordinateTuple point = transformer.transform(sequence.getX(i), sequence.getY(i));
            sequence.setOrdinate(i, 0, point.getX());
            sequence.setOrdinate(i, 1, point.getY());
          }

          @Override
          public boolean isDone() {
            return false;
          }

          @Override
          public boolean isGeometryChanged() {
            return true;
          }
        });
    return transformed;
  }
}
