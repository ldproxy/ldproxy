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
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ParameterResolverCql extends CqlVisitorCopy implements ParameterResolverBase {

  private final QueryParameterSet queryParameterSet;
  private final SchemaValidator schemaValidator;
  private final Map<String, JsonSchema> globalParameters;

  public ParameterResolverCql(
      QueryParameterSet queryParameterSet,
      Map<String, JsonSchema> globalParameters,
      SchemaValidator schemaValidator) {
    this.queryParameterSet = queryParameterSet;
    this.schemaValidator = schemaValidator;
    this.globalParameters = globalParameters;
  }

  @Override
  public CqlNode visit(Parameter parameter, List<CqlNode> children) {
    String name = parameter.getName();
    Object value = queryParameterSet.getTypedValues().get(name);
    JsonSchema schema =
        Objects.requireNonNullElse(globalParameters.get(name), parameter.getSchema());
    validateParameter(parameter.getName(), value, schema, schemaValidator);
    if (value instanceof List<?> list) {
      return ArrayLiteral.of(list.stream().map(this::handleScalar).toList());
    }
    return handleScalar(value);
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
