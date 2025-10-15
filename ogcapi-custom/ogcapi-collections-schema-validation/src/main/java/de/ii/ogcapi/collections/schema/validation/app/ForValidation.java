/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.schema.validation.app;

import com.google.common.collect.ImmutableMap;
import de.ii.xtraplatform.base.domain.util.Tuple;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaArray;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaConstant;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaDocument;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaFalse;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaInteger;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaNumber;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaObject;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaOneOf;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaString;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaArray;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaBuildingBlocks;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaDocument;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaGeometry;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaInteger;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaObject;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaString;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaVisitor;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

public class ForValidation implements JsonSchemaVisitor {

  private static final JsonSchemaArray POSITION =
      new ImmutableJsonSchemaArray.Builder()
          .minItems(2)
          .maxItems(3) // increase to 4 when measures are supported
          .items(new ImmutableJsonSchemaNumber.Builder().build())
          .build();
  private static final JsonSchemaObject POINT =
      new ImmutableJsonSchemaObject.Builder()
          .addRequired("type", "coordinates")
          .putProperties(
              "type", new ImmutableJsonSchemaConstant.Builder().constant("Point").build())
          .putProperties("coordinates", POSITION)
          .additionalProperties(ImmutableJsonSchemaFalse.builder().build())
          .build();
  private static final JsonSchemaObject LINESTRING =
      new ImmutableJsonSchemaObject.Builder()
          .addRequired("type", "coordinates")
          .putProperties(
              "type", new ImmutableJsonSchemaConstant.Builder().constant("LineString").build())
          .putProperties(
              "coordinates",
              new ImmutableJsonSchemaArray.Builder().minItems(2).items(POSITION).build())
          .additionalProperties(ImmutableJsonSchemaFalse.builder().build())
          .build();
  private static final JsonSchemaObject CIRCULARSTRING =
      new ImmutableJsonSchemaObject.Builder()
          .addRequired("type", "coordinates")
          .putProperties(
              "type", new ImmutableJsonSchemaConstant.Builder().constant("CircularString").build())
          .putProperties(
              "coordinates",
              new ImmutableJsonSchemaOneOf.Builder()
                  .addOneOf(
                      new ImmutableJsonSchemaArray.Builder()
                          .minItems(3)
                          .maxItems(3)
                          .items(POSITION)
                          .build(),
                      new ImmutableJsonSchemaArray.Builder()
                          .minItems(5)
                          .maxItems(5)
                          .items(POSITION)
                          .build(),
                      new ImmutableJsonSchemaArray.Builder()
                          .minItems(7)
                          .maxItems(7)
                          .items(POSITION)
                          .build(),
                      new ImmutableJsonSchemaArray.Builder()
                          .minItems(9)
                          .maxItems(9)
                          .items(POSITION)
                          .build(),
                      new ImmutableJsonSchemaArray.Builder()
                          .minItems(11)
                          .maxItems(11)
                          .items(POSITION)
                          .build())
                  .build())
          .additionalProperties(ImmutableJsonSchemaFalse.builder().build())
          .build();
  private static final JsonSchemaObject POLYGON =
      new ImmutableJsonSchemaObject.Builder()
          .addRequired("type", "coordinates")
          .putProperties(
              "type", new ImmutableJsonSchemaConstant.Builder().constant("Polygon").build())
          .putProperties(
              "coordinates",
              new ImmutableJsonSchemaArray.Builder()
                  .items(new ImmutableJsonSchemaArray.Builder().minItems(4).items(POSITION).build())
                  .build())
          .additionalProperties(ImmutableJsonSchemaFalse.builder().build())
          .build();
  private static final JsonSchemaObject MULTIPOINT =
      new ImmutableJsonSchemaObject.Builder()
          .addRequired("type", "coordinates")
          .putProperties(
              "type", new ImmutableJsonSchemaConstant.Builder().constant("MultiPoint").build())
          .putProperties(
              "coordinates", new ImmutableJsonSchemaArray.Builder().items(POSITION).build())
          .additionalProperties(ImmutableJsonSchemaFalse.builder().build())
          .build();
  private static final JsonSchemaObject MULTILINESTRING =
      new ImmutableJsonSchemaObject.Builder()
          .addRequired("type", "coordinates")
          .putProperties(
              "type", new ImmutableJsonSchemaConstant.Builder().constant("MultiLineString").build())
          .putProperties(
              "coordinates",
              new ImmutableJsonSchemaArray.Builder()
                  .items(new ImmutableJsonSchemaArray.Builder().minItems(2).items(POSITION).build())
                  .build())
          .additionalProperties(ImmutableJsonSchemaFalse.builder().build())
          .build();
  private static final JsonSchemaObject MULTIPOLYGON =
      new ImmutableJsonSchemaObject.Builder()
          .addRequired("type", "coordinates")
          .putProperties(
              "type", new ImmutableJsonSchemaConstant.Builder().constant("MultiPolygon").build())
          .putProperties(
              "coordinates",
              new ImmutableJsonSchemaArray.Builder()
                  .items(
                      new ImmutableJsonSchemaArray.Builder()
                          .items(
                              new ImmutableJsonSchemaArray.Builder()
                                  .minItems(4)
                                  .items(POSITION)
                                  .build())
                          .build())
                  .build())
          .additionalProperties(ImmutableJsonSchemaFalse.builder().build())
          .build();
  private static final JsonSchemaObject COMPOUNDCURVE =
      new ImmutableJsonSchemaObject.Builder()
          .addRequired("type", "geometry")
          .putProperties(
              "type", new ImmutableJsonSchemaConstant.Builder().constant("CompoundCurve").build())
          .putProperties(
              "geometries",
              new ImmutableJsonSchemaArray.Builder()
                  .minItems(1)
                  .items(
                      new ImmutableJsonSchemaOneOf.Builder()
                          .addOneOf(LINESTRING, CIRCULARSTRING)
                          .build())
                  .build())
          .additionalProperties(ImmutableJsonSchemaFalse.builder().build())
          .build();
  private static final JsonSchemaObject MULTICURVE =
      new ImmutableJsonSchemaObject.Builder()
          .addRequired("type", "geometry")
          .putProperties(
              "type", new ImmutableJsonSchemaConstant.Builder().constant("MultiCurve").build())
          .putProperties(
              "geometries",
              new ImmutableJsonSchemaArray.Builder()
                  .items(
                      new ImmutableJsonSchemaOneOf.Builder()
                          .addOneOf(LINESTRING, CIRCULARSTRING, COMPOUNDCURVE)
                          .build())
                  .build())
          .additionalProperties(ImmutableJsonSchemaFalse.builder().build())
          .build();
  private static final JsonSchemaObject CURVEPOLYGON =
      new ImmutableJsonSchemaObject.Builder()
          .addRequired("type", "geometry")
          .putProperties(
              "type", new ImmutableJsonSchemaConstant.Builder().constant("CurvePolygon").build())
          .putProperties(
              "geometries",
              new ImmutableJsonSchemaArray.Builder()
                  .minItems(1)
                  .items(
                      new ImmutableJsonSchemaOneOf.Builder()
                          .addOneOf(LINESTRING, CIRCULARSTRING, COMPOUNDCURVE)
                          .build())
                  .build())
          .additionalProperties(ImmutableJsonSchemaFalse.builder().build())
          .build();
  private static final JsonSchemaObject MULTISURFACE =
      new ImmutableJsonSchemaObject.Builder()
          .addRequired("type", "geometry")
          .putProperties(
              "type", new ImmutableJsonSchemaConstant.Builder().constant("MultiSurface").build())
          .putProperties(
              "geometries",
              new ImmutableJsonSchemaArray.Builder()
                  .items(
                      new ImmutableJsonSchemaOneOf.Builder()
                          .addOneOf(POLYGON, CURVEPOLYGON)
                          .build())
                  .build())
          .additionalProperties(ImmutableJsonSchemaFalse.builder().build())
          .build();
  private static final JsonSchemaObject GEOMETRYCOLLECTION =
      new ImmutableJsonSchemaObject.Builder()
          .addRequired("type", "geometry")
          .putProperties(
              "type",
              new ImmutableJsonSchemaConstant.Builder().constant("GeometryCollection").build())
          .putProperties(
              "geometries",
              new ImmutableJsonSchemaArray.Builder()
                  .items(
                      new ImmutableJsonSchemaOneOf.Builder()
                          .addOneOf(
                              POINT, LINESTRING, POLYGON, MULTIPOINT, MULTILINESTRING, MULTIPOLYGON)
                          .build())
                  .build())
          .additionalProperties(ImmutableJsonSchemaFalse.builder().build())
          .build();

  private final ProfileJsonSchemaForValidation profile;

  public ForValidation(ProfileJsonSchemaForValidation profile) {
    this.profile = profile;
  }

  @Override
  public JsonSchema visit(JsonSchema schema) {
    if (schema instanceof JsonSchemaDocument document) {
      ImmutableJsonSchemaDocument.Builder builder =
          ImmutableJsonSchemaDocument.builder()
              .schema(document.getSchema())
              .id(document.getId().map(id -> String.format("%s?profile=%s", id, profile.getId())))
              .addRequired("type", "geometry", "properties")
              .putProperties(
                  "type", new ImmutableJsonSchemaConstant.Builder().constant("Feature").build());

      document.getProperties().values().stream()
          .filter(
              property ->
                  property.getRole().filter("id"::equals).isPresent()
                      && !profile.skipProperty(property))
          .findFirst()
          .ifPresent(
              idProperty ->
                  builder.putProperties(
                      "id",
                      idProperty instanceof JsonSchemaInteger
                          ? new ImmutableJsonSchemaInteger.Builder().from(idProperty).build()
                          : new ImmutableJsonSchemaString.Builder().from(idProperty).build()));

      if (profile.supportJsonFgExtensions()) {
        builder
            .putProperties(
                "conformsTo",
                new ImmutableJsonSchemaArray.Builder()
                    .items(new ImmutableJsonSchemaString.Builder().format("uri").build())
                    .build())
            .putProperties("featureType", new ImmutableJsonSchemaString.Builder().build())
            .putProperties(
                "featureSchema",
                new ImmutableJsonSchemaOneOf.Builder()
                    .addOneOf(new ImmutableJsonSchemaString.Builder().format("uri").build())
                    .addOneOf(
                        new ImmutableJsonSchemaObject.Builder()
                            .additionalProperties(
                                new ImmutableJsonSchemaString.Builder().format("uri").build())
                            .build())
                    .build())
            .putProperties(
                "coordRefSys", new ImmutableJsonSchemaString.Builder().format("uri").build());
        getTemporalPropertyByRole(document, "primary-instant")
            .ifPresentOrElse(
                instant -> builder.putProperties("time", instant(instant.second())),
                () -> {
                  Optional<Tuple<String, JsonSchemaString>> start =
                      getTemporalPropertyByRole(document, "primary-interval-start");
                  Optional<Tuple<String, JsonSchemaString>> end =
                      getTemporalPropertyByRole(document, "primary-interval-end");
                  if (start.isPresent() || end.isPresent()) {
                    builder.putProperties(
                        "time",
                        new ImmutableJsonSchemaObject.Builder()
                            .putProperties(
                                "interval",
                                new ImmutableJsonSchemaArray.Builder()
                                    .minItems(2)
                                    .maxItems(2)
                                    .prefixItems(
                                        List.of(
                                            getIntervalEnd(document, start),
                                            getIntervalEnd(document, end)))
                                    .build())
                            .build());
                  }
                });
        getSpatialPropertyByRole(document, "primary-geometry")
            .ifPresentOrElse(
                // handle all combinations of presence/absence/null of place and geometry
                geometry -> {
                  builder.addAnyOf(
                      new ImmutableJsonSchemaObject.Builder()
                          .addRequired("place", "geometry")
                          .putProperties("place", getGeometryType(geometry.second(), false))
                          .putProperties("geometry", JsonSchemaBuildingBlocks.NULL)
                          .build());
                  builder.addAnyOf(
                      new ImmutableJsonSchemaObject.Builder()
                          .addRequired("geometry")
                          .putProperties("geometry", getGeometryType(geometry.second(), true))
                          .not(new ImmutableJsonSchemaObject.Builder().addRequired("place").build())
                          .build());
                  builder.addAnyOf(
                      new ImmutableJsonSchemaObject.Builder()
                          .addRequired("geometry")
                          .putProperties("place", JsonSchemaBuildingBlocks.NULL)
                          .putProperties("geometry", getGeometryType(geometry.second(), true))
                          .build());
                  if (!geometry.second().isRequired()) {
                    builder.addAnyOf(
                        new ImmutableJsonSchemaObject.Builder()
                            .addRequired("geometry")
                            .putProperties("place", JsonSchemaBuildingBlocks.NULL)
                            .putProperties("geometry", JsonSchemaBuildingBlocks.NULL)
                            .build());
                  }
                },
                () -> {
                  builder.putProperties("geometry", JsonSchemaBuildingBlocks.NULL);
                });
      } else {
        getSpatialPropertyByRole(document, "primary-geometry")
            .ifPresentOrElse(
                geometry -> {
                  if (geometry.second().isRequired()) {
                    builder.putProperties("geometry", getGeometryType(geometry.second(), true));
                  } else {
                    builder.putProperties(
                        "geometry",
                        new ImmutableJsonSchemaOneOf.Builder()
                            .addOneOf(getGeometryType(geometry.second(), true))
                            .addOneOf(JsonSchemaBuildingBlocks.NULL)
                            .build());
                  }
                },
                () -> {
                  builder.putProperties("geometry", JsonSchemaBuildingBlocks.NULL);
                });
      }

      builder
          .putProperties(
              "properties",
              new ImmutableJsonSchemaObject.Builder()
                  .properties(processProperties(document.getProperties()))
                  .patternProperties(processSchemaMap(document.getPatternProperties()))
                  .additionalProperties(ImmutableJsonSchemaFalse.builder().build())
                  .build())
          .definitions(processSchemaMap(document.getDefinitions()));

      return builder.build();
    }

    if (profile.skipProperty(schema)) {
      return null;
    }

    if (schema.getRole().filter("reference"::equals).isPresent()) {
      return profile.getReference(schema);
    }

    if (schema instanceof JsonSchemaObject obj) {
      return visitProperties(
          new ImmutableJsonSchemaObject.Builder()
              .from(obj)
              .additionalProperties(ImmutableJsonSchemaFalse.builder().build())
              .build());
    }

    return visitProperties(schema);
  }

  private JsonSchema getGeometryType(JsonSchemaGeometry geometry, boolean simpleFeatures) {
    String format = geometry.getFormat().substring(9); // Remove "geometry-"
    return switch (format) {
      case "point" -> POINT;
      case "multipoint" -> MULTIPOINT;
      case "point-or-multipoint" -> new ImmutableJsonSchemaOneOf.Builder()
          .addOneOf(POINT, MULTIPOINT)
          .build();
      case "linestring" -> LINESTRING;
      case "circularstring" -> simpleFeatures ? LINESTRING : CIRCULARSTRING;
      case "curve" -> simpleFeatures
          ? LINESTRING
          : new ImmutableJsonSchemaOneOf.Builder()
              .addOneOf(LINESTRING, CIRCULARSTRING, COMPOUNDCURVE)
              .build();
      case "compoundcurve" -> simpleFeatures ? LINESTRING : COMPOUNDCURVE;
      case "multilinestring" -> MULTILINESTRING;
      case "linestring-or-multilinestring" -> new ImmutableJsonSchemaOneOf.Builder()
          .addOneOf(LINESTRING, MULTILINESTRING)
          .build();
      case "polygon" -> POLYGON;
      case "multipolygon", "polyhedralsurface" -> MULTIPOLYGON;
      case "polygon-or-multipolygon" -> new ImmutableJsonSchemaOneOf.Builder()
          .addOneOf(POLYGON, MULTIPOLYGON)
          .build();
      case "curvepolygon" -> simpleFeatures ? POLYGON : CURVEPOLYGON;
      case "surface" -> simpleFeatures
          ? POLYGON
          : new ImmutableJsonSchemaOneOf.Builder().addOneOf(POLYGON, CURVEPOLYGON).build();
      case "multicurve" -> simpleFeatures ? MULTILINESTRING : MULTICURVE;
      case "multisurface" -> simpleFeatures ? MULTIPOLYGON : MULTISURFACE;
        // case "polyhedron" -> POLYHEDRON;
        // case "multipolyhedron" -> MULTIPOLYHEDRON;
        // case "polyhedron-or-multipolyhedron" -> new
        // ImmutableJsonSchemaOneOf.Builder().addOneOf(POLYHEDRON, MULTIPOLYHEDRON).build();
      case "geometrycollection" -> GEOMETRYCOLLECTION;
      case "any" -> simpleFeatures
          ? new ImmutableJsonSchemaOneOf.Builder()
              .addOneOf(
                  POINT,
                  MULTIPOINT,
                  LINESTRING,
                  MULTILINESTRING,
                  POLYGON,
                  MULTIPOLYGON,
                  GEOMETRYCOLLECTION)
              .build()
          : new ImmutableJsonSchemaOneOf.Builder()
              .addOneOf(
                  POINT,
                  MULTIPOINT,
                  LINESTRING,
                  CIRCULARSTRING,
                  COMPOUNDCURVE,
                  MULTILINESTRING,
                  MULTICURVE,
                  POLYGON,
                  CURVEPOLYGON,
                  MULTIPOLYGON,
                  MULTISURFACE,
                  GEOMETRYCOLLECTION)
              .build();
      default -> throw new IllegalStateException(
          "Unexpected format value: " + geometry.getFormat());
    };
  }

  private JsonSchema getIntervalEnd(
      JsonSchemaDocument document, Optional<Tuple<String, JsonSchemaString>> startOrEnd) {
    return startOrEnd
        .map(s -> instant(s.second()))
        .map(
            instant -> {
              if (document.getRequired().contains(startOrEnd.get().first())) {
                return new ImmutableJsonSchemaOneOf.Builder()
                    .addOneOf(
                        instant, new ImmutableJsonSchemaConstant.Builder().constant("..").build())
                    .build();
              }
              return instant;
            })
        .orElse(new ImmutableJsonSchemaConstant.Builder().constant("..").build());
  }

  private static Optional<Tuple<String, JsonSchemaGeometry>> getSpatialPropertyByRole(
      JsonSchemaDocument document, String role) {
    return document.getProperties().entrySet().stream()
        .filter(entry -> entry.getValue().getRole().filter(role::equals).isPresent())
        .filter(entry -> entry.getValue() instanceof JsonSchemaGeometry)
        .map(entry -> Tuple.of(entry.getKey(), (JsonSchemaGeometry) entry.getValue()))
        .findFirst();
  }

  private static Optional<Tuple<String, JsonSchemaString>> getTemporalPropertyByRole(
      JsonSchemaDocument document, String role) {
    return document.getProperties().entrySet().stream()
        .filter(entry -> entry.getValue().getRole().filter(role::equals).isPresent())
        .filter(entry -> entry.getValue() instanceof JsonSchemaString)
        .map(entry -> Tuple.of(entry.getKey(), (JsonSchemaString) entry.getValue()))
        .findFirst();
  }

  private JsonSchema instant(JsonSchemaString instant) {
    if (instant.getFormat().filter("date"::equals).isPresent()) {
      return new ImmutableJsonSchemaObject.Builder()
          .putProperties(
              "date",
              new ImmutableJsonSchemaString.Builder().pattern("^\\d{4}-\\d{2}-\\d{2}$").build())
          .required(List.of("date"))
          .additionalProperties(ImmutableJsonSchemaFalse.builder().build())
          .build();
    }
    return new ImmutableJsonSchemaObject.Builder()
        .putProperties(
            "timestamp",
            new ImmutableJsonSchemaString.Builder()
                .pattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z$")
                .build())
        .required(List.of("timestamp"))
        .additionalProperties(ImmutableJsonSchemaFalse.builder().build())
        .build();
  }

  public Map<String, JsonSchema> processProperties(Map<String, JsonSchema> properties) {
    return processSchemaMap(
        properties.entrySet().stream()
            .filter(
                entry -> {
                  Optional<String> role = entry.getValue().getRole();
                  return role.isEmpty()
                      || role.filter(r -> "id".equals(r) || "primary-geometry".equals(r)).isEmpty();
                })
            .collect(ImmutableMap.toImmutableMap(Entry::getKey, Entry::getValue)));
  }
}
