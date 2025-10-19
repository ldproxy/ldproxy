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
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(using = ParameterOrListOfStringOrParameter.Serializer.class)
@JsonDeserialize(using = ParameterOrListOfStringOrParameter.Deserializer.class)
public interface ParameterOrListOfStringOrParameter extends StoredQueryComponent {

  @Value.Parameter
  Optional<List<StringOrParameter>> getValue();

  @Value.Parameter
  Optional<ParameterValue> getParameter();

  @Value.Check
  default void check() {
    if (getValue().isEmpty() && getParameter().isEmpty()) {
      throw new IllegalStateException(
          "Either a parameter or a list of strings or parameters must be set.");
    }
    if (getValue().isPresent() && getParameter().isPresent()) {
      throw new IllegalStateException(
          "Only one of parameter or a list of string or parameters can be set.");
    }
  }

  class Deserializer extends JsonDeserializer<ParameterOrListOfStringOrParameter> {

    @Override
    public ParameterOrListOfStringOrParameter deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException {
      JsonNode node = StoredQueryExpression.MAPPER.readTree(p);

      if (node.isArray()) {
        ImmutableList.Builder<StringOrParameter> items = ImmutableList.builder();
        for (Iterator<JsonNode> it = node.elements(); it.hasNext(); ) {
          JsonNode item = it.next();
          items.add(StoredQueryExpression.MAPPER.treeToValue(item, StringOrParameter.class));
        }
        return new ImmutableParameterOrListOfStringOrParameter.Builder()
            .value(items.build())
            .build();
      } else if (node.isObject()) {
        ParameterValue param =
            StoredQueryExpression.MAPPER.treeToValue(node.get("$parameter"), ParameterValue.class);
        return new ImmutableParameterOrListOfStringOrParameter.Builder().parameter(param).build();
      }

      throw new IOException("Expected string or a parameter object. Found: " + node);
    }
  }

  class Serializer extends StdSerializer<ParameterOrListOfStringOrParameter> {

    protected Serializer() {
      this(null);
    }

    protected Serializer(Class<ParameterOrListOfStringOrParameter> t) {
      super(t);
    }

    @Override
    public void serialize(
        ParameterOrListOfStringOrParameter value,
        JsonGenerator jsonGenerator,
        SerializerProvider serializerProvider)
        throws IOException {
      if (value.getValue().isPresent()) {
        jsonGenerator.writeStartArray();
        for (StringOrParameter item : value.getValue().get()) {
          if (item.getValue().isPresent()) {
            jsonGenerator.writeString(item.getValue().get());
          } else if (item.getParameter().isPresent()) {
            serializerProvider
                .findValueSerializer(ParameterValue.class)
                .serialize(item.getParameter().get(), jsonGenerator, serializerProvider);
          }
        }
        jsonGenerator.writeEndArray();
      } else if (value.getParameter().isPresent()) {
        serializerProvider
            .findValueSerializer(ParameterValue.class)
            .serialize(value.getParameter().get(), jsonGenerator, serializerProvider);
      }
    }
  }
}
