/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.custom.extensions.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.custom.extensions.domain.Geometry;
import de.ii.ogcapi.features.custom.extensions.domain.GeometryHelperWKT;
import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class GeometryHelperWKTImpl implements GeometryHelperWKT {

  static final String NUMBER_REGEX_NOGROUP = "[+-]?\\d+\\.?\\d*";
  static final String NUMBER_REGEX = "([+-]?\\d+\\.?\\d*)";
  static final String POSITION_REGEX_NOGROUP =
      NUMBER_REGEX_NOGROUP + "(?:\\s+" + NUMBER_REGEX_NOGROUP + "){1,2}";
  static final String POSITION_REGEX =
      "(" + NUMBER_REGEX_NOGROUP + ")(?:\\s+(" + NUMBER_REGEX_NOGROUP + ")){1,2}";
  static final String POINT_REGEX = "\\(\\s*" + POSITION_REGEX + "\\s*\\)";
  static final String MULTI_POINT_REGEX =
      "\\(\\s*("
          + POSITION_REGEX_NOGROUP
          + ")(?:\\s*\\,\\s*("
          + POSITION_REGEX_NOGROUP
          + ")\\s*)+\\)";
  static final String LINE_STRING_REGEX_NOGROUP =
      "\\(\\s*" + POSITION_REGEX_NOGROUP + "(?:\\s*\\,\\s*" + POSITION_REGEX_NOGROUP + "\\s*)+\\)";
  static final String LINE_STRING_REGEX =
      "\\(\\s*("
          + POSITION_REGEX_NOGROUP
          + ")(?:\\s*\\,\\s*("
          + POSITION_REGEX_NOGROUP
          + ")\\s*)+\\)";
  static final String MULTI_LINE_STRING_REGEX =
      "\\(\\s*("
          + LINE_STRING_REGEX_NOGROUP
          + ")\\s*(?:\\s*\\,\\s*("
          + LINE_STRING_REGEX_NOGROUP
          + ")\\s*)*\\)";
  static final String POLYGON_REGEX_NOGROUP =
      "\\(\\s*"
          + LINE_STRING_REGEX_NOGROUP
          + "\\s*(?:\\s*\\,\\s*"
          + LINE_STRING_REGEX_NOGROUP
          + "\\s*)*\\)";
  static final String POLYGON_REGEX =
      "\\(\\s*("
          + LINE_STRING_REGEX_NOGROUP
          + ")\\s*(?:\\s*\\,\\s*("
          + LINE_STRING_REGEX_NOGROUP
          + ")\\s*)*\\)";
  static final String MULTIPOLYGON_REGEX =
      "\\(\\s*("
          + POLYGON_REGEX_NOGROUP
          + ")\\s*(?:\\s*\\,\\s*("
          + POLYGON_REGEX_NOGROUP
          + ")\\s*)*\\)";
  static final Pattern numberPattern = Pattern.compile(NUMBER_REGEX);
  static final Pattern positionPattern = Pattern.compile(POSITION_REGEX);
  static final Pattern pointPattern = Pattern.compile(POINT_REGEX);
  static final Pattern lineStringPattern = Pattern.compile(LINE_STRING_REGEX);
  static final Pattern polygonPattern = Pattern.compile(POLYGON_REGEX);

  @Inject
  GeometryHelperWKTImpl() {}

  @Override
  public String getPointRegex() {
    return "^\\s*POINT\\s*" + POINT_REGEX + "\\s*$";
  }

  @Override
  public String getMultiPointRegex() {
    return "^\\s*MULTIPOINT\\s*" + MULTI_POINT_REGEX + "\\s*$";
  }

  @Override
  public String getLineStringRegex() {
    return "^\\s*LINESTRING\\s*" + LINE_STRING_REGEX + "\\s*$";
  }

  @Override
  public String getMultiLineStringRegex() {
    return "^\\s*MULTILINESTRING\\s*" + MULTI_LINE_STRING_REGEX + "\\s*$";
  }

  @Override
  public String getPolygonRegex() {
    return "^\\s*POLYGON\\s*" + POLYGON_REGEX + "\\s*$";
  }

  @Override
  public String getMultiPolygonRegex() {
    return "^\\s*MULTIPOLYGON\\s*" + MULTIPOLYGON_REGEX + "\\s*$";
  }

  @Override
  public Geometry extractGeometry(String wkt) {
    if (wkt.startsWith("MULTIPOLYGON")) return extractMultiPolygon(wkt);
    else if (wkt.startsWith("POLYGON")) return extractPolygon(wkt);
    else if (wkt.startsWith("MULTILINESTRING")) return extractMultiLineString(wkt);
    else if (wkt.startsWith("LINESTRING")) return extractLineString(wkt);
    else if (wkt.startsWith("MULTIPOINT")) return extractMultiPoint(wkt);
    else if (wkt.startsWith("POINT")) return extractPoint(wkt);

    throw new IllegalStateException(
        String.format(
            "WKT cannot be converted to a geometry, unsupported geometry type '%s'.",
            wkt.substring(0, wkt.indexOf("("))));
  }

  private Geometry.Coordinate extractPosition(String text) {
    List<Double> vector = new Vector<>();
    Matcher matcher = numberPattern.matcher(text);
    while (matcher.find()) {
      String subText = text.substring(matcher.start(), matcher.end());
      vector.add(Double.valueOf(subText));
    }
    return Geometry.Coordinate.of(vector);
  }

  private Geometry.Point extractPoint(String text) {
    Matcher matcher = positionPattern.matcher(text);
    if (matcher.find()) {
      String subText = text.substring(matcher.start(), matcher.end());
      return Geometry.Point.of(extractPosition(subText));
    }
    throw new IllegalStateException(
        String.format("Expected a point, but no coordinates were found: '%s'", text));
  }

  private Geometry.MultiPoint extractMultiPoint(String text) {
    List<Geometry.Point> vector = new Vector<>();
    Matcher matcher = positionPattern.matcher(text);
    while (matcher.find()) {
      String subText = text.substring(matcher.start(), matcher.end());
      vector.add(Geometry.Point.of(extractPosition(subText)));
    }
    return Geometry.MultiPoint.of(vector);
  }

  private Geometry.LineString extractLineString(String text) {
    List<Geometry.Coordinate> vector = new Vector<>();
    Matcher matcher = positionPattern.matcher(text);
    while (matcher.find()) {
      String subText = text.substring(matcher.start(), matcher.end());
      vector.add(extractPosition(subText));
    }
    return Geometry.LineString.of(vector);
  }

  private Geometry.MultiLineString extractMultiLineString(String text) {
    List<Geometry.LineString> vector = new Vector<>();
    Matcher matcher = lineStringPattern.matcher(text);
    while (matcher.find()) {
      String subText = text.substring(matcher.start(), matcher.end());
      vector.add(extractLineString(subText));
    }
    return Geometry.MultiLineString.of(vector);
  }

  private Geometry.Polygon extractPolygon(String text) {
    List<Geometry.LineString> vector = new Vector<>();
    Matcher matcher = lineStringPattern.matcher(text);
    while (matcher.find()) {
      String subText = text.substring(matcher.start(), matcher.end());
      vector.add(extractLineString(subText));
    }
    return Geometry.Polygon.of(vector);
  }

  private Geometry.MultiPolygon extractMultiPolygon(String text) {
    List<Geometry.Polygon> vector = new Vector<>();
    Matcher matcher = polygonPattern.matcher(text);
    while (matcher.find()) {
      String subText = text.substring(matcher.start(), matcher.end());
      vector.add(extractPolygon(subText));
    }
    return Geometry.MultiPolygon.of(vector);
  }

  /* TODO
  public Geometry.Polygon convertBboxToPolygon(String bbox) {
      List<String> ords = Splitter.on(",")
          .trimResults()
          .splitToList(bbox);
      String lowerLeft = ords.get(0) + " " + ords.get(1);
      String lowerRight = ords.get(2) + " " + ords.get(1);
      String upperLeft = ords.get(0) + " " + ords.get(3);
      String upperRight = ords.get(2) + " " + ords.get(3);
      String polygon = "((" + lowerLeft + "," + lowerRight + "," + upperRight + "," + upperLeft + "," + lowerLeft + "))";
      return extractPolygon(polygon);
  }

  @Override
  public Geometry.Polygon convertBboxToPolygon(List<Double> bbox) {
      String lowerLeft = bbox.get(0) + " " + bbox.get(1);
      String lowerRight = bbox.get(2) + " " + bbox.get(1);
      String upperLeft = bbox.get(0) + " " + bbox.get(3);
      String upperRight = bbox.get(2) + " " + bbox.get(3);
      String polygon = "((" + lowerLeft + "," + lowerRight + "," + upperRight + "," + upperLeft + "," + lowerLeft + "))";
      return extractPolygon(polygon);
  }
   */

  @Override
  public String convertGeometryToWkt(Geometry geom) {
    if (geom instanceof Geometry.MultiPolygon)
      return "MULTIPOLYGON" + convertMultiPolygonToWktCoordinates((Geometry.MultiPolygon) geom);
    else if (geom instanceof Geometry.Polygon)
      return "POLYGON" + convertPolygonToWktCoordinates((Geometry.Polygon) geom);
    else if (geom instanceof Geometry.MultiLineString)
      return "MULTILINESTRING"
          + convertMultiLineStringToWktCoordinates((Geometry.MultiLineString) geom);
    else if (geom instanceof Geometry.LineString)
      return "LINESTRING" + convertLineStringToWktCoordinates((Geometry.LineString) geom);
    else if (geom instanceof Geometry.MultiPoint)
      return "MULTIPOINT" + convertMultiPointToWktCoordinates((Geometry.MultiPoint) geom);
    else if (geom instanceof Geometry.Point)
      return "POINT" + convertPointToWktCoordinates((Geometry.Point) geom);

    throw new IllegalStateException(
        String.format(
            "Geometry cannot be converted to WKT, unsupported geometry type '%s'.",
            geom.getType().toString()));
  }

  private String convertMultiPolygonToWktCoordinates(Geometry.MultiPolygon multiPolygon) {
    List<String> polygons =
        multiPolygon.getCoordinates().stream()
            .map(this::convertPolygonToWktCoordinates)
            .collect(Collectors.toList());
    return "(" + String.join(",", polygons) + ")";
  }

  private String convertPolygonToWktCoordinates(Geometry.Polygon polygon) {
    List<String> rings =
        polygon.getCoordinates().stream()
            .map(this::convertLineStringToWktCoordinates)
            .collect(Collectors.toList());
    return "(" + String.join(",", rings) + ")";
  }

  private String convertMultiLineStringToWktCoordinates(Geometry.MultiLineString multiLineString) {
    List<String> rings =
        multiLineString.getCoordinates().stream()
            .map(this::convertLineStringToWktCoordinates)
            .collect(Collectors.toList());
    return "(" + String.join(",", rings) + ")";
  }

  private String convertLineStringToWktCoordinates(Geometry.LineString lineString) {
    List<String> coordinates =
        lineString.getCoordinates().stream()
            .map(this::convertCoordinateToWktCoordinates)
            .collect(Collectors.toList());
    return "(" + String.join(",", coordinates) + ")";
  }

  private String convertMultiPointToWktCoordinates(Geometry.MultiPoint multiPoint) {
    List<String> coordinates =
        multiPoint.getCoordinates().stream()
            .map(p -> p.getCoordinates().get(0))
            .map(this::convertCoordinateToWktCoordinates)
            .collect(Collectors.toList());
    return "(" + String.join(",", coordinates) + ")";
  }

  private String convertPointToWktCoordinates(Geometry.Point point) {
    List<String> ordinates =
        point.getCoordinates().get(0).stream()
            .filter(v -> !Double.isNaN(v))
            .map(String::valueOf)
            .collect(Collectors.toList());
    return "(" + String.join(" ", ordinates) + ")";
  }

  private String convertCoordinateToWktCoordinates(Geometry.Coordinate coord) {
    List<String> ordinates =
        coord.stream()
            .filter(v -> !Double.isNaN(v))
            .map(String::valueOf)
            .collect(Collectors.toList());
    return String.join(" ", ordinates);
  }

  public String convertGeoJsonToWkt(JsonNode geoJsonNode) {
    Geometry geom;
    JsonNode typeNode = geoJsonNode.get("type");
    if (Objects.isNull(typeNode)) {
      throw new IllegalStateException(
          "Could not parse geometry from JSON string. No 'type' property found.");
    } else if (!typeNode.isValueNode()) {
      throw new IllegalStateException(
          "Could not parse geometry from JSON string. The 'type' property is not a value.");
    } else {
      String type = typeNode.asText();
      if (type.equals("Feature") && Objects.nonNull(geoJsonNode.get("geometry"))) {
        return convertGeoJsonToWkt(geoJsonNode.get("geometry"));
      } else if (type.equals("FeatureCollection")
          && Objects.nonNull(geoJsonNode.get("features"))
          && geoJsonNode.get("features").isArray()) {
        // pick the first feature
        if (geoJsonNode.get("features").size() > 0)
          return convertGeoJsonToWkt(geoJsonNode.get("features").get(0));
        else
          throw new IllegalStateException(
              "Could not parse geometry from GeoJSON string. Empty feature collection.");
      } else if (type.equals("Point")) {
        geom = this.createPoint(geoJsonNode.get("coordinates"));
      } else if (type.equals("LineString")) {
        geom = this.createLineString(geoJsonNode.get("coordinates"));
      } else if (type.equals("Polygon")) {
        geom = this.createPolygon(geoJsonNode.get("coordinates"));
      } else if (type.equals("MultiPoint")) {
        geom = this.createMultiPoint(geoJsonNode.get("coordinates"));
      } else if (type.equals("MultiLineString")) {
        geom = this.createMultiLineString(geoJsonNode.get("coordinates"));
      } else if (type.equals("MultiPolygon")) {
        geom = this.createMultiPolygon(geoJsonNode.get("coordinates"));
      } else {
        throw new IllegalStateException(
            String.format(
                "Could not parse geometry from GeoJSON string.  Unsupported 'type': %s.", type));
      }

      return convertGeometryToWkt(geom);
    }
  }

  private Geometry.MultiPolygon createMultiPolygon(JsonNode geometryNode) {
    return Geometry.MultiPolygon.of(
        ImmutableList.copyOf(geometryNode.elements()).stream()
            .map(this::createPolygon)
            .collect(ImmutableList.toImmutableList()));
  }

  private Geometry.Polygon createPolygon(JsonNode geometryNode) {
    return Geometry.Polygon.of(
        ImmutableList.copyOf(geometryNode.elements()).stream()
            .map(this::createLineString)
            .collect(ImmutableList.toImmutableList()));
  }

  private Geometry.MultiLineString createMultiLineString(JsonNode geometryNode) {
    return Geometry.MultiLineString.of(
        ImmutableList.copyOf(geometryNode.elements()).stream()
            .map(this::createLineString)
            .collect(ImmutableList.toImmutableList()));
  }

  private Geometry.LineString createLineString(JsonNode geometryNode) {
    return Geometry.LineString.of(
        ImmutableList.copyOf(geometryNode.elements()).stream()
            .map(this::createCoordinate)
            .collect(ImmutableList.toImmutableList()));
  }

  private Geometry.MultiPoint createMultiPoint(JsonNode geometryNode) {
    return Geometry.MultiPoint.of(
        ImmutableList.copyOf(geometryNode.elements()).stream()
            .map(this::createCoordinate)
            .map(Geometry.Point::of)
            .collect(ImmutableList.toImmutableList()));
  }

  private Geometry.Point createPoint(JsonNode geometryNode) {
    return Geometry.Point.of(
        ImmutableList.copyOf(geometryNode.elements()).stream()
            .map(JsonNode::asDouble)
            .collect(ImmutableList.toImmutableList()));
  }

  private Geometry.Coordinate createCoordinate(JsonNode positionNode) {
    return Geometry.Coordinate.of(
        ImmutableList.copyOf(positionNode.elements()).stream()
            .map(JsonNode::asDouble)
            .collect(ImmutableList.toImmutableList()));
  }
}
