/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.CrsInfo;
import de.ii.xtraplatform.crs.domain.CrsTransformer;
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import java.util.Arrays;
import java.util.Optional;

/**
 * Derives the bounding box of a collection in its storage CRS from the spatial extent in CRS84.
 *
 * <p>The box of the CRS84 extent is sampled along its edges and transformed to the storage CRS; the
 * minimum and maximum of each axis of the transformed points give the box. The edges of a CRS84 box
 * are curves in most other CRSs, so transforming only the corners would not always enclose the
 * data. The values are in the axis order of the storage CRS, as identified by its URI in
 * `storageCrs`, regardless of any forced axis order of the provider.
 */
class StorageCrsBbox {

  static final int SEGMENTS_PER_EDGE = 20;

  private final CrsTransformerFactory crsTransformerFactory;
  private final CrsInfo crsInfo;

  StorageCrsBbox(CrsTransformerFactory crsTransformerFactory, CrsInfo crsInfo) {
    this.crsTransformerFactory = crsTransformerFactory;
    this.crsInfo = crsInfo;
  }

  /**
   * @param extent the spatial extent in CRS84 or CRS84h
   * @param storageCrs the native CRS of the feature provider
   * @return the bounding box in the storage CRS, empty if the storage CRS is CRS84 or CRS84h, if
   *     the extent crosses the antimeridian or if the extent cannot be transformed
   */
  Optional<double[]> of(BoundingBox extent, EpsgCrs storageCrs) {
    return targetCrs(extent, storageCrs).flatMap(targetCrs -> transform(extent, targetCrs));
  }

  /**
   * @return the CRS identified by the `storageCrs` URI (see {@code EpsgCrs.toUriString()}), i.e.,
   *     without a forced axis order and without a vertical CRS; empty if no box is needed or
   *     possible
   */
  private Optional<EpsgCrs> targetCrs(BoundingBox extent, EpsgCrs storageCrs) {
    if (OgcCrs.CRS84.equals(storageCrs)
        || OgcCrs.CRS84h.equals(storageCrs)
        || extent.getXmin() > extent.getXmax()) {
      return Optional.empty();
    }

    EpsgCrs targetCrs = EpsgCrs.of(storageCrs.getCode());
    if (!crsInfo.isSupported(targetCrs) || crsInfo.is3d(targetCrs) && !extent.is3d()) {
      return Optional.empty();
    }

    return Optional.of(targetCrs);
  }

  private Optional<double[]> transform(BoundingBox extent, EpsgCrs targetCrs) {
    boolean is3d = crsInfo.is3d(targetCrs);
    Optional<CrsTransformer> transformer =
        is3d
            ? crsTransformerFactory.getTransformer(OgcCrs.CRS84h, targetCrs)
            : crsTransformerFactory.getTransformer(OgcCrs.CRS84, targetCrs, true);
    if (transformer.isEmpty()) {
      return Optional.empty();
    }

    int dimension = is3d ? 3 : 2;
    double[] points = sampleEdges(extent, dimension);
    double[] transformed =
        transformer.get().transform(points, points.length / dimension, dimension);
    if (transformed == null || transformed.length < points.length) {
      return Optional.empty();
    }

    return enclosingBox(transformed, points.length, dimension);
  }

  /**
   * @return the minimum and maximum of each axis of the first {@code length} coordinates, empty if
   *     a coordinate is not finite
   */
  private static Optional<double[]> enclosingBox(double[] coordinates, int length, int dimension) {
    double[] bbox = new double[2 * dimension];
    Arrays.fill(bbox, 0, dimension, Double.POSITIVE_INFINITY);
    Arrays.fill(bbox, dimension, 2 * dimension, Double.NEGATIVE_INFINITY);
    for (int i = 0; i < length; i++) {
      double value = coordinates[i];
      if (!Double.isFinite(value)) {
        return Optional.empty();
      }
      int axis = i % dimension;
      bbox[axis] = Math.min(bbox[axis], value);
      bbox[dimension + axis] = Math.max(bbox[dimension + axis], value);
    }
    return Optional.of(bbox);
  }

  private static double[] sampleEdges(BoundingBox extent, int dimension) {
    double[] heights =
        dimension == 3 ? new double[] {extent.getZmin(), extent.getZmax()} : new double[] {0};
    double[] points = new double[heights.length * 4 * SEGMENTS_PER_EDGE * dimension];

    int pos = 0;
    for (double z : heights) {
      for (int i = 0; i < SEGMENTS_PER_EDGE; i++) {
        double fraction = (double) i / SEGMENTS_PER_EDGE;
        double x = extent.getXmin() + fraction * (extent.getXmax() - extent.getXmin());
        double xReverse = extent.getXmax() - fraction * (extent.getXmax() - extent.getXmin());
        double y = extent.getYmin() + fraction * (extent.getYmax() - extent.getYmin());
        double yReverse = extent.getYmax() - fraction * (extent.getYmax() - extent.getYmin());
        // counter-clockwise: bottom, right, top, left edge, each without its end point
        pos = put(points, pos, dimension, x, extent.getYmin(), z);
        pos = put(points, pos, dimension, extent.getXmax(), y, z);
        pos = put(points, pos, dimension, xReverse, extent.getYmax(), z);
        pos = put(points, pos, dimension, extent.getXmin(), yReverse, z);
      }
    }

    return points;
  }

  /** writes a point and returns the position after it; {@code z} is ignored in 2D */
  private static int put(double[] points, int pos, int dimension, double x, double y, double z) {
    points[pos] = x;
    points[pos + 1] = y;
    if (dimension == 3) {
      points[pos + 2] = z;
    }
    return pos + dimension;
  }
}
