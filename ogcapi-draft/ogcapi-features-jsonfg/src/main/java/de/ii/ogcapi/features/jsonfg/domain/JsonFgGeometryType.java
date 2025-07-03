/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.jsonfg.domain;

import de.ii.xtraplatform.geometries.domain.SimpleFeatureGeometry;
import java.util.Optional;

public enum JsonFgGeometryType {
  POINT("Point", SimpleFeatureGeometry.POINT, 0),
  MULTI_POINT("MultiPoint", SimpleFeatureGeometry.MULTI_POINT, 0),
  LINE_STRING("LineString", SimpleFeatureGeometry.LINE_STRING, 1),
  MULTI_LINE_STRING("MultiLineString", SimpleFeatureGeometry.MULTI_LINE_STRING, 1),
  POLYGON("Polygon", SimpleFeatureGeometry.POLYGON, 2),
  MULTI_POLYGON("MultiPolygon", SimpleFeatureGeometry.MULTI_POLYGON, 2),
  POLYHEDRON("Polyhedron", SimpleFeatureGeometry.MULTI_POLYGON, 3, true, true),
  GEOMETRY_COLLECTION("GeometryCollection", SimpleFeatureGeometry.GEOMETRY_COLLECTION, null),
  NONE("", SimpleFeatureGeometry.NONE, null);

  private final String stringRepresentation;
  private final SimpleFeatureGeometry sfType;
  private final boolean isComposite;
  private final boolean isClosed;
  private final Integer geometryDimension;

  JsonFgGeometryType(
      String stringRepresentation, SimpleFeatureGeometry sfType, Integer geometryDimension) {
    this.stringRepresentation = stringRepresentation;
    this.sfType = sfType;
    this.geometryDimension = geometryDimension;
    this.isComposite = false;
    this.isClosed = false;
  }

  JsonFgGeometryType(
      String stringRepresentation,
      SimpleFeatureGeometry sfType,
      Integer geometryDimension,
      boolean isComposite,
      boolean isClosed) {
    this.stringRepresentation = stringRepresentation;
    this.sfType = sfType;
    this.geometryDimension = geometryDimension;
    this.isComposite = isComposite;
    this.isClosed = isClosed;
  }

  @Override
  public String toString() {
    return stringRepresentation;
  }

  public SimpleFeatureGeometry toSimpleFeatureGeometry() {
    return sfType;
  }

  public static JsonFgGeometryType forString(String type) {
    for (JsonFgGeometryType jsonFgType : JsonFgGeometryType.values()) {
      if (jsonFgType.toString().equals(type)) {
        return jsonFgType;
      }
    }

    return NONE;
  }

  public static JsonFgGeometryType forSimpleFeatureType(
      SimpleFeatureGeometry type, boolean isComposite, boolean isClosed) {
    for (JsonFgGeometryType jsonFgType : JsonFgGeometryType.values()) {
      if (jsonFgType.sfType.equals(type)
          && jsonFgType.isComposite == isComposite
          && jsonFgType.isClosed == isClosed) {
        return jsonFgType;
      }
    }

    return NONE;
  }

  public static Optional<Integer> getGeometryDimension(
      SimpleFeatureGeometry type, boolean isComposite, boolean isClosed) {
    for (JsonFgGeometryType jsonFgType : JsonFgGeometryType.values()) {
      if (jsonFgType.sfType.equals(type)
          && jsonFgType.isComposite == isComposite
          && jsonFgType.isClosed == isClosed) {
        return Optional.ofNullable(jsonFgType.geometryDimension);
      }
    }

    return Optional.empty();
  }

  public boolean isValid() {
    return this != NONE;
  }

  public boolean isSupported() {
    return isValid() && this != GEOMETRY_COLLECTION;
  }
}
