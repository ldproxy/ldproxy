/*
 * Copyright 2023 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonSchemaDeserializer extends StdDeserializer<JsonSchema> {

  private static final Logger LOGGER = LoggerFactory.getLogger(JsonSchemaDeserializer.class);

  private final ObjectMapper mapper;

  public JsonSchemaDeserializer() {
    this(null);
  }

  public JsonSchemaDeserializer(Class<?> clazz) {
    super(clazz);
    mapper = new ObjectMapper();
    mapper.registerModule(new Jdk8Module());
    mapper.registerModule(new GuavaModule());
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
  }

  @Override
  public JsonSchema deserialize(
      JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
    JsonNode schemaNode = jsonParser.getCodec().readTree(jsonParser);

    // special case of "true" and "false" schema
    if (schemaNode.isBoolean()) {
      if (schemaNode.asBoolean()) {
        return ImmutableJsonSchemaTrue.builder().build();
      }
      return ImmutableJsonSchemaFalse.builder().build();
    }

    // otherwise we must have a schema object
    if (!schemaNode.isObject()) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn(
            "Invalid JSON Schema, neither an object nor 'true' or 'false'. Using 'false'. Found: {}",
            schemaNode);
      }
      return ImmutableJsonSchemaFalse.builder().build();
    }

    if (schemaNode.hasNonNull("type")) {
      JsonNode type = schemaNode.get("type");
      if (type.isArray()) {
        if (LOGGER.isWarnEnabled()) {
          LOGGER.warn(
              "JSON Schema type arrays are not supported. Using the first value. Found: {}",
              schemaNode);
        }
        if (type.has(0)) {
          type = type.get(0);
        }
      }
      if (type.isTextual()) {
        switch (type.asText()) {
          case "array":
            return mapper.treeToValue(schemaNode, JsonSchemaArray.class);
          case "boolean":
            return mapper.treeToValue(schemaNode, JsonSchemaBoolean.class);
          case "integer":
            return mapper.treeToValue(schemaNode, JsonSchemaInteger.class);
          case "null":
            return mapper.treeToValue(schemaNode, JsonSchemaNull.class);
          case "number":
            return mapper.treeToValue(schemaNode, JsonSchemaNumber.class);
          case "object":
            return mapper.treeToValue(schemaNode, JsonSchemaObject.class);
          case "string":
            return mapper.treeToValue(schemaNode, JsonSchemaString.class);
        }
      } else {
        if (LOGGER.isWarnEnabled()) {
          LOGGER.warn(
              "Invalid JSON Schema, 'type' is not a string. Using 'false'. Found: {}", schemaNode);
        }
        return ImmutableJsonSchemaFalse.builder().build();
      }
    } else if (schemaNode.hasNonNull("constant")) {
      return mapper.treeToValue(schemaNode, JsonSchemaConstant.class);
    } else if (schemaNode.hasNonNull("$ref")) {
      return mapper.treeToValue(schemaNode, JsonSchemaRef.class);
    } else if (schemaNode.hasNonNull("oneOf")) {
      return mapper.treeToValue(schemaNode, JsonSchemaOneOf.class);
    } else if (schemaNode.hasNonNull("format")) {
      return mapper.treeToValue(schemaNode, JsonSchemaGeometry.class);
    }

    if (LOGGER.isWarnEnabled()) {
      LOGGER.warn(
          "The provided JSON Schema is not supported. Using 'false'. Found: {}", schemaNode);
    }
    return ImmutableJsonSchemaFalse.builder().build();
  }
}
