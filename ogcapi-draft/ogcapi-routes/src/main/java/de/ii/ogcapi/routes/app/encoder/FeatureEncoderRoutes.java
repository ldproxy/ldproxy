/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.routes.app.encoder;

import static java.lang.Math.abs;
import static java.lang.Math.round;
import static java.lang.Math.sqrt;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.foundation.domain.UnprocessableEntity;
import de.ii.ogcapi.routes.domain.FeatureTransformationContextRoutes;
import de.ii.ogcapi.routes.domain.ImmutableRoute;
import de.ii.ogcapi.routes.domain.ImmutableRouteComponent;
import de.ii.ogcapi.routes.domain.RouteComponent;
import de.ii.xtraplatform.crs.domain.CrsInfo;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.features.domain.FeatureObjectEncoder;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.PropertyBase;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.domain.SchemaMapping;
import de.ii.xtraplatform.geometries.domain.LineString;
import de.ii.xtraplatform.geometries.domain.MultiPoint;
import de.ii.xtraplatform.geometries.domain.Point;
import de.ii.xtraplatform.geometries.domain.Position;
import de.ii.xtraplatform.geometries.domain.PositionList;
import de.ii.xtraplatform.geometries.domain.transform.DouglasPeuckerLineSimplifier;
import de.ii.xtraplatform.geometries.domain.transform.MinMaxDeriver;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import javax.measure.Unit;
import org.kortforsyningen.proj.Units;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeatureEncoderRoutes extends FeatureObjectEncoder<PropertyRoutes, FeatureRoutes> {

  private static final Logger LOGGER = LoggerFactory.getLogger(FeatureEncoderRoutes.class);

  private static final double R = 6378137.0;

  private final FeatureTransformationContextRoutes transformationContext;
  private final ImmutableRoute.Builder builder;
  private RouteComponent<Point> start;
  private Point lastPoint;
  private Double lastAngle;
  private List<Position> overviewGeometry;
  private List<RouteComponent<Point>> segments;
  private Double aggCost;
  private Double aggDuration;
  private Double aggLength;
  private Double aggAscent;
  private Double aggDescent;
  private ImmutableRouteComponent.Builder<Point> segmentBuilder;
  private boolean firstSegment;
  private boolean is3d;
  private boolean isReverse;
  private String speedLimitUnit;

  public FeatureEncoderRoutes(FeatureTransformationContextRoutes transformationContext) {
    this.transformationContext = transformationContext;
    this.builder = new ImmutableRoute.Builder();
  }

  @Override
  public FeatureRoutes createFeature() {
    return ModifiableFeatureRoutes.create();
  }

  @Override
  public PropertyRoutes createProperty() {
    return ModifiablePropertyRoutes.create();
  }

  @Override
  public void onStart(ModifiableContext<FeatureSchema, SchemaMapping> context) {
    transformationContext.getName().ifPresent(builder::name);
    context.metadata().getNumberMatched().ifPresent(num -> LOGGER.debug("numberMatched {}", num));
    context.metadata().getNumberReturned().ifPresent(num -> LOGGER.debug("numberReturned {}", num));
    lastPoint = null;
    lastAngle = 0.0;
    firstSegment = true;
    is3d = true;
    start = null;
    segments = new ArrayList<>();
    overviewGeometry = new ArrayList<>();
    aggCost = 0.0;
    aggDuration = 0.0;
    aggLength = 0.0;
    aggAscent = 0.0;
    aggDescent = 0.0;
    speedLimitUnit = transformationContext.getSpeedLimitUnit();
  }

  @Override
  public void onFeature(FeatureRoutes feature) {
    String id = feature.getIdValue();

    PositionList coordinates =
        feature
            .getGeometry()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        String.format(
                            "Route segment with sequence number '%s' is without a geometry. Cannot construct route.",
                            id)))
            .getValue();

    AtomicReference<ImmutableMap.Builder<String, Object>> propertyBuilder =
        new AtomicReference<>(ImmutableMap.builder());
    int coordCount = coordinates.getNumPositions();

    // swap curve, if necessary
    int node =
        feature
            .findPropertyByPath("node")
            .map(PropertyRoutes::getFirstValue)
            .map(Integer::parseInt)
            .orElse(Integer.MIN_VALUE);
    int target =
        feature
            .findPropertyByPath(ImmutableList.of("data", "target"))
            .map(PropertyRoutes::getFirstValue)
            .map(Integer::parseInt)
            .orElse(Integer.MIN_VALUE);
    if (node != Integer.MIN_VALUE && target != Integer.MIN_VALUE && node == target) {
      coordinates = coordinates.reverse();
      isReverse = true;
    } else {
      isReverse = false;
    }

    if (firstSegment) {
      firstSegment = false;
      Position firstCoord = coordinates.get(0);
      start =
          ImmutableRouteComponent.<Point>builder()
              .id(2)
              .geometry(Point.of(firstCoord))
              .putProperties("featureType", "start")
              .build();
      overviewGeometry.add(firstCoord);
      is3d = firstCoord.size() == 3;
    } else {
      double angle = computeAngle(coordinates.get(0), coordinates.get(1));
      double changeDeg = Math.toDegrees(deltaAngle(lastAngle, angle));
      segments.add(
          segmentBuilder
              .putProperties(
                  "instructions", changeDeg > 45 ? "left" : changeDeg < -45 ? "right" : "continue")
              .putProperties("directionChange_deg", round(changeDeg))
              .build());
    }

    for (int i = 1; i < coordinates.getNumPositions(); i++) {
      overviewGeometry.add(coordinates.get(i));
    }

    if (is3d) {
      AtomicReference<Double> segAscent = new AtomicReference<>(0.0);
      AtomicReference<Double> segDescent = new AtomicReference<>(0.0);
      PositionList finalCoordinates = coordinates;
      IntStream.range(1, coordCount)
          .mapToDouble(i -> finalCoordinates.get(i).z() - finalCoordinates.get(i - 1).z())
          .forEach(
              deltaZ -> {
                if (deltaZ > 0) segAscent.updateAndGet(v -> v + deltaZ);
                else segDescent.updateAndGet(v -> v - deltaZ);
              });
      aggAscent += segAscent.get();
      propertyBuilder.get().put("ascent_m", String.format(Locale.US, "%.1f", segAscent.get()));
      aggDescent += segDescent.get();
      propertyBuilder.get().put("descent_m", String.format(Locale.US, "%.1f", segDescent.get()));
    }

    lastPoint = Point.of(coordinates.get(coordCount - 1));
    lastAngle = computeAngle(coordinates.get(coordCount - 2), coordinates.get(coordCount - 1));

    feature
        .findPropertyByPath("cost")
        .map(PropertyRoutes::getFirstValue)
        .map(Double::parseDouble)
        .ifPresent(
            cost -> {
              aggCost += cost;
            });
    feature.getProperties().stream()
        .filter(p -> p.getPropertyPath().get(0).equals("data"))
        .filter(p -> p.getSchema().filter(SchemaBase::isSpatial).isEmpty())
        .forEach(
            p -> {
              propertyBuilder.set(processProperty(id, p, propertyBuilder.get()));
            });
    segmentBuilder =
        ImmutableRouteComponent.<Point>builder()
            .geometry(lastPoint)
            .id(Integer.parseInt(id) + 3)
            .putProperties("featureType", "segment")
            .putAllProperties(propertyBuilder.get().build());
  }

  private ImmutableMap.Builder<String, Object> processProperty(
      String id, PropertyRoutes p, ImmutableMap.Builder<String, Object> propertyBuilder) {
    if (p.getSchema().filter(SchemaBase::isSpatial).isPresent()) return propertyBuilder;

    SchemaBase.Type type =
        p.getSchema().map(FeatureSchema::getType).orElse(SchemaBase.Type.UNKNOWN);
    if (p.getType().equals(PropertyBase.Type.OBJECT)) {
      for (PropertyRoutes p2 : p.getNestedProperties()) {
        propertyBuilder = processProperty(id, p2, propertyBuilder);
      }
    } else if (!p.getType().equals(PropertyBase.Type.VALUE)) {
      LOGGER.debug("Property '{}' of segment '{}' is not a value and is ignored.", p.getName(), id);
    } else if (type.equals(SchemaBase.Type.BOOLEAN)) {
      propertyBuilder.put(p.getName(), Boolean.parseBoolean(p.getFirstValue()));
    } else if (type.equals(SchemaBase.Type.FLOAT)) {
      String name = p.getName();
      double value = Double.parseDouble(p.getFirstValue());
      if (name.equals("length_m")) {
        propertyBuilder.put(name, round(value));
        aggLength += value;
      } else if (name.equals("maxHeight_m") || name.equals("maxWeight_t")) {
        propertyBuilder.put(name, String.format(Locale.US, "%.1f", value));
      } else if (name.equals("duration_forward_s") && !isReverse) {
        propertyBuilder.put("duration_s", round(value));
        aggDuration += value;
      } else if (name.equals("duration_backward_s") && isReverse) {
        value = abs(value);
        propertyBuilder.put("duration_s", value);
        aggDuration += value;
      } else if (name.equals("maxspeed_forward") && !isReverse) {
        propertyBuilder.put("maxSpeed", round(value));
        propertyBuilder.put("maxSpeedUnit", speedLimitUnit);
      } else if (name.equals("maxspeed_backward") && isReverse) {
        propertyBuilder.put("maxSpeed", round(value));
        propertyBuilder.put("maxSpeedUnit", speedLimitUnit);
      }
    } else if (type.equals(SchemaBase.Type.INTEGER)) {
      String name = p.getName();
      int value = Integer.parseInt(p.getFirstValue());
      if (!name.equals("source") && !name.equals("target")) propertyBuilder.put(name, value);
    } else if (type.equals(SchemaBase.Type.STRING)
        || type.equals(SchemaBase.Type.DATE)
        || type.equals(SchemaBase.Type.DATETIME)) {
      if (!p.getFirstValue().isEmpty()) {
        propertyBuilder.put(p.getName(), p.getFirstValue());
      }
    } else
      LOGGER.debug(
          "Property '{}' of segment '{}' property is of unsupported type '{}' and is ignored.",
          p.getName(),
          id,
          type);

    return propertyBuilder;
  }

  @Override
  public void onEnd(ModifiableContext<FeatureSchema, SchemaMapping> context) {
    if (Objects.isNull(start) || Objects.isNull(lastPoint)) {
      throw new UnprocessableEntity("No route was found between the start and end location.");
    }

    if (is3d) {
      transformationContext
          .getElevationProfileSimplificationTolerance()
          .ifPresent(this::computeSimplifiedElevationProfile);
    }

    builder.bbox(computeBbox());
    long processingDuration =
        (System.nanoTime() - transformationContext.getStartTimeNano()) / 1000000;
    ImmutableMap.Builder<String, Object> propertyBuilder = ImmutableMap.builder();
    if (aggLength > 0.0) propertyBuilder.put("length_m", round(aggLength));
    if (aggDuration > 0.0) propertyBuilder.put("duration_s", round(aggDuration));
    if (aggAscent > 0.0) propertyBuilder.put("ascent_m", round(aggAscent));
    if (aggDescent > 0.0) propertyBuilder.put("descent_m", round(aggDescent));
    propertyBuilder.put("processingTime", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
    propertyBuilder.put("processingDuration_ms", round(processingDuration));
    builder.addFeatures(
        ImmutableRouteComponent.<LineString>builder()
            .id(1)
            .geometry(LineString.of(overviewGeometry))
            .putProperties("featureType", "overview")
            .putAllProperties(propertyBuilder.build())
            .build(),
        start,
        ImmutableRouteComponent.<Point>builder()
            .id(3)
            .geometry(lastPoint)
            .putProperties("featureType", "end")
            .build());
    segments.add(segmentBuilder.build());
    builder.addAllFeatures(segments);
    builder.links(transformationContext.getLinks());
    byte[] result =
        transformationContext
            .getFormat()
            .getRouteAsByteArray(
                builder.build(),
                transformationContext.getApiData(),
                transformationContext.getOgcApiRequest());
    push(result);
  }

  private void computeSimplifiedElevationProfile(double tolerance) {
    CrsInfo crsInfo = transformationContext.getCrsInfo();
    EpsgCrs crs = transformationContext.getCrs();
    List<Unit<?>> units = crsInfo.getAxisUnits(crs);
    boolean degree =
        units.size() >= 2 && units.get(0).equals(Units.DEGREE) && units.get(1).equals(Units.DEGREE);
    boolean latAxisY = false;
    if (degree) {
      List<String> axesAbbrev = crsInfo.getAxisAbbreviations(crs);
      if (axesAbbrev.get(0).equalsIgnoreCase("lat")) {
        latAxisY = false;
      } else if (axesAbbrev.get(1).equalsIgnoreCase("lat")) {
        latAxisY = true;
      } else {
        degree = false;
      }
    }
    double[] profile = new double[2 * overviewGeometry.size()];
    double d = 0.0;
    Position previous = overviewGeometry.get(0);
    profile[0] = d;
    profile[1] = previous.z();
    for (int i = 1; i < overviewGeometry.size(); i++) {
      Position current = overviewGeometry.get(i);
      double dx = current.x() - previous.x();
      double dy = current.y() - previous.y();
      double dz = current.z() - previous.z();
      d +=
          degree
              ? computeDistanceInMeter(
                  dx,
                  dy,
                  dz,
                  latAxisY ? previous.y() : previous.x(),
                  latAxisY ? current.y() : current.x())
              : sqrt(dx * dx + dy * dy + dz * dz);
      profile[i * 2] = d;
      profile[i * 2 + 1] = current.z();
      previous = current;
    }
    DouglasPeuckerLineSimplifier simplifier = new DouglasPeuckerLineSimplifier(tolerance, 2);
    double[] simplifiedProfile = simplifier.simplify(profile, overviewGeometry.size());
    LOGGER.debug(
        "From route geometry: ascent {}m, descent {}m, length {}m",
        round(aggAscent),
        round(aggDescent),
        round(profile[profile.length - 2]));
    aggAscent = 0.0;
    aggDescent = 0.0;
    for (int i = 1; i < simplifiedProfile.length / 2; i++) {
      final double diff = simplifiedProfile[i * 2 + 1] - simplifiedProfile[i * 2 - 1];
      if (diff > 0) aggAscent += diff;
      else aggDescent -= diff;
    }
    LOGGER.debug(
        "From simplified elevation profile: ascent {}m, descent {}m, length {}m",
        round(aggAscent),
        round(aggDescent),
        round(simplifiedProfile[simplifiedProfile.length - 2]));
  }

  // using the haversine formula, assumes that the Earth is a sphere
  private double computeDistanceInMeter(double dx, double dy, double dz, double lat1, double lat2) {
    final double dLon = dx * Math.PI / 180.0;
    final double dLat = dy * Math.PI / 180.0;
    final double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(lat1 * Math.PI / 180.0)
                * Math.cos(lat2 * Math.PI / 180.0)
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);
    final double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    final double dxy = R * c;
    return sqrt(dxy * dxy + dz * dz);
  }

  private double computeAngle(Position p1, Position p2) {
    return Math.atan2(
        p2.getCoordinates()[1] - p1.getCoordinates()[1],
        p2.getCoordinates()[0] - p1.getCoordinates()[0]);
  }

  private double deltaAngle(double angle1, double angle2) {
    double val = angle2 - angle1;
    if (val > Math.PI) {
      val -= 2. * Math.PI;
    }
    if (val < -Math.PI) {
      val += 2. * Math.PI;
    }
    return val;
  }

  private List<Double> computeBbox() {
    double[][] minMax =
        MultiPoint.of(overviewGeometry.stream().map(Point::of).toList())
            .accept(new MinMaxDeriver());
    return Arrays.stream(minMax).map(Arrays::stream).flatMapToDouble(v -> v).boxed().toList();
  }
}
