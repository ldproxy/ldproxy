/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The tool schemas are modelled with the OpenAPI 3.0 schema objects of the API definition, while
 * MCP clients expect JSON Schema 2020-12, as do the schemas of process descriptions. The two differ
 * in the exclusive bounds: OpenAPI 3.0 has {@code minimum} with a boolean {@code exclusiveMinimum},
 * JSON Schema 2020-12 has a numeric {@code exclusiveMinimum}; the same for the maximum.
 */
final class McpSchemaVersions {

  private static final String MINIMUM = "minimum";
  private static final String MAXIMUM = "maximum";
  private static final String EXCLUSIVE_MINIMUM = "exclusiveMinimum";
  private static final String EXCLUSIVE_MAXIMUM = "exclusiveMaximum";

  private McpSchemaVersions() {}

  /** A JSON Schema 2020-12 schema with the exclusive bounds of OpenAPI 3.0, nested schemas too. */
  static Map<String, Object> toOpenApi30(Map<String, Object> schema) {
    return convert(schema, true);
  }

  /** An OpenAPI 3.0 schema with the exclusive bounds of JSON Schema 2020-12, nested schemas too. */
  static Map<String, Object> toJsonSchema2020(Map<String, Object> schema) {
    return convert(schema, false);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> convert(Map<String, Object> schema, boolean toOpenApi30) {
    Map<String, Object> converted = new LinkedHashMap<>();
    schema.forEach((key, value) -> converted.put(key, convertValue(value, toOpenApi30)));
    if (toOpenApi30) {
      toBoolean(converted, EXCLUSIVE_MINIMUM, MINIMUM);
      toBoolean(converted, EXCLUSIVE_MAXIMUM, MAXIMUM);
    } else {
      toNumber(converted, EXCLUSIVE_MINIMUM, MINIMUM);
      toNumber(converted, EXCLUSIVE_MAXIMUM, MAXIMUM);
    }
    return converted;
  }

  @SuppressWarnings("unchecked")
  private static Object convertValue(Object value, boolean toOpenApi30) {
    if (value instanceof Map<?, ?> map) {
      return convert((Map<String, Object>) map, toOpenApi30);
    }
    if (value instanceof List<?> list) {
      return list.stream().map(item -> convertValue(item, toOpenApi30)).toList();
    }
    return value;
  }

  // {"exclusiveMinimum": 0} -> {"minimum": 0, "exclusiveMinimum": true}
  private static void toBoolean(Map<String, Object> schema, String exclusive, String bound) {
    if (schema.get(exclusive) instanceof Number value) {
      schema.put(bound, value);
      schema.put(exclusive, true);
    }
  }

  // {"minimum": 0, "exclusiveMinimum": true} -> {"exclusiveMinimum": 0}
  private static void toNumber(Map<String, Object> schema, String exclusive, String bound) {
    Object flag = schema.get(exclusive);
    if (flag instanceof Boolean isExclusive) {
      if (isExclusive && Objects.nonNull(schema.get(bound))) {
        schema.put(exclusive, schema.remove(bound));
      } else {
        schema.remove(exclusive);
      }
    }
  }
}
