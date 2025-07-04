/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonDeserialize(as = ImmutableSchemaProperty.class)
public abstract class SchemaProperty {

  private static final String STRING = "String";
  private static final String NUMBER = "Number";
  private static final String INTEGER = "Integer";
  private static final String BOOLEAN = "Boolean";
  private static final String TIMESTAMP = "Timestamp";
  private static final String DATE = "Date";
  private static final String URI = "URI";
  private static final String OBJECT = "Object";

  public static SchemaProperty of(String name, ObjectNode schema, SchemaType schemaType) {
    ImmutableSchemaProperty.Builder builder = ImmutableSchemaProperty.builder().id(name);
    if (schema.has("title")) {
      builder.title(schema.get("title").textValue());
    }
    if (schema.has("description")) {
      builder.description(schema.get("description").textValue());
    }
    if (schema.has("default")) {
      JsonNode defaultValue = schema.get("default");
      if (defaultValue.isTextual()) {
        builder.defaultValue(defaultValue.textValue());
      } else if (defaultValue.isArray()) {
        Iterable<JsonNode> iterable = defaultValue::elements;
        StreamSupport.stream(iterable.spliterator(), false)
            .map(element -> element.isTextual() ? element.textValue() : element.toString())
            .forEach(builder::addDefaultValues);
      } else {
        builder.defaultValue(defaultValue.toString());
      }
    }

    String type = schema.has("type") ? schema.get("type").textValue() : null;
    if ("array".equals(type)) {
      builder.isArray(true);
      schema = (ObjectNode) schema.get("items");
      type = schema.has("type") ? schema.get("type").textValue() : null;
    } else {
      builder.isArray(false);
    }

    if ("string".equals(type)) {
      if (schema.has("format")) {
        String format = schema.get("format").textValue();
        if ("date-time".equals(format)) {
          builder.type(TIMESTAMP);
        } else if ("date".equals(format)) {
          builder.type(DATE);
        } else if ("uri".equals(format)) {
          builder.type(URI);
        } else {
          builder.type(STRING);
        }
      } else {
        builder.type(STRING);
      }
      if (schema.has("minLength")) {
        builder.minLength(schema.get("minLength").numberValue());
      }
      if (schema.has("maxLength")) {
        builder.maxLength(schema.get("maxLength").numberValue());
      }
      if (schema.has("pattern")) {
        builder.pattern(schema.get("pattern").textValue());
      }
      if (schema.has("enum")) {
        Iterator<JsonNode> enums = schema.get("enum").elements();
        while (enums.hasNext()) {
          builder.addValues(enums.next().textValue());
        }
      }
    } else if ("number".equals(type)) {
      builder.type(NUMBER);
      if (schema.has("minimum")) {
        builder.minimum(schema.get("minimum").numberValue());
      }
      if (schema.has("maximum")) {
        builder.maximum(schema.get("maximum").numberValue());
      }
    } else if ("integer".equals(type)) {
      builder.type(INTEGER);
      if (schema.has("minimum")) {
        builder.minimum(schema.get("minimum").numberValue());
      }
      if (schema.has("maximum")) {
        builder.maximum(schema.get("maximum").numberValue());
      }
      if (schema.has("enum")) {
        Iterator<JsonNode> enums = schema.get("enum").elements();
        while (enums.hasNext()) {
          builder.addValues(String.valueOf(enums.next().intValue()));
        }
      }
    } else if ("boolean".equals(type)) {
      builder.type(BOOLEAN);
    } else if ("object".equals(type)) {
      builder.type(OBJECT);
    } else {
      builder.type(STRING);
    }
    return builder.inSchemaType(schemaType).build();
  }

  public abstract String getId();

  public abstract String getType();

  @JsonIgnore
  @Value.Derived
  public boolean isInteger() {
    return INTEGER.equals(getType());
  }

  @JsonIgnore
  @Value.Derived
  public boolean isNumber() {
    return NUMBER.equals(getType());
  }

  @JsonIgnore
  @Value.Derived
  public boolean isBoolean() {
    return BOOLEAN.equals(getType());
  }

  @JsonIgnore
  @Value.Derived
  public boolean isUri() {
    return URI.equals(getType());
  }

  @JsonIgnore
  @Value.Derived
  public boolean isDate() {
    return DATE.equals(getType());
  }

  @JsonIgnore
  @Value.Derived
  public boolean isTimestamp() {
    return TIMESTAMP.equals(getType());
  }

  @JsonIgnore
  @Value.Derived
  public boolean isString() {
    return STRING.equals(getType());
  }

  public abstract boolean isArray();

  public abstract Optional<String> getTitle();

  public abstract Optional<String> getDescription();

  public abstract Optional<Boolean> getRequired();

  public abstract Optional<String> getPattern();

  public abstract Optional<Number> getMinLength();

  public abstract Optional<Number> getMaxLength();

  public abstract Optional<Number> getMinimum();

  public abstract Optional<Number> getMaximum();

  public abstract List<String> getValues();

  public abstract SchemaType inSchemaType();

  @JsonIgnore
  @Value.Derived
  public Optional<String> getValueList() {
    return !getValues().isEmpty() ? Optional.of(String.join("; ", getValues())) : Optional.empty();
  }

  @JsonIgnore
  @Value.Derived
  public List<String> getValuesAsOptions() {
    return getValues().stream()
        .map(
            value ->
                String.format(
                    "<option%s>%s</option>",
                    getDefaultValues().contains(value) || getDefaultValue().orElse("").equals(value)
                        ? " selected"
                        : "",
                    value))
        .collect(Collectors.toUnmodifiableList());
  }

  @JsonIgnore
  @Value.Derived
  public boolean hasValues() {
    return !getValues().isEmpty();
  }

  @JsonIgnore
  @Value.Derived
  public boolean isReturnablesReceivables() {
    return inSchemaType() == SchemaType.RETURNABLES_AND_RECEIVABLES;
  }

  @JsonIgnore
  @Value.Derived
  public int getMargin() {
    if (inSchemaType() == SchemaType.RETURNABLES_AND_RECEIVABLES) {
      int depth = (int) getId().chars().filter(c -> c == '.').count();
      return depth * 2;
    }
    return 0;
  }

  @JsonIgnore
  @Value.Derived
  public String getLastId() {
    String[] parts = getId().split("\\.");
    return parts[parts.length - 1];
  }

  public abstract Optional<String> getDefaultValue();

  public abstract List<String> getDefaultValues();
}
