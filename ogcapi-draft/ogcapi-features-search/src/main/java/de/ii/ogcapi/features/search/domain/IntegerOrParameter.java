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
@JsonSerialize(using = IntegerOrParameter.Serializer.class)
@JsonDeserialize(using = IntegerOrParameter.Deserializer.class)
public interface IntegerOrParameter extends StoredQueryComponent {

  @Value.Parameter
  Optional<Integer> getValue();

  @Value.Parameter
  Optional<ParameterValue> getParameter();

  @Value.Check
  default void check() {
    if (getValue().isEmpty() && getParameter().isEmpty()) {
      throw new IllegalStateException("Either integer or parameter must be set.");
    }
    if (getValue().isPresent() && getParameter().isPresent()) {
      throw new IllegalStateException("Only one of integer or parameter can be set.");
    }
  }

  class Deserializer extends JsonDeserializer<IntegerOrParameter> {

    @Override
    public IntegerOrParameter deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException {
      JsonNode node = StoredQueryExpression.MAPPER.readTree(p);

      if (node.isInt()) {
        return new ImmutableIntegerOrParameter.Builder().value(node.asInt()).build();
      } else if (node.isObject()) {
        ParameterValue param =
            StoredQueryExpression.MAPPER.treeToValue(node.get("$parameter"), ParameterValue.class);
        return new ImmutableIntegerOrParameter.Builder().parameter(param).build();
      }

      throw new IOException("Expected integer or a parameter object. Found: " + node);
    }
  }

  class Serializer extends StdSerializer<IntegerOrParameter> {

    protected Serializer() {
      this(null);
    }

    protected Serializer(Class<IntegerOrParameter> t) {
      super(t);
    }

    @Override
    public void serialize(
        IntegerOrParameter value,
        JsonGenerator jsonGenerator,
        SerializerProvider serializerProvider)
        throws IOException {
      if (value.getValue().isPresent()) {
        jsonGenerator.writeNumber(value.getValue().get());
      } else if (value.getParameter().isPresent()) {
        serializerProvider
            .findValueSerializer(ParameterValue.class)
            .serialize(value.getParameter().get(), jsonGenerator, serializerProvider);
      }
    }
  }
}
