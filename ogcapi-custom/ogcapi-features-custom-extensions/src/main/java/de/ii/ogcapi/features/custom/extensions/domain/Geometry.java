/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.custom.extensions.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

public interface Geometry<T> {

  // This will not be migrated to new Geometry interface, since the whole building block is
  // deprecated and will be removed in v5.0.

  enum Type {
    Point,
    LineString,
    Polygon,
    MultiPoint,
    MultiLineString,
    MultiPolygon
  }

  Type getType();

  List<T> getCoordinates();

  @JsonProperty("coordRefSys")
  Optional<EpsgCrs> getCrs();

  List<Coordinate> getCoordinatesFlat();

  boolean is3d();

  @Value.Immutable
  @JsonDeserialize(builder = ImmutablePoint.Builder.class)
  interface Point extends Geometry<Coordinate> {

    static Point of(double x, double y) {
      return ImmutablePoint.builder().addCoordinates(Coordinate.of(x, y)).build();
    }

    static Point of(double x, double y, double z) {
      return ImmutablePoint.builder().addCoordinates(Coordinate.of(x, y, z)).build();
    }

    static Point of(List<Double> xyz) {
      if (xyz.size() == 2) return Point.of(xyz.get(0), xyz.get(1));
      else if (xyz.size() == 3) return Point.of(xyz.get(0), xyz.get(1), xyz.get(2));
      throw new IllegalArgumentException(
          String.format("A coordinate requires 2 or 3 values. Found: %s", xyz));
    }

    static Point of(Coordinate coordinate) {
      return ImmutablePoint.builder().addCoordinates(coordinate).build();
    }

    @Override
    @JsonIgnore
    @Value.Derived
    @Value.Auxiliary
    default List<Coordinate> getCoordinatesFlat() {
      return getCoordinates();
    }

    @Override
    @JsonIgnore
    @Value.Derived
    @Value.Auxiliary
    default boolean is3d() {
      return getCoordinates().get(0).size() == 3;
    }

    @Value.Check
    default void check() {
      Preconditions.checkState(
          getCoordinates().size() == 1,
          "A point must have only one coordinate, found %d coordinates.",
          getCoordinates().size());
    }

    @Override
    default Type getType() {
      return Type.Point;
    }
  }

  @Value.Immutable
  @JsonDeserialize(builder = ImmutableLineString.Builder.class)
  interface LineString extends Geometry<Coordinate> {

    static LineString of(Coordinate... coordinates) {
      return ImmutableLineString.builder().addCoordinates(coordinates).build();
    }

    static LineString of(List<Coordinate> coordinates) {
      return ImmutableLineString.builder().addAllCoordinates(coordinates).build();
    }

    @Override
    @JsonIgnore
    @Value.Derived
    @Value.Auxiliary
    default List<Coordinate> getCoordinatesFlat() {
      return getCoordinates();
    }

    @Override
    @JsonIgnore
    @Value.Derived
    @Value.Auxiliary
    default boolean is3d() {
      return getCoordinates().get(0).size() == 3;
    }

    @Value.Check
    default void check() {
      Preconditions.checkState(
          getCoordinates().size() > 1,
          "A line string must have at least two coordinates, found %d coordiantes.",
          getCoordinates().size());
      List<Coordinate> coords = getCoordinatesFlat();
      Preconditions.checkState(
          coords.stream().skip(1).allMatch(c -> c.size() == coords.get(0).size()),
          "The first coordinate has dimension %d, but at least one other coordinate has a different dimension.",
          coords.size());
    }

    @Override
    default Type getType() {
      return Type.LineString;
    }
  }

  @Value.Immutable
  @JsonDeserialize(builder = ImmutablePolygon.Builder.class)
  interface Polygon extends Geometry<LineString> {

    static Polygon of(LineString... rings) {
      return ImmutablePolygon.builder().addCoordinates(rings).build();
    }

    static Polygon of(EpsgCrs crs, LineString... rings) {
      return ImmutablePolygon.builder().crs(crs).addCoordinates(rings).build();
    }

    static Polygon of(List<LineString> rings) {
      return ImmutablePolygon.builder().addAllCoordinates(rings).build();
    }

    static Polygon of(EpsgCrs crs, List<LineString> rings) {
      return ImmutablePolygon.builder().crs(crs).addAllCoordinates(rings).build();
    }

    @Override
    @JsonIgnore
    @Value.Derived
    @Value.Auxiliary
    default List<Coordinate> getCoordinatesFlat() {
      return getCoordinates().stream().map(Geometry::getCoordinates).flatMap(List::stream).toList();
    }

    @Override
    @JsonIgnore
    @Value.Derived
    @Value.Auxiliary
    default boolean is3d() {
      return getCoordinates().get(0).getCoordinates().get(0).size() == 3;
    }

    @Value.Check
    default void check() {
      Preconditions.checkState(
          !getCoordinates().isEmpty(),
          "A polygon must have at least an outer ring, no ring was found.");
      getCoordinates()
          .forEach(
              ring ->
                  Preconditions.checkState(
                      ring.getCoordinates().size() > 3,
                      "Each ring must have at least 4 coordinates, found: %d.",
                      ring.getCoordinates().size()));
      List<Coordinate> coords = getCoordinatesFlat();
      Preconditions.checkState(
          coords.stream().skip(1).allMatch(c -> c.size() == coords.get(0).size()),
          "The first coordinate has dimension %d, but at least one other coordinate has a different dimension.",
          coords.size());
    }

    @Override
    default Type getType() {
      return Type.Polygon;
    }
  }

  @Value.Immutable
  @JsonDeserialize(builder = ImmutableMultiPoint.Builder.class)
  interface MultiPoint extends Geometry<Point> {

    static MultiPoint of(Point... points) {
      return ImmutableMultiPoint.builder().addCoordinates(points).build();
    }

    static MultiPoint of(List<Point> points) {
      return ImmutableMultiPoint.builder().addAllCoordinates(points).build();
    }

    @Override
    @JsonIgnore
    @Value.Derived
    @Value.Auxiliary
    default List<Coordinate> getCoordinatesFlat() {
      return getCoordinates().stream().map(Geometry::getCoordinates).flatMap(List::stream).toList();
    }

    @Override
    @JsonIgnore
    @Value.Derived
    @Value.Auxiliary
    default boolean is3d() {
      return !getCoordinates().isEmpty() && getCoordinates().get(0).is3d();
    }

    @Value.Check
    default void check() {
      List<Coordinate> coords = getCoordinatesFlat();
      Preconditions.checkState(
          coords.stream().skip(1).allMatch(c -> c.size() == coords.get(0).size()),
          "The first coordinate has dimension %d, but at least one other coordinate has a different dimension.",
          coords.size());
    }

    @Override
    default Type getType() {
      return Type.MultiPoint;
    }
  }

  @Value.Immutable
  @JsonDeserialize(builder = ImmutableMultiLineString.Builder.class)
  interface MultiLineString extends Geometry<LineString> {

    static MultiLineString of(LineString... lineStrings) {
      return ImmutableMultiLineString.builder().addCoordinates(lineStrings).build();
    }

    static MultiLineString of(List<LineString> lineStrings) {
      return ImmutableMultiLineString.builder().addAllCoordinates(lineStrings).build();
    }

    @Override
    @JsonIgnore
    @Value.Derived
    @Value.Auxiliary
    default List<Coordinate> getCoordinatesFlat() {
      return getCoordinates().stream().map(Geometry::getCoordinates).flatMap(List::stream).toList();
    }

    @Override
    @JsonIgnore
    @Value.Derived
    @Value.Auxiliary
    default boolean is3d() {
      return !getCoordinates().isEmpty() && getCoordinates().get(0).is3d();
    }

    @Value.Check
    default void check() {
      List<Coordinate> coords = getCoordinatesFlat();
      Preconditions.checkState(
          coords.stream().skip(1).allMatch(c -> c.size() == coords.get(0).size()),
          "The first coordinate has dimension %d, but at least one other coordinate has a different dimension.",
          coords.size());
    }

    @Override
    default Type getType() {
      return Type.MultiLineString;
    }
  }

  @Value.Immutable
  @JsonDeserialize(builder = ImmutableMultiPolygon.Builder.class)
  interface MultiPolygon extends Geometry<Polygon> {

    static MultiPolygon of(Polygon... polygons) {
      return ImmutableMultiPolygon.builder().addCoordinates(polygons).build();
    }

    static MultiPolygon of(List<Polygon> polygons) {
      return ImmutableMultiPolygon.builder().addAllCoordinates(polygons).build();
    }

    @Override
    @JsonIgnore
    @Value.Derived
    @Value.Auxiliary
    default List<Coordinate> getCoordinatesFlat() {
      return getCoordinates().stream()
          .map(Geometry::getCoordinates)
          .flatMap(List::stream)
          .map(Geometry::getCoordinates)
          .flatMap(List::stream)
          .toList();
    }

    @Override
    @JsonIgnore
    @Value.Derived
    @Value.Auxiliary
    default boolean is3d() {
      return !getCoordinates().isEmpty() && getCoordinates().get(0).is3d();
    }

    @Value.Check
    default void check() {
      List<Coordinate> coords = getCoordinatesFlat();
      Preconditions.checkState(
          coords.stream().skip(1).allMatch(c -> c.size() == coords.get(0).size()),
          "The first coordinate has dimension %d, but at least one other coordinate has a different dimension.",
          coords.size());
    }

    @Override
    default Type getType() {
      return Type.MultiPolygon;
    }
  }

  class Coordinate extends ArrayList<Double> {

    public static Coordinate of(double x, double y) {
      return new Coordinate(x, y);
    }

    public static Coordinate of(double x, double y, double z) {
      return new Coordinate(x, y, z);
    }

    public static Coordinate of(List<Double> xyz) {
      if (xyz.size() == 2) return new Coordinate(xyz.get(0), xyz.get(1));
      else if (xyz.size() == 3) return new Coordinate(xyz.get(0), xyz.get(1), xyz.get(2));
      throw new IllegalArgumentException(
          String.format("A coordinate requires 2 or 3 values. Found: %s", xyz));
    }

    public Coordinate(double x, double y) {
      super();
      add(x);
      add(y);
    }

    public Coordinate(double x, double y, double z) {
      this(x, y);
      add(z);
    }
  }
}
