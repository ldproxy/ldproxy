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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaArray;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaBoolean;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaInteger;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaNumber;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaObject;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaString;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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

  public static SchemaProperty of(String name, JsonSchema schema, SchemaType schemaType) {
    ImmutableSchemaProperty.Builder builder = ImmutableSchemaProperty.builder().id(name);
    schema.getTitle().ifPresent(builder::title);
    schema.getDescription().ifPresent(builder::description);
    schema
        .getDefault_()
        .ifPresent(
            v -> {
              if (v instanceof List) {
                ((List<?>) v).forEach(item -> builder.addDefaultValues(item.toString()));
              } else {
                builder.defaultValue(v.toString());
              }
            });

    if (schema instanceof JsonSchemaArray) {
      builder.isArray(true);
      schema = ((JsonSchemaArray) schema).getItems().orElse(null);
    } else {
      builder.isArray(false);
    }

    if (schema == null) {
      builder.type(STRING);
    } else if (schema instanceof JsonSchemaString string) {
      String format = string.getFormat().orElse(null);
      if ("date-time".equals(format)) {
        builder.type(TIMESTAMP);
      } else if ("date".equals(format)) {
        builder.type(DATE);
      } else if ("uri".equals(format)) {
        builder.type(URI);
      } else {
        builder.type(STRING);
      }
      string.getPattern().ifPresent(builder::pattern);
      string.getEnums().ifPresent(values -> values.forEach(builder::addValues));
      string.getMinLength().ifPresent(builder::minLength);
      string.getMaxLength().ifPresent(builder::maxLength);
    } else if (schema instanceof JsonSchemaNumber number) {
      builder.type(NUMBER);
      number.getMinimum().ifPresent(builder::minimum);
      number.getMaximum().ifPresent(builder::maximum);
    } else if (schema instanceof JsonSchemaInteger integer) {
      builder.type(INTEGER);
      integer.getMinimum().ifPresent(builder::minimum);
      integer.getMaximum().ifPresent(builder::maximum);
      integer
          .getEnums()
          .ifPresent(values -> values.forEach(v -> builder.addValues(String.valueOf(v))));
    } else if (schema instanceof JsonSchemaBoolean) {
      builder.type(BOOLEAN);
    } else if (schema instanceof JsonSchemaObject) {
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

  public abstract Optional<String> getRole();

  public abstract Optional<String> getCodelistId();

  public abstract Optional<String> getCodelistUri();

  public abstract List<String> getRefCollectionIds();

  public abstract List<String> getValues();

  public abstract SchemaType inSchemaType();

  @Value.Default
  public boolean getWriteOnly() {
    return false;
  }

  @Value.Default
  public boolean getReadOnly() {
    return false;
  }

  @JsonIgnore
  @Value.Derived
  public Optional<String> getCodelistText() {
    if (getCodelistId().isEmpty()) {
      return Optional.empty();
    }
    return getCodelistUri()
        .map(
            uri ->
                String.format(
                    "<a href=\"%s\" target=\"_blank\">%s</a>", uri, getCodelistId().get()))
        .or(this::getCodelistId);
  }

  @JsonIgnore
  @Value.Derived
  public Optional<String> getRefCollectionIdsList() {
    return !getRefCollectionIds().isEmpty()
        ? Optional.of(String.join("; ", getRefCollectionIds()))
        : Optional.empty();
  }

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
