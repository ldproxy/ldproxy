/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import de.ii.ogcapi.styles.domain.ImmutableArrayValue;
import de.ii.ogcapi.styles.domain.ImmutableBooleanValue;
import de.ii.ogcapi.styles.domain.ImmutableNullValue;
import de.ii.ogcapi.styles.domain.ImmutableNumberValue;
import de.ii.ogcapi.styles.domain.ImmutableObjectValue;
import de.ii.ogcapi.styles.domain.ImmutableStringValue;
import de.ii.ogcapi.styles.domain.MbStyleExpression;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class MbStyleExpressionDeserializer extends JsonDeserializer<MbStyleExpression> {

  private final ObjectMapper mapper;

  public MbStyleExpressionDeserializer() {
    mapper = new ObjectMapper();
    mapper.registerModule(new Jdk8Module());
    mapper.registerModule(new GuavaModule());
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
  }

  @Override
  public MbStyleExpression deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException {
    JsonNode node = mapper.readTree(p);

    if (node.isTextual()) {
      return ImmutableStringValue.of(node.asText());
    } else if (node.isNumber()) {
      return ImmutableNumberValue.of(node.numberValue());
    } else if (node.isBoolean()) {
      return ImmutableBooleanValue.of(node.asBoolean());
    } else if (node.isNull()) {
      return new ImmutableNullValue.Builder().build();
    } else if (node.isArray()) {
      List<MbStyleExpression> values = new ArrayList<>();
      for (JsonNode element : node) {
        values.add(deserialize(element.traverse(p.getCodec()), ctxt));
      }
      return ImmutableArrayValue.of(values);
    } else if (node.isObject()) {
      Map<String, MbStyleExpression> values = new HashMap<>();
      ObjectNode objectNode = (ObjectNode) node;
      for (Entry<String, JsonNode> entry : objectNode.properties()) {
        values.put(entry.getKey(), deserialize(entry.getValue().traverse(), ctxt));
      }
      return ImmutableObjectValue.of(values);
    } else {
      throw new IOException("Unexpected MapLibre expression type: " + node);
    }
  }
}
