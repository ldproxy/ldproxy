/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.features.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;

/**
 * The output stream that a query with {@link FeatureRecordsFormat} writes to: every line is parsed
 * into a {@link FeatureRecord} and passed on, so the features are processed while the query runs.
 */
class FeatureRecordReader extends OutputStream {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Consumer<FeatureRecord> consumer;
  private final LongConsumer numberMatched;
  private final ByteArrayOutputStream line;
  private final WKBReader wkbReader;

  FeatureRecordReader(Consumer<FeatureRecord> consumer) {
    this(consumer, count -> {});
  }

  /**
   * @param consumer receives the features
   * @param numberMatched receives the number of matching features of a query with hits only, -1 if
   *     the number is unknown
   */
  FeatureRecordReader(Consumer<FeatureRecord> consumer, LongConsumer numberMatched) {
    this.consumer = consumer;
    this.numberMatched = numberMatched;
    this.line = new ByteArrayOutputStream();
    this.wkbReader = new WKBReader();
  }

  @Override
  public void write(int b) throws IOException {
    if (b == '\n') {
      flushLine();
    } else {
      line.write(b);
    }
  }

  @Override
  public void write(byte[] bytes, int offset, int length) throws IOException {
    int start = offset;
    int end = offset + length;
    for (int i = offset; i < end; i++) {
      if (bytes[i] == '\n') {
        line.write(bytes, start, i - start);
        flushLine();
        start = i + 1;
      }
    }
    line.write(bytes, start, end - start);
  }

  @Override
  public void close() throws IOException {
    flushLine();
  }

  private void flushLine() throws IOException {
    if (line.size() == 0) {
      return;
    }
    JsonNode node = MAPPER.readTree(line.toByteArray());
    line.reset();
    if (node.has(FeatureRecord.NUMBER_MATCHED)) {
      numberMatched.accept(node.get(FeatureRecord.NUMBER_MATCHED).asLong(-1));
      return;
    }
    consumer.accept(parse(node));
  }

  private FeatureRecord parse(JsonNode node) throws IOException {
    Map<String, List<Object>> values = new LinkedHashMap<>();
    node.path(FeatureRecord.PROPERTIES)
        .properties()
        .forEach(
            entry -> {
              List<Object> list = new ArrayList<>();
              entry.getValue().forEach(value -> list.add(value(value)));
              values.put(entry.getKey(), list);
            });

    Optional<Geometry> geometry = Optional.empty();
    if (node.path(FeatureRecord.GEOMETRY).isTextual()) {
      try {
        geometry =
            Optional.of(
                wkbReader.read(
                    Base64.getDecoder().decode(node.path(FeatureRecord.GEOMETRY).asText())));
      } catch (ParseException e) {
        throw new IOException("Invalid geometry in a feature record.", e);
      }
    }

    return new FeatureRecord(
        node.path(FeatureRecord.COLLECTION).asText(null),
        node.path(FeatureRecord.ID).asText(null),
        values,
        geometry);
  }

  private static Object value(JsonNode value) {
    if (value.isIntegralNumber()) {
      return value.asLong();
    }
    if (value.isNumber()) {
      return value.asDouble();
    }
    if (value.isBoolean()) {
      return value.asBoolean();
    }
    return value.asText();
  }
}
