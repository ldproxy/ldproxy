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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

/**
 * A grid of square cells in the native, projected coordinate reference system. A feature belongs to
 * the cell of a point on its geometry, the same point for every feature, independent of a clip
 * area. A cell is identified by its center as a position [lon, lat] in CRS84.
 */
class GridCells {

  // about 0.1 m
  private static final int DECIMALS = 6;

  private final double width;
  private final CrsTransformer fromNative;

  GridCells(double width, CrsTransformer fromNative) {
    this.width = width;
    this.fromNative = fromNative;
  }

  /**
   * @param geometry a geometry in the native coordinate reference system
   * @return the center of the cell of the geometry, or null for an empty geometry
   */
  List<Double> center(Geometry geometry) {
    if (geometry.isEmpty()) {
      return null;
    }
    Point point = geometry.getInteriorPoint();
    double x = (Math.floor(point.getX() / width) + 0.5) * width;
    double y = (Math.floor(point.getY() / width) + 0.5) * width;
    CoordinateTuple center = fromNative.transform(x, y);
    return List.of(round(center.getX()), round(center.getY()));
  }

  private static double round(double value) {
    return BigDecimal.valueOf(value).setScale(DECIMALS, RoundingMode.HALF_UP).doubleValue();
  }
}
