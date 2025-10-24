/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import de.ii.ogcapi.styles.domain.MbStyleArrayOfSprites;
import de.ii.ogcapi.styles.domain.MbStyleSingleSprite;
import de.ii.ogcapi.styles.domain.MbStyleSpriteObject;
import de.ii.ogcapi.styles.domain.MbStyleSprites;
import java.io.IOException;
import java.util.List;

public class MbStyleSpritesSerializer extends JsonSerializer<MbStyleSprites> {

  @Override
  public void serialize(MbStyleSprites value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    if (value instanceof MbStyleSingleSprite stringValue) {
      gen.writeString(stringValue.getValue());
    } else if (value instanceof MbStyleArrayOfSprites arrayValue) {
      List<MbStyleSpriteObject> values = arrayValue.getValue();
      gen.writeStartArray();
      for (MbStyleSpriteObject v : values) {
        gen.writeStartObject();
        gen.writeStringField("id", v.getId());
        gen.writeStringField("url", v.getUrl());
        gen.writeEndObject();
      }
      gen.writeEndArray();
    } else {
      throw new IOException("Unexpected MapLibre expression type: " + value);
    }
  }
}
