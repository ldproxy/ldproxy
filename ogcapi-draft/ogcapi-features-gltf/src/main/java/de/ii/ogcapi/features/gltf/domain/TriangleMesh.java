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
import de.ii.xtraplatform.crs.domain.CrsTransformer;
import de.ii.xtraplatform.geometries.domain.LineString;
import de.ii.xtraplatform.geometries.domain.Polygon;
import de.ii.xtraplatform.geometries.domain.PolyhedralSurface;
import de.ii.xtraplatform.geometries.domain.PositionList;
import de.ii.xtraplatform.geometries.domain.transform.ClampToEllipsoid;
import de.ii.xtraplatform.geometries.domain.transform.GeometryVisitor;
import earcut4j.Earcut;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import org.immutables.value.Value;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
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
      PolyhedralSurface polyhedralSurface,
      double minZ,
      boolean clampToEllipsoid,
      boolean withNormals,
      boolean withOutline,
      int startIndex,
      Optional<CrsTransformer> crsTransformer,
      String featureName) {

    Optional<GeometryVisitor<?>> clampToEllipsoidVisitor =
        clampToEllipsoid ? Optional.of(new ClampToEllipsoid(minZ)) : Optional.empty();

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
    double[] normal = null;
    for (Polygon polygon : polyhedralSurface.getValue()) {
      numRing = 0;
      data.clear();
      normals.clear();
      holeIndices.clear();
      outlineIndices.clear();

      // change axis order, if we have a vertical polygon; ensure we still have a right-handed CRS
      for (LineString ring : polygon.getValue()) {
        if (clampToEllipsoidVisitor.isPresent()) {
          ring = (LineString) ring.accept(clampToEllipsoidVisitor.get());
        }
        PositionList posList = ring.getValue();
        double[] coordinates = posList.getCoordinates();
        // remove consecutive duplicate points
        int removed = 0;
        for (int i = 0; i < posList.getNumPositions() - 1; i++) {
          boolean done = false;
          while (!done) {
            if (coordinates[i * 3] == coordinates[(i + 1) * 3]
                && coordinates[i * 3 + 1] == coordinates[(i + 1) * 3 + 1]
                && coordinates[i * 3 + 2] == coordinates[(i + 1) * 3 + 2]) {
              // remove position i+1
              if ((posList.getNumPositions() - removed - i - 2) > 0) {
                System.arraycopy(
                    coordinates,
                    (i + 2) * 3,
                    coordinates,
                    (i + 1) * 3,
                    (posList.getNumPositions() - removed - i - 2) * 3);
              }
              removed++;
              break;
            } else {
              done = true;
            }
          }
        }

        if (removed > 0) {
          posList =
              PositionList.of(
                  posList.getAxes(),
                  Arrays.copyOf(coordinates, (posList.getNumPositions() - removed) * 3));
        }

        // skip a degenerated or colinear polygon (no area)
        if (posList.getNumPositions() < 4 || find3rdPoint(posList.getCoordinates())[1] == -1) {
          if (numRing == 0) {
            // skip polygon, if exterior boundary
            if (LOGGER.isTraceEnabled()) {
              LOGGER.trace(
                  "Skipping polygon of feature '{}', exterior ring has no effective area: {}",
                  featureName,
                  posList.getCoordinates());
            }
            break;
          } else {
            // skip ring, if a hole
            if (LOGGER.isTraceEnabled()) {
              LOGGER.trace(
                  "Skipping hole of feature '{}', interior ring has no effective area: {}",
                  featureName,
                  posList.getCoordinates());
            }
            continue;
          }
        }

        double[] coords = posList.getCoordinates();

        // transform coordinates?
        if (crsTransformer.isPresent()) {
          coords = crsTransformer.get().transform(coords, coords.length / 3, 3);
        }

        if (LOGGER.isTraceEnabled() && !isCoplanar(posList)) {
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
          if (withNormals) {
            normal = computeNormal(coords);
          }
        } else {
          // inner ring
          holeIndices.add(data.size() / 3);
        }

        data.addAll(Arrays.stream(coords).boxed().toList());

        if (withNormals) {
          if (Objects.isNull(normal)) {
            // skip polygon
            if (LOGGER.isDebugEnabled()) {
              LOGGER.debug(
                  "Skipping polygon of feature '{}', could not compute normal for exterior ring: {}",
                  featureName,
                  posList.getCoordinates());
            }
            break;
          }
          for (int i = 0; i < coords.length / 3; i++) {
            normals.add(normal[0]);
            normals.add(normal[1]);
            normals.add(normal[2]);
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
        IntStream.range(0, data.size() / 3)
            .mapToObj(
                n ->
                    new org.locationtech.jts.geom.Coordinate(
                        axes == AXES.XYZ
                            ? data.get(n * 3)
                            : (axes == AXES.YZX ? data.get(n * 3 + 1) : data.get(n * 3 + 2)),
                        axes == AXES.XYZ
                            ? data.get(n * 3 + 1)
                            : (axes == AXES.YZX ? data.get(n * 3 + 2) : data.get(n * 3))))
            .toArray(org.locationtech.jts.geom.Coordinate[]::new);

    org.locationtech.jts.geom.Polygon polygon;
    if (holeIndices.isEmpty()) {
      polygon =
          geometryFactory.createPolygon(
              geometryFactory.createLinearRing(
                  IntStream.range(0, data.size() / 3 + 1)
                      .mapToObj(n -> coords2dForTriangulation[n == data.size() / 3 ? 0 : n])
                      .toArray(org.locationtech.jts.geom.Coordinate[]::new)));
    } else {
      LinearRing shell =
          geometryFactory.createLinearRing(
              IntStream.range(0, holeIndices.get(0) + 1)
                  .mapToObj(n -> coords2dForTriangulation[n == data.size() / 3 ? 0 : n])
                  .toArray(org.locationtech.jts.geom.Coordinate[]::new));
      LinearRing[] holes =
          IntStream.range(1, holeIndices.size() + 1)
              .mapToObj(
                  r -> {
                    int n0 = holeIndices.get(r - 1);
                    int n1 = r < holeIndices.size() ? holeIndices.get(r) : data.size() / 3;
                    return geometryFactory.createLinearRing(
                        IntStream.range(n0, n1 + 1)
                            .mapToObj(
                                n -> {
                                  return coords2dForTriangulation[n == n1 ? n0 : n];
                                })
                            .toArray(org.locationtech.jts.geom.Coordinate[]::new));
                  })
              .toArray(org.locationtech.jts.geom.LinearRing[]::new);
      polygon = geometryFactory.createPolygon(shell, holes);
    }

    ConstrainedDelaunayTriangulator triangulator = new ConstrainedDelaunayTriangulator(polygon);
    org.locationtech.jts.geom.Geometry triangulation = triangulator.getResult();

    List<Integer> triangles = new ArrayList<>();
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
      double[] triangle =
          new double[] {
            data.get(p0 * 3),
            data.get(p0 * 3 + 1),
            data.get(p0 * 3 + 2),
            data.get(p1 * 3),
            data.get(p1 * 3 + 1),
            data.get(p1 * 3 + 2),
            data.get(p2 * 3),
            data.get(p2 * 3 + 1),
            data.get(p2 * 3 + 2)
          };
      boolean ccwTriangle =
          axes == AXES.XYZ
              ? computeAreaTriangle(triangle, 0, 1) > 0
              : axes == AXES.YZX
                  ? computeAreaTriangle(triangle, 1, 2) > 0
                  : computeAreaTriangle(triangle, 2, 0) > 0;
      if (ccwTriangle != ccw) {
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
        / 2.0d;
  }

  private static double computeAreaTriangle(double[] triangle, int axis1, int axis2) {
    return (triangle[axis1] * (triangle[3 + axis2] - triangle[6 + axis2])
            + triangle[3 + axis1] * (triangle[6 + axis2] - triangle[axis2])
            + triangle[6 + axis1] * (triangle[axis2] - triangle[3 + axis2]))
        / 2.0d;
  }

  private static double[] computeNormal(double... ring) {
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
      return null;
    }
    return new double[] {x / length, y / length, z / length};
  }

  private static boolean isCoplanar(PositionList posList) {
    if (posList.getNumPositions() < 4) {
      return true;
    }

    // find three points on the ring that are not collinear
    int[] n = find3rdPoint(posList.getCoordinates());

    if (n[1] == -1) {
      return true;
    }

    double[] coordinates = posList.getCoordinates();

    // establish plane from points A, B, C
    double[] ab =
        new double[] {
          coordinates[n[0] * 3] - coordinates[0],
          coordinates[n[0] * 3 + 1] - coordinates[1],
          coordinates[n[0] * 3 + 2] - coordinates[2]
        };
    double[] ac =
        new double[] {
          coordinates[n[1] * 3] - coordinates[0],
          coordinates[n[1] * 3 + 1] - coordinates[1],
          coordinates[n[1] * 3 + 2] - coordinates[2]
        };

    double[] x = crossProduct(ab, ac);

    double d = x[0] * coordinates[0] + x[1] * coordinates[1] + x[2] * coordinates[2];

    // check for all other points that they are on the plane
    for (int i = 3; i < coordinates.length / 3; i++) {
      if (Math.abs(
              x[0] * coordinates[i * 3]
                  + x[1] * coordinates[i * 3 + 1]
                  + x[2] * coordinates[i * 3 + 2]
                  - d)
          > EPSILON) {
        return false;
      }
    }
    return true;
  }

  private static int[] find3rdPoint(double[] coordinates) {
    // find three points on the ring that are not collinear
    int k = 1;
    boolean found = false;
    while (!found && k < coordinates.length / 3) {
      if (length(sub(coordinates, k, 0)) > EPSILON) {
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
    while (!found && n < coordinates.length / 3) {
      if (colinear(coordinates, 0, k, n)) {
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

  private static boolean colinear(double[] coordinates, int i, int j, int k) {
    double[] ab =
        new double[] {
          coordinates[j * 3] - coordinates[i * 3],
          coordinates[j * 3 + 1] - coordinates[i * 3 + 1],
          coordinates[j * 3 + 2] - coordinates[i * 3 + 2]
        };
    double[] ac =
        new double[] {
          coordinates[k * 3] - coordinates[i * 3],
          coordinates[k * 3 + 1] - coordinates[i * 3 + 1],
          coordinates[k * 3 + 2] - coordinates[i * 3 + 2]
        };

    return length(crossProduct(normalize(ab), normalize(ac))) < EPSILON;
  }

  private static boolean colinear(double[] v1, double[] v2, double[] v3) {
    double[] ab = new double[] {v2[0] - v1[0], v2[1] - v1[1], v2[2] - v1[2]};
    double[] ac = new double[] {v3[0] - v1[0], v3[1] - v1[1], v3[2] - v1[2]};

    return length(crossProduct(normalize(ab), normalize(ac))) < EPSILON;
  }

  private static double[] crossProduct(double[] v1, double[] v2) {
    return new double[] {
      v1[1] * v2[2] - v2[1] * v1[2], v2[0] * v1[2] - v1[0] * v2[2], v1[0] * v2[1] - v1[1] * v2[0]
    };
  }

  private static double[] sub(double[] coordinates, int i, int j) {
    return new double[] {
      coordinates[i * 3] - coordinates[j * 3],
      coordinates[i * 3 + 1] - coordinates[j * 3 + 1],
      coordinates[i * 3 + 2] - coordinates[j * 3 + 2]
    };
  }

  private static double[] sub(double[] v1, double[] v2) {
    return new double[] {v1[0] - v2[0], v1[1] - v2[1], v1[2] - v2[2]};
  }

  private static double length(double[] v) {
    return Math.sqrt(
        v.length == 2 ? v[0] * v[0] + v[1] * v[1] : v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
  }

  private static double[] normalize(double[] v) {
    double length = length(v);
    return v.length == 2
        ? new double[] {v[0] / length, v[1] / length}
        : new double[] {v[0] / length, v[1] / length, v[2] / length};
  }
}
