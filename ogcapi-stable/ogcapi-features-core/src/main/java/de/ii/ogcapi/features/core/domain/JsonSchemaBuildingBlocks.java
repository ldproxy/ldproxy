/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.google.common.collect.ImmutableList;

public interface JsonSchemaBuildingBlocks {

  JsonSchemaNull NULL = new ImmutableJsonSchemaNull.Builder().build();

  JsonSchemaGeometry POINT =
      new ImmutableJsonSchemaGeometry.Builder().format("geometry-point").build();
  JsonSchemaGeometry MULTI_POINT =
      new ImmutableJsonSchemaGeometry.Builder().format("geometry-multipoint").build();
  JsonSchemaGeometry POINT_OR_MULTI_POINT =
      new ImmutableJsonSchemaGeometry.Builder().format("geometry-point-or-multipoint").build();
  JsonSchemaGeometry LINE_STRING =
      new ImmutableJsonSchemaGeometry.Builder().format("geometry-linestring").build();
  JsonSchemaGeometry MULTI_LINE_STRING =
      new ImmutableJsonSchemaGeometry.Builder().format("geometry-multilinestring").build();
  JsonSchemaGeometry LINE_STRING_OR_MULTI_LINE_STRING =
      new ImmutableJsonSchemaGeometry.Builder()
          .format("geometry-linestring-or-multilinestring")
          .build();
  JsonSchemaGeometry POLYGON =
      new ImmutableJsonSchemaGeometry.Builder().format("geometry-polygon").build();
  JsonSchemaGeometry MULTI_POLYGON =
      new ImmutableJsonSchemaGeometry.Builder().format("geometry-multipolygon").build();
  JsonSchemaGeometry POLYGON_OR_MULTI_POLYGON =
      new ImmutableJsonSchemaGeometry.Builder().format("geometry-polygon-or-multipolygon").build();
  JsonSchemaGeometry POLYHEDRON =
      new ImmutableJsonSchemaGeometry.Builder().format("geometry-polyhedron").build();
  JsonSchemaGeometry MULTI_POLYHEDRON =
      new ImmutableJsonSchemaGeometry.Builder().format("geometry-multipolyhedron").build();
  JsonSchemaGeometry POLYHEDRON_OR_MULTI_POLYHEDRON =
      new ImmutableJsonSchemaGeometry.Builder()
          .format("geometry-polyhedron-or-multipolyhedron")
          .build();
  JsonSchemaGeometry GEOMETRY =
      new ImmutableJsonSchemaGeometry.Builder().format("geometry-any").build();
  JsonSchemaGeometry GEOMETRY_COLLECTION =
      new ImmutableJsonSchemaGeometry.Builder().format("geometry-geometrycollection").build();

  JsonSchemaObject LINK_JSON =
      new ImmutableJsonSchemaObject.Builder()
          .putProperties(
              "href", new ImmutableJsonSchemaString.Builder().format("uri-reference").build())
          .putProperties("rel", new ImmutableJsonSchemaString.Builder().build())
          .putProperties("type", new ImmutableJsonSchemaString.Builder().build())
          .putProperties("title", new ImmutableJsonSchemaString.Builder().build())
          .required(ImmutableList.of("href", "rel"))
          .build();

  static JsonSchemaString getEnum(String value) {
    return new ImmutableJsonSchemaString.Builder().addEnums(value).build();
  }

  static JsonSchemaOneOf nullable(JsonSchema schema) {
    if (schema instanceof JsonSchemaOneOf) {
      return new ImmutableJsonSchemaOneOf.Builder()
          .addOneOf(NULL)
          .addAllOneOf(((JsonSchemaOneOf) schema).getOneOf())
          .build();
    }

    return new ImmutableJsonSchemaOneOf.Builder().addOneOf(NULL, schema).build();
  }
}
