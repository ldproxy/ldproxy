/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(using = FilterOperatorOrParameter.Serializer.class)
@JsonDeserialize(using = FilterOperatorOrParameter.Deserializer.class)
public interface FilterOperatorOrParameter extends StoredQueryComponent {

  @Value.Parameter
  Optional<FilterOperator> getValue();

  @Value.Parameter
  Optional<ParameterValue> getParameter();

  @Value.Check
  default void check() {
    if (getValue().isEmpty() && getParameter().isEmpty()) {
      throw new IllegalStateException("Either AND, OR or parameter must be set.");
    }
    if (getValue().isPresent() && getParameter().isPresent()) {
      throw new IllegalStateException("Only one of AND, OR or parameter can be set.");
    }
  }

  class Deserializer extends JsonDeserializer<FilterOperatorOrParameter> {

    @Override
    public FilterOperatorOrParameter deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException {
      JsonNode node = StoredQueryExpression.MAPPER.readTree(p);

      if (node.isTextual()) {
        return new ImmutableFilterOperatorOrParameter.Builder()
            .value(FilterOperator.valueOf(node.asText()))
            .build();
      } else if (node.isObject()) {
        ParameterValue param =
            StoredQueryExpression.MAPPER.treeToValue(node.get("$parameter"), ParameterValue.class);
        return new ImmutableFilterOperatorOrParameter.Builder().parameter(param).build();
      }

      throw new IOException("Expected AND, OR or a parameter object. Found: " + node);
    }
  }

  class Serializer extends StdSerializer<FilterOperatorOrParameter> {

    protected Serializer() {
      this(null);
    }

    protected Serializer(Class<FilterOperatorOrParameter> t) {
      super(t);
    }

    @Override
    public void serialize(
        FilterOperatorOrParameter value,
        JsonGenerator jsonGenerator,
        SerializerProvider serializerProvider)
        throws IOException {
      if (value.getValue().isPresent()) {
        jsonGenerator.writeString(value.getValue().get().name());
      } else if (value.getParameter().isPresent()) {
        serializerProvider
            .findValueSerializer(ParameterValue.class)
            .serialize(value.getParameter().get(), jsonGenerator, serializerProvider);
      }
    }
  }
}
