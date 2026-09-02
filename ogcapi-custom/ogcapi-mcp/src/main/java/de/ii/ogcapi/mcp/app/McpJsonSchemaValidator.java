/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates structured tool output against a tool's output schema.
 *
 * <p>The MCP SDK ships a default validator, but it is compiled against json-schema-validator 1.5.x,
 * whose {@code SpecVersion.VersionFlag} was removed in 2.0. Since ogcapi-foundation exports 2.0.x,
 * the default validator fails with a {@link NoClassDefFoundError} as soon as it is instantiated, so
 * this implementation is passed to the server builder instead.
 */
public class McpJsonSchemaValidator implements JsonSchemaValidator {

  private static final Logger LOGGER = LoggerFactory.getLogger(McpJsonSchemaValidator.class);

  private final ObjectMapper objectMapper;
  private final SchemaRegistry schemaRegistry;
  private final Map<String, Schema> schemaCache;

  public McpJsonSchemaValidator(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.schemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
    this.schemaCache = new ConcurrentHashMap<>();
  }

  @Override
  public ValidationResponse validate(Map<String, Object> schema, Object structuredContent) {
    if (schema == null) {
      throw new IllegalArgumentException("Schema must not be null");
    }
    if (structuredContent == null) {
      throw new IllegalArgumentException("Structured content must not be null");
    }

    try {
      JsonNode content =
          structuredContent instanceof String str
              ? objectMapper.readTree(str)
              : objectMapper.valueToTree(structuredContent);

      List<Error> errors = getOrCreateSchema(schema).validate(content);

      if (!errors.isEmpty()) {
        return ValidationResponse.asInvalid(
            "Validation failed: structuredContent does not match tool outputSchema. "
                + "Validation errors: "
                + errors);
      }

      return ValidationResponse.asValid(content.toString());

    } catch (JsonProcessingException e) {
      LOGGER.error("Failed to validate tool result: error parsing schema: {}", e.getMessage());
      return ValidationResponse.asInvalid("Error parsing tool JSON Schema: " + e.getMessage());
    } catch (Exception e) {
      LOGGER.error("Failed to validate tool result: unexpected error: {}", e.getMessage());
      return ValidationResponse.asInvalid("Unexpected validation error: " + e.getMessage());
    }
  }

  private Schema getOrCreateSchema(Map<String, Object> schema) {
    return schemaCache.computeIfAbsent(
        cacheKey(schema),
        key -> {
          Schema compiled = schemaRegistry.getSchema(objectMapper.valueToTree(schema));
          // validators are lazily created otherwise, which is not thread-safe
          compiled.initializeValidators();
          return compiled;
        });
  }

  private String cacheKey(Map<String, Object> schema) {
    if (schema.containsKey("$id")) {
      return String.valueOf(schema.get("$id"));
    }
    return String.valueOf(schema.hashCode());
  }
}
