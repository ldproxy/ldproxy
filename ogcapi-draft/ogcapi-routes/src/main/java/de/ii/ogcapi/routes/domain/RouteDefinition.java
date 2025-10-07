/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.routes.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.geometries.domain.ImmutableMultiPolygon;
import de.ii.xtraplatform.geometries.domain.ImmutablePolygon;
import de.ii.xtraplatform.geometries.domain.LineString;
import de.ii.xtraplatform.geometries.domain.MultiPolygon;
import de.ii.xtraplatform.geometries.domain.Point;
import de.ii.xtraplatform.geometries.domain.Position;
import de.ii.xtraplatform.geometries.domain.PositionList;
import de.ii.xtraplatform.values.domain.StoredValue;
import de.ii.xtraplatform.values.domain.ValueBuilder;
import de.ii.xtraplatform.values.domain.annotations.FromValueStore;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true, deepImmutablesDetection = true, builder = "new")
@FromValueStore(type = "routes/definitions")
@JsonDeserialize(builder = ImmutableRouteDefinition.Builder.class)
public interface RouteDefinition extends StoredValue {

  String SCHEMA_REF = "#/components/schemas/RouteDefinition";

  RouteDefinitionInputs getInputs();

  List<Link> getLinks();

  @JsonIgnore
  @Value.Derived
  default List<List<Float>> getPoints() {
    return getInputs().getWaypoints().getValue().getCoordinates();
  }

  @JsonIgnore
  @Value.Derived
  default EpsgCrs getWaypointsCrs() {
    try {
      return EpsgCrs.fromString(getInputs().getWaypoints().getValue().getCoordRefSys());
    } catch (Throwable e) {
      throw new IllegalArgumentException(
          String.format(
              "The value of 'coordRefSys' in the route definition is invalid: %s", e.getMessage()),
          e);
    }
  }

  @JsonIgnore
  @Value.Derived
  default Point getStart() {
    return processWaypoint(getPoints().get(0), getWaypointsCrs());
  }

  @JsonIgnore
  @Value.Derived
  default Point getEnd() {
    List<List<Float>> waypoints = getPoints();
    return processWaypoint(waypoints.get(waypoints.size() - 1), getWaypointsCrs());
  }

  @JsonIgnore
  @Value.Derived
  default List<Point> getWaypoints() {
    List<List<Float>> waypoints = getPoints();
    EpsgCrs crs = getWaypointsCrs();

    if (waypoints.size() <= 2) {
      return ImmutableList.of();
    }

    return IntStream.range(1, waypoints.size() - 1)
        .mapToObj(i -> processWaypoint(waypoints.get(i), crs))
        .toList();
  }

  @JsonIgnore
  @Value.Derived
  default Optional<MultiPolygon> getObstacles() {
    Optional<Obstacles> obstacles = getInputs().getObstacles();
    if (obstacles.isEmpty()) return Optional.empty();

    EpsgCrs crs;
    try {
      crs = EpsgCrs.fromString(obstacles.get().getValue().getCoordRefSys());
    } catch (Throwable e) {
      throw new IllegalArgumentException(
          String.format(
              "The value of 'coordRefSys' in the route definition is invalid: %s", e.getMessage()),
          e);
    }

    ImmutableMultiPolygon.Builder builder = ImmutableMultiPolygon.builder().crs(crs);

    obstacles
        .get()
        .getValue()
        .getCoordinates()
        .forEach(
            polygon ->
                builder.addValue(
                    ImmutablePolygon.builder()
                        .crs(crs)
                        .value(
                            polygon.stream()
                                .map(
                                    ring ->
                                        PositionList.of(
                                            ring.stream()
                                                .map(RouteDefinition::processPosition)
                                                .toList()))
                                .map(posList -> LineString.of(posList, Optional.of(crs)))
                                .toList())
                        .build()));

    return Optional.of(builder.build());
  }

  static Point processWaypoint(List<Float> coord, EpsgCrs crs) {
    if (coord.size() == 2) {
      return Point.of(coord.get(0), coord.get(1), crs);
    } else {
      return Point.of(
          Position.ofXYZ(
              coord.get(0).doubleValue(), coord.get(1).doubleValue(), coord.get(2).doubleValue()),
          Optional.of(crs));
    }
  }

  static Position processPosition(List<Float> coord) {
    if (coord.size() == 2) {
      return Position.ofXY(coord.get(0), coord.get(1));
    } else {
      return Position.ofXYZ(
          coord.get(0).doubleValue(), coord.get(1).doubleValue(), coord.get(2).doubleValue());
    }
  }

  abstract class Builder implements ValueBuilder<RouteDefinition> {}

  @SuppressWarnings("UnstableApiUsage")
  Funnel<RouteDefinition> FUNNEL =
      (from, into) -> {
        RouteDefinitionInputs.FUNNEL.funnel(from.getInputs(), into);
      };
}
