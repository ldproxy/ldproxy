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
import de.ii.ogcapi.styles.domain.ImmutableMbStyleArrayOfSprites;
import de.ii.ogcapi.styles.domain.ImmutableMbStyleSingleSprite;
import de.ii.ogcapi.styles.domain.ImmutableMbStyleSpriteObject;
import de.ii.ogcapi.styles.domain.MbStyleSpriteObject;
import de.ii.ogcapi.styles.domain.MbStyleSprites;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MbStyleSpritesDeserializer extends JsonDeserializer<MbStyleSprites> {

  private final ObjectMapper mapper;

  public MbStyleSpritesDeserializer() {
    mapper = new ObjectMapper();
    mapper.registerModule(new Jdk8Module());
    mapper.registerModule(new GuavaModule());
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
  }

  @Override
  public MbStyleSprites deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    JsonNode node = mapper.readTree(p);

    if (node.isTextual()) {
      return ImmutableMbStyleSingleSprite.of(node.asText());
    } else if (node.isArray()) {
      List<MbStyleSpriteObject> values = new ArrayList<>();
      for (JsonNode element : node) {
        if (!element.isObject()) {
          throw new IOException("Expected object in sprite array, got: " + element);
        }
        ObjectNode objectNode = (ObjectNode) element;
        if (!objectNode.has("id")
            || !objectNode.get("id").isTextual()
            || !objectNode.has("url")
            || !objectNode.get("url").isTextual()) {
          throw new IOException(
              "Expected 'id' and 'uri' fields of type string in a sprite object, got: "
                  + objectNode);
        }
        values.add(
            ImmutableMbStyleSpriteObject.builder()
                .id(objectNode.get("id").asText())
                .url(objectNode.get("url").asText())
                .build());
      }
      return ImmutableMbStyleArrayOfSprites.of(values);
    } else {
      throw new IOException("Unexpected MapLibre sprite type: " + node);
    }
  }
}
