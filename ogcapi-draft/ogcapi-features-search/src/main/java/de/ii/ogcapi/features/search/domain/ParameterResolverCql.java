/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.xtraplatform.cql.domain.ArrayLiteral;
import de.ii.xtraplatform.cql.domain.CqlNode;
import de.ii.xtraplatform.cql.domain.CqlVisitorCopy;
import de.ii.xtraplatform.cql.domain.Parameter;
import de.ii.xtraplatform.cql.domain.Scalar;
import de.ii.xtraplatform.cql.domain.ScalarLiteral;
import de.ii.xtraplatform.cql.domain.SpatialLiteral;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.geometries.domain.Geometry;
import de.ii.xtraplatform.geometries.domain.transcode.wktwkb.GeometryDecoderWkt;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaString;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ParameterResolverCql extends CqlVisitorCopy implements ParameterResolverBase {

  private static final String GEOMETRY_FORMAT_PREFIX = "geometry";

  private final QueryParameterSet queryParameterSet;
  private final SchemaValidator schemaValidator;
  private final Map<String, JsonSchema> globalParameters;
  private final Optional<EpsgCrs> filterCrs;

  public ParameterResolverCql(
      QueryParameterSet queryParameterSet,
      Map<String, JsonSchema> globalParameters,
      SchemaValidator schemaValidator,
      Optional<EpsgCrs> filterCrs) {
    this.queryParameterSet = queryParameterSet;
    this.schemaValidator = schemaValidator;
    this.globalParameters = globalParameters;
    this.filterCrs = filterCrs;
  }

  @Override
  public CqlNode visit(Parameter parameter, List<CqlNode> children) {
    String name = parameter.getName();
    Object value = queryParameterSet.getTypedValues().get(name);
    JsonSchema schema =
        Objects.requireNonNullElse(globalParameters.get(name), parameter.getSchema());
    validateParameter(parameter.getName(), value, schema, schemaValidator);
    Optional<String> geometryFormat = getGeometryFormat(schema);
    if (geometryFormat.isPresent()) {
      return handleGeometry(name, value, geometryFormat.get());
    }
    if (value instanceof List<?> list) {
      return ArrayLiteral.of(list.stream().map(this::handleScalar).toList());
    }
    return handleScalar(value);
  }

  private static Optional<String> getGeometryFormat(JsonSchema schema) {
    if (schema instanceof JsonSchemaString string) {
      return string.getFormat().filter(format -> format.startsWith(GEOMETRY_FORMAT_PREFIX));
    }
    return Optional.empty();
  }

  private CqlNode handleGeometry(String name, Object value, String format) {
    if (!(value instanceof String wkt)) {
      throw new IllegalArgumentException(
          String.format(
              "Parameter '%s' must be a geometry as WKT (well-known text). Found: %s",
              name, value));
    }

    Geometry<?> geometry;
    try {
      geometry = new GeometryDecoderWkt().decode(wkt, filterCrs);
    } catch (IOException | RuntimeException e) {
      throw new IllegalArgumentException(
          String.format(
              "Parameter '%s' is not a valid geometry as WKT (well-known text): %s", name, wkt),
          e);
    }

    // a format like "geometry-polygon" restricts the parameter to that geometry type
    String requestedType =
        format.startsWith(GEOMETRY_FORMAT_PREFIX + "-")
            ? format.substring(GEOMETRY_FORMAT_PREFIX.length() + 1)
            : "any";
    String actualType = geometry.getType().name().replace("_", "").toLowerCase(Locale.ROOT);
    if (!"any".equals(requestedType)
        && !Objects.equals(requestedType.replace("-", ""), actualType)) {
      throw new IllegalArgumentException(
          String.format(
              "Parameter '%s' must be a geometry of type '%s'. Found: %s",
              name, requestedType, geometry.getType().name()));
    }

    return SpatialLiteral.of(geometry);
  }

  private Scalar handleScalar(Object value) {
    if (value instanceof String string) {
      return ScalarLiteral.of(string);
    } else if (value instanceof Integer num) {
      return ScalarLiteral.of(num);
    } else if (value instanceof Double num) {
      return ScalarLiteral.of(num);
    } else if (value instanceof Boolean bool) {
      return ScalarLiteral.of(bool);
    }
    return null;
  }
}
