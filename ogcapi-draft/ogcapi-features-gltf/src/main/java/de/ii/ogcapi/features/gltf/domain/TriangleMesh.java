/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gltf.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.html.domain.Geometry;
import de.ii.ogcapi.features.html.domain.Geometry.Coordinate;
import de.ii.xtraplatform.crs.domain.CrsTransformer;
import earcut4j.Earcut;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.immutables.value.Value;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.triangulate.polygon.ConstrainedDelaunayTriangulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonDeserialize(builder = ImmutableTriangleMesh.Builder.class)
@SuppressWarnings("PMD.TooManyMethods")
public interface TriangleMesh {

  Logger LOGGER = LoggerFactory.getLogger(TriangleMesh.class);

  GeometryFactory geometryFactory = new GeometryFactory();

  double EPSILON = 1.0e-7;

  enum AXES {
    XYZ,
    YZX,
    ZXY
  }

  List<Integer> getIndices();

  List<Double> getVertices();

  List<Double> getNormals();

  List<Integer> getOutlineIndices();

  @SuppressWarnings({
    "PMD.ExcessiveMethodLength",
    "PMD.NcssCount",
    "PMD.AvoidInstantiatingObjectsInLoops",
    "PMD.UnusedLocalVariable"
  })
  static TriangleMesh of(
      Geometry.MultiPolygon multiPolygon,
      double minZ,
      boolean clampToEllipsoid,
      boolean withNormals,
      boolean withOutline,
      int startIndex,
      Optional<CrsTransformer> crsTransformer,
      String featureName) {

    ImmutableTriangleMesh.Builder builder = ImmutableTriangleMesh.builder();

    // triangulate the polygons, translate relative to origin
    int vertexCountSurface = 0;
    int numRing;
    AXES axes = AXES.XYZ;
    boolean ccw = true;
    List<Double> data = new ArrayList<>();
    List<Integer> holeIndices = new ArrayList<>();
    List<Double> normals = new ArrayList<>();
    List<Integer> outlineIndices = new ArrayList<>();
    double area;
    for (Geometry.Polygon polygon : multiPolygon.getCoordinates()) {
      numRing = 0;
      data.clear();
      normals.clear();
      holeIndices.clear();
      outlineIndices.clear();

      // change axis order, if we have a vertical polygon; ensure we still have a right-handed CRS
      for (Geometry.LineString ring : polygon.getCoordinates()) {
        List<Coordinate> coordsRing = ring.getCoordinates();

        // remove consecutive duplicate points
        List<Coordinate> coordList =
            IntStream.range(0, coordsRing.size())
                .filter(
                    n ->
                        n == 0
                            || !Objects.equals(
                                coordsRing.get(n).get(0), coordsRing.get(n - 1).get(0))
                            || !Objects.equals(
                                coordsRing.get(n).get(1), coordsRing.get(n - 1).get(1))
                            || !Objects.equals(
                                coordsRing.get(n).get(2), coordsRing.get(n - 1).get(2)))
                .mapToObj(coordsRing::get)
                .collect(Collectors.toUnmodifiableList());

        // skip a degenerated or colinear polygon (no area)
        if (coordList.size() < 4 || find3rdPoint(coordList)[1] == -1) {
          if (numRing == 0) {
            // skip polygon, if exterior boundary
            if (LOGGER.isTraceEnabled()) {
              LOGGER.trace(
                  "Skipping polygon of feature '{}', exterior ring has no effective area: {}",
                  featureName,
                  coordList);
            }
            break;
          } else {
            // skip ring, if a hole
            if (LOGGER.isTraceEnabled()) {
              LOGGER.trace(
                  "Skipping hole of feature '{}', interior ring has no effective area: {}",
                  featureName,
                  coordList);
            }
            continue;
          }
        }

        // do not copy the last point, same as first point
        double[] coords = new double[(coordList.size() - 1) * 3];
        for (int n = 0; n < coordList.size() - 1; n++) {
          coords[n * 3] = coordList.get(n).get(0);
          coords[n * 3 + 1] = coordList.get(n).get(1);
          coords[n * 3 + 2] =
              clampToEllipsoid ? coordList.get(n).get(2) - minZ : coordList.get(n).get(2);
        }

        // transform coordinates?
        if (crsTransformer.isPresent()) {
          coords = crsTransformer.get().transform(coords, coords.length / 3, 3);
        }

        if (LOGGER.isTraceEnabled() && !isCoplanar(coordList)) {
          LOGGER.trace(
              "Feature '{}' has a ring that is not coplanar. The glTF mesh may be invalid. Coordinates: {}",
              featureName,
              coords);
        }

        if (numRing == 0) {
          // outer ring
          final double area01 = computeArea(coords, 0, 1);
          final double area12 = computeArea(coords, 1, 2);
          final double area20 = computeArea(coords, 2, 0);
          if (Math.abs(area01) > Math.abs(area12) && Math.abs(area01) > Math.abs(area20)) {
            axes = AXES.XYZ;
            area = area01;
          } else if (Math.abs(area12) > Math.abs(area20)) {
            axes = AXES.YZX;
            area = area12;
          } else if (Math.abs(area20) > 0.0) {
            axes = AXES.ZXY;
            area = area20;
          } else {
            if (LOGGER.isTraceEnabled()) {
              LOGGER.trace(
                  "The area of the exterior ring is too small, the polygon is ignored: {} {} {} - {}",
                  area01,
                  area12,
                  area20,
                  coords);
            }
            break;
          }
          ccw = area > 0;
        } else {
          // inner ring
          holeIndices.add(data.size() / 3 + 1);
        }

        data.addAll(Arrays.stream(coords).boxed().collect(Collectors.toUnmodifiableList()));

        if (withNormals) {
          Geometry.Coordinate normal = computeNormal(coords);
          if (normal.isEmpty()) {
            if (numRing == 0) {
              // skip polygon, if exterior boundary
              if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                    "Skipping polygon of feature '{}', could not compute normal for exterior ring: {}",
                    featureName,
                    coordList);
              }
              break;
            } else {
              // skip ring, if a hole
              if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                    "Skipping hole of feature '{}', could not compute normal for exterior ring: {}",
                    featureName,
                    coordList);
              }
              continue;
            }
          }
          for (int i = 0; i < coords.length / 3; i++) {
            normals.addAll(normal);
          }
        }

        if (withOutline && coords.length > 6) {
          int l = coords.length;
          for (int i = 0; i < l / 3; i++) {
            // also include closing edge
            outlineIndices.add(i);
            outlineIndices.add(i < l / 3 - 1 ? i + 1 : 0);
          }
        }

        numRing++;
      }

      if (data.size() < 9) {
        continue;
      }

      // try JTS triangulation first; it is a bit slower, but produces better results while earcut
      // sometimes creates incorrect triangles; on the other hand, JTS is not always able to
      // triangulate, so we fall back to earcut in that case
      List<Integer> triangles;
      try {
        triangles = triangulateWithJts(data, holeIndices, axes);
      } catch (Exception e) {
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace(
              "JTS triangulation failed for a polygon in Feature '{}', falling back to earcut: {}",
              featureName,
              e.getMessage());
        }
        triangles = triangulateWithEarcut(data, holeIndices, axes);
      }

      if (triangles.isEmpty()) {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug(
              "Cannot triangulate a polygon of feature '{}', the polygon is ignored: {}",
              featureName,
              data);
        }
        continue;
      }

      ensureTriangleOrientation(triangles, data, holeIndices, axes, ccw);

      // we have a triangle mesh for the polygon
      for (int ringIndex : triangles) {
        builder.addIndices(startIndex + vertexCountSurface + ringIndex);
      }
      builder.addAllVertices(data);
      builder.addAllNormals(normals);
      if (withOutline) {
        for (int outlineIndex : outlineIndices) {
          builder.addOutlineIndices(startIndex + vertexCountSurface + outlineIndex);
        }
      }

      vertexCountSurface += data.size() / 3;
    }

    return builder.build();
  }

  private static List<Integer> triangulateWithJts(
      List<Double> data, List<Integer> holeIndices, AXES axes) {
    org.locationtech.jts.geom.Coordinate[] coords2dForTriangulation =
        IntStream.range(0, data.size() / 3 + 1)
            .mapToObj(
                n -> {
                  int n2 = n % (data.size() / 3);
                  return new org.locationtech.jts.geom.Coordinate(
                      axes == AXES.XYZ
                          ? data.get(n2 * 3)
                          : (axes == AXES.YZX ? data.get(n2 * 3 + 1) : data.get(n2 * 3 + 2)),
                      axes == AXES.XYZ
                          ? data.get(n2 * 3 + 1)
                          : (axes == AXES.YZX ? data.get(n2 * 3 + 2) : data.get(n2 * 3)));
                })
            .toArray(org.locationtech.jts.geom.Coordinate[]::new);

    List<Integer> triangles = new ArrayList<>();
    int outerRingEnd = holeIndices.isEmpty() ? coords2dForTriangulation.length : holeIndices.get(0);
    org.locationtech.jts.geom.Coordinate[] outerRing =
        Arrays.copyOfRange(coords2dForTriangulation, 0, outerRingEnd);

    org.locationtech.jts.geom.LinearRing shell = geometryFactory.createLinearRing(outerRing);
    org.locationtech.jts.geom.LinearRing[] holes =
        new org.locationtech.jts.geom.LinearRing[holeIndices.size()];

    for (int i = 0; i < holeIndices.size(); i++) {
      int startIdx = holeIndices.get(i);
      int endIdx =
          i < holeIndices.size() - 1 ? holeIndices.get(i + 1) : coords2dForTriangulation.length;
      org.locationtech.jts.geom.Coordinate[] holeCoords =
          Arrays.copyOfRange(coords2dForTriangulation, startIdx, endIdx);
      holes[i] = geometryFactory.createLinearRing(holeCoords);
    }

    org.locationtech.jts.geom.Polygon polygon = geometryFactory.createPolygon(shell, holes);
    ConstrainedDelaunayTriangulator triangulator = new ConstrainedDelaunayTriangulator(polygon);
    org.locationtech.jts.geom.Geometry triangulation = triangulator.getResult();

    for (int i = 0; i < triangulation.getNumGeometries(); i++) {
      org.locationtech.jts.geom.Polygon tri =
          (org.locationtech.jts.geom.Polygon) triangulation.getGeometryN(i);
      org.locationtech.jts.geom.Coordinate[] triCoords = tri.getExteriorRing().getCoordinates();

      for (int j = 0; j < 3; j++) {
        for (int k = 0; k < coords2dForTriangulation.length; k++) {
          if (triCoords[j].equals2D(coords2dForTriangulation[k])) {
            triangles.add(k);
            break;
          }
        }
      }
    }
    return triangles;
  }

  private static void ensureTriangleOrientation(
      List<Integer> triangles,
      List<Double> data,
      List<Integer> holeIndices,
      AXES axes,
      boolean ccw) {
    for (int i = 0; i < triangles.size() / 3; i++) {
      Integer p0 = triangles.get(i * 3);
      Integer p1 = triangles.get(i * 3 + 1);
      Integer p2 = triangles.get(i * 3 + 2);
      ImmutableList<Coordinate> triangle =
          ImmutableList.of(
              Coordinate.of(data.get(p0 * 3), data.get(p0 * 3 + 1), data.get(p0 * 3 + 2)),
              Coordinate.of(data.get(p1 * 3), data.get(p1 * 3 + 1), data.get(p1 * 3 + 2)),
              Coordinate.of(data.get(p2 * 3), data.get(p2 * 3 + 1), data.get(p2 * 3 + 2)));
      boolean ccwTriangle =
          axes == AXES.XYZ
              ? computeAreaTriangle(triangle, 0, 1) > 0
              : axes == AXES.YZX
                  ? computeAreaTriangle(triangle, 1, 2) > 0
                  : computeAreaTriangle(triangle, 2, 0) > 0;
      boolean exterior = holeIndices.isEmpty() || p0 <= holeIndices.get(0);
      if (exterior && ccwTriangle != ccw || !exterior && ccwTriangle == ccw) {
        // switch orientation, if the triangle has the wrong orientation
        triangles.set(i * 3, p2);
        triangles.set(i * 3 + 2, p0);
      }
    }
  }

  private static List<Integer> triangulateWithEarcut(
      List<Double> data, List<Integer> holeIndices, AXES axes) {
    double[] coords2dForTriangulation = new double[data.size() / 3 * 2];
    IntStream.range(0, data.size() / 3)
        .forEach(
            n -> {
              int xIndex = axes == AXES.XYZ ? n * 3 : (axes == AXES.YZX ? n * 3 + 1 : n * 3 + 2);
              int yIndex = axes == AXES.XYZ ? n * 3 + 1 : (axes == AXES.YZX ? n * 3 + 2 : n * 3);
              coords2dForTriangulation[n * 2] = data.get(xIndex);
              coords2dForTriangulation[n * 2 + 1] = data.get(yIndex);
            });

    List<Integer> triangles =
        coords2dForTriangulation.length > 6
            ? Earcut.earcut(
                coords2dForTriangulation,
                holeIndices.isEmpty()
                    ? null
                    : holeIndices.stream().mapToInt(Integer::intValue).toArray(),
                2)
            : new ArrayList<>(ImmutableList.of(0, 1, 2));

    return triangles;
  }

  private static double computeArea(double[] ring, int axis1, int axis2) {
    int len = ring.length / 3;
    return (IntStream.range(0, len)
                .mapToDouble(n -> ring[n * 3 + axis1] * ring[((n + 1) % len) * 3 + axis2])
                .sum()
            - IntStream.range(0, ring.length / 3)
                .mapToDouble(n -> ring[((n + 1) % len) * 3 + axis1] * ring[n * 3 + axis2])
                .sum())
        / 2;
  }

  private static double computeAreaTriangle(
      List<Geometry.Coordinate> triangle, int axis1, int axis2) {
    return (triangle.get(0).get(axis1) * (triangle.get(1).get(axis2) - triangle.get(2).get(axis2))
            + triangle.get(1).get(axis1) * (triangle.get(2).get(axis2) - triangle.get(0).get(axis2))
            + triangle.get(2).get(axis1)
                * (triangle.get(0).get(axis2) - triangle.get(1).get(axis2)))
        / 2.0d;
  }

  private static Geometry.Coordinate computeNormal(double... ring) {
    if (ring.length < 9) {
      throw new IllegalStateException(
          String.format("Ring with less than 3 coordinates: %s", Arrays.toString(ring)));
    }

    double x = 0.0;
    double y = 0.0;
    double z = 0.0;
    if (ring.length == 9) {
      // a triangle, use cross product
      double ux = ring[3] - ring[0];
      double uy = ring[4] - ring[1];
      double uz = ring[5] - ring[2];
      double vx = ring[6] - ring[0];
      double vy = ring[7] - ring[1];
      double vz = ring[8] - ring[2];
      x = uy * vz - uz * vy;
      y = uz * vx - ux * vz;
      z = ux * vy - uy * vx;
    } else {
      // use Newell's method
      int l = ring.length;
      for (int i = 0; i < l / 3; i++) {
        x += (ring[i * 3 + 1] - ring[(i * 3 + 4) % l]) * (ring[i * 3 + 2] + ring[(i * 3 + 5) % l]);
        y += (ring[i * 3 + 2] - ring[(i * 3 + 5) % l]) * (ring[i * 3] + ring[(i * 3 + 3) % l]);
        z += (ring[i * 3] - ring[(i * 3 + 3) % l]) * (ring[i * 3 + 1] + ring[(i * 3 + 4) % l]);
      }
    }
    double length = Math.sqrt(x * x + y * y + z * z);
    if (length == 0.0) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Normal has length 0 for ring: {}", ring);
      }
      return Geometry.Coordinate.of(ImmutableList.of());
    }
    return Geometry.Coordinate.of(x / length, y / length, z / length);
  }

  private static boolean isCoplanar(List<Geometry.Coordinate> coords) {
    if (coords.size() < 4) {
      return true;
    }

    // find three points on the ring that are not collinear
    int[] n = find3rdPoint(coords);

    if (n[1] == -1) {
      return true;
    }

    // establish plane from points A, B, C
    Coordinate ab =
        Coordinate.of(
            coords.get(n[0]).get(0) - coords.get(0).get(0),
            coords.get(n[0]).get(1) - coords.get(0).get(1),
            coords.get(n[0]).get(2) - coords.get(0).get(2));
    Coordinate ac =
        Coordinate.of(
            coords.get(n[1]).get(0) - coords.get(0).get(0),
            coords.get(n[1]).get(1) - coords.get(0).get(1),
            coords.get(n[1]).get(2) - coords.get(0).get(2));

    Coordinate x = crossProduct(ab, ac);

    double d =
        x.get(0) * coords.get(0).get(0)
            + x.get(1) * coords.get(0).get(1)
            + x.get(2) * coords.get(0).get(2);

    // check for all other points that they are on the plane
    for (int i = 3; i < coords.size(); i++) {
      if (Math.abs(
              x.get(0) * coords.get(i).get(0)
                  + x.get(1) * coords.get(i).get(1)
                  + x.get(2) * coords.get(i).get(2)
                  - d)
          > EPSILON) {
        return false;
      }
    }
    return true;
  }

  private static int[] find3rdPoint(List<Geometry.Coordinate> coords) {
    // find three points on the ring that are not collinear
    int k = 1;
    boolean found = false;
    while (!found && k < coords.size()) {
      if (length(sub(coords.get(k), coords.get(0))) > EPSILON) {
        found = true;
      } else {
        k++;
      }
    }
    if (!found) {
      return new int[] {-1, -1};
    }
    int n = k + 1;
    found = false;
    while (!found && n < coords.size()) {
      if (colinear(coords.get(0), coords.get(k), coords.get(n))) {
        n++;
      } else {
        found = true;
      }
    }

    if (!found) {
      return new int[] {k, -1};
    }

    return new int[] {k, n};
  }

  private static boolean colinear(
      Geometry.Coordinate v1, Geometry.Coordinate v2, Geometry.Coordinate v3) {
    Coordinate ab =
        Coordinate.of(v2.get(0) - v1.get(0), v2.get(1) - v1.get(1), v2.get(2) - v1.get(2));
    Coordinate ac =
        Coordinate.of(v3.get(0) - v1.get(0), v3.get(1) - v1.get(1), v3.get(2) - v1.get(2));

    return length(crossProduct(normalize(ab), normalize(ac))) < EPSILON;
  }

  private static Geometry.Coordinate crossProduct(Geometry.Coordinate v1, Geometry.Coordinate v2) {
    return Coordinate.of(
        v1.get(1) * v2.get(2) - v2.get(1) * v1.get(2),
        v2.get(0) * v1.get(2) - v1.get(0) * v2.get(2),
        v1.get(0) * v2.get(1) - v1.get(1) * v2.get(0));
  }

  private static Geometry.Coordinate sub(Geometry.Coordinate v1, Geometry.Coordinate v2) {
    return Coordinate.of(v1.get(0) - v2.get(0), v1.get(1) - v2.get(1), v1.get(1) - v2.get(1));
  }

  private static double length(Geometry.Coordinate v) {
    return Math.sqrt(
        v.size() == 2
            ? v.get(0) * v.get(0) + v.get(1) * v.get(1)
            : v.get(0) * v.get(0) + v.get(1) * v.get(1) + v.get(2) * v.get(2));
  }

  private static Geometry.Coordinate normalize(Geometry.Coordinate v) {
    double length = length(v);
    return v.size() == 2
        ? new Geometry.Coordinate(v.get(0) / length, v.get(1) / length)
        : new Geometry.Coordinate(v.get(0) / length, v.get(1) / length, v.get(2) / length);
  }
}
