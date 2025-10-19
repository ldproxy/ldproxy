/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import java.io.IOException;
import java.util.Objects;

public interface ParameterResolverBase {

  default Object resolveParameter(
      String name,
      JsonSchema schema,
      QueryParameterSet queryParameterSet,
      SchemaValidator schemaValidator) {
    Object value =
        queryParameterSet.getTypedValues().containsKey(name)
            ? queryParameterSet.getTypedValues().get(name)
            : useDefault(name, schema);
    validateParameter(name, value, schema, schemaValidator);
    return value;
  }

  default Object useDefault(String name, JsonSchema schema) {
    if (Objects.isNull(schema)) {
      throw new IllegalStateException(
          String.format("No schema provided for parameter '%s'.", name));
    }
    return schema
        .getDefault_()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    String.format("No value provided for parameter '%s'.", name)));
  }

  default void validateParameter(
      String name, Object value, JsonSchema schema, SchemaValidator schemaValidator) {
    final String schemaAsString;
    try {
      // get the JSON Schema of the parameter as a string for validation
      schemaAsString = QueryExpression.MAPPER.writeValueAsString(schema);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          String.format("Could not read the schema of parameter '%s' in a query.", name), e);
    }

    final String stringValue;
    try {
      // convert to string for validation
      stringValue = QueryExpression.MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(
          String.format(
              "The JSON value derived for parameter '%s' could not be converted to a string value for validation.",
              name),
          e);
    }
    // validate parameter
    try {
      schemaValidator
          .validate(schemaAsString, stringValue)
          .ifPresent(
              error -> {
                throw new IllegalArgumentException(
                    String.format(
                        "Parameter '%s' is invalid, the value '%s' does not conform to the schema '%s'. Reason: %s",
                        name, value, schemaAsString, error));
              });
    } catch (IOException e) {
      throw new IllegalStateException(
          String.format(
              "Could not validate value '%s' of parameter '%s' against its schema '%s'",
              value, name, schemaAsString),
          e);
    }
  }
}
