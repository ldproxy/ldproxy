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
import de.ii.ogcapi.styles.domain.ImmutableArrayValue;
import de.ii.ogcapi.styles.domain.ImmutableBooleanValue;
import de.ii.ogcapi.styles.domain.ImmutableNullValue;
import de.ii.ogcapi.styles.domain.ImmutableNumberValue;
import de.ii.ogcapi.styles.domain.ImmutableObjectValue;
import de.ii.ogcapi.styles.domain.ImmutableStringValue;
import de.ii.ogcapi.styles.domain.MbStyleExpression;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class MbStyleExpressionSerializer extends JsonSerializer<MbStyleExpression> {

  @Override
  public void serialize(MbStyleExpression value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    if (value instanceof ImmutableStringValue stringValue) {
      gen.writeString(stringValue.getValue());
    } else if (value instanceof ImmutableNumberValue numberValue) {
      gen.writeObject(numberValue.getValue());
    } else if (value instanceof ImmutableBooleanValue booleanValue) {
      gen.writeBoolean(booleanValue.getValue());
    } else if (value instanceof ImmutableNullValue) {
      gen.writeNull();
    } else if (value instanceof ImmutableArrayValue arrayValue) {
      List<MbStyleExpression> values = arrayValue.getValue();
      gen.writeStartArray();
      for (MbStyleExpression v : values) {
        serialize(v, gen, serializers);
      }
      gen.writeEndArray();
    } else if (value instanceof ImmutableObjectValue objectValue) {
      Map<String, MbStyleExpression> values = objectValue.getValue();
      gen.writeStartObject();
      for (Entry<String, MbStyleExpression> entry : values.entrySet()) {
        gen.writeFieldName(entry.getKey());
        serialize(entry.getValue(), gen, serializers);
      }
      gen.writeEndObject();
    } else {
      throw new IOException("Unexpected MapLibre expression type: " + value);
    }
  }
}
