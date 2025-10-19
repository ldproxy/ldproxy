/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.hash.Funnel;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaRef;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(using = ParameterValue.Serializer.class)
@JsonDeserialize(using = ParameterValue.Deserializer.class)
public interface ParameterValue extends StoredQueryComponent {

  @SuppressWarnings("UnstableApiUsage")
  Funnel<ParameterValue> FUNNEL =
      (from, into) -> {
        into.putString(from.getName(), StandardCharsets.UTF_8);
        JsonSchema.FUNNEL.funnel(from.getSchema(), into);
      };

  @Value.Parameter
  String getName();

  @Value.Parameter
  JsonSchema getSchema();

  class Deserializer extends JsonDeserializer<ParameterValue> {

    @Override
    public ParameterValue deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException {
      JsonNode node = StoredQueryExpression.MAPPER.readTree(p);

      if (!node.isObject()) {
        throw new IOException("Expected a parameter object. Found: " + node);
      }

      if (node.size() != 1) {
        throw new IOException(
            "Expected a (single member with a JSON Schema value). Found "
                + node.size()
                + " members.");
      }

      // Using '$ref' directly inside '$parameter' is deprecated.
      // TODO: Remove support in future versions.
      if (node.has("$ref")) {
        JsonNode value = node.get("$ref");
        if (!value.isTextual()) {
          throw new JsonParseException(
              p, "The value of '$ref' must be a string, found: " + value.getNodeType());
        }
        String name = value.asText();
        if (name.startsWith("#/parameters/")) {
          name = name.substring("#/parameters/".length());
        }
        return new ImmutableParameterValue.Builder()
            .name(name)
            .schema(new ImmutableJsonSchemaRef.Builder().ref(value.asText()).build())
            .build();
      }

      Entry<String, JsonNode> param = node.properties().iterator().next();

      JsonSchema schema =
          StoredQueryExpression.MAPPER.treeToValue(param.getValue(), JsonSchema.class);

      return new ImmutableParameterValue.Builder().name(param.getKey()).schema(schema).build();
    }
  }

  class Serializer extends StdSerializer<ParameterValue> {

    protected Serializer() {
      this(null);
    }

    protected Serializer(Class<ParameterValue> t) {
      super(t);
    }

    @Override
    public void serialize(
        ParameterValue param, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
        throws IOException {
      jsonGenerator.writeStartObject();
      jsonGenerator.writeFieldName("$parameter");
      jsonGenerator.writeStartObject();
      jsonGenerator.writeObjectField(param.getName(), param.getSchema());
      jsonGenerator.writeEndObject();
      jsonGenerator.writeEndObject();
    }
  }
}
