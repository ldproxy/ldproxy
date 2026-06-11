/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.versioned.features.domain.EncodingContextTimeMap;
import de.ii.ogcapi.versioned.features.domain.FeatureEncoderTimeMap;
import de.ii.ogcapi.versioned.features.domain.TimeMap;
import de.ii.xtraplatform.streams.domain.OutputStreamToByteConsumer;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

public class FeatureEncoderTimeMapJson extends FeatureEncoderTimeMap {

  private static final JsonFactory JSON_FACTORY = new JsonFactory();
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
  private static final DateTimeFormatter HTTP_DATE =
      DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT"));

  public FeatureEncoderTimeMapJson(EncodingContextTimeMap encodingContext) {
    super(encodingContext);
  }

  @Override
  protected void encode(TimeMap timeMap) throws IOException {
    I18n i18n = encodingContext.getI18n();
    Optional<Locale> language = encodingContext.getRequestContext().getLanguage();

    try (JsonGenerator json =
        JSON_FACTORY.createGenerator(new OutputStreamToByteConsumer(this::push))) {
      json.setCodec(OBJECT_MAPPER);
      if (encodingContext.getPrettify()) {
        json.useDefaultPrettyPrinter();
      }

      json.writeStartObject();
      json.writeArrayFieldStart("links");
      for (Link l : timeMap.getResourceLinks()) {
        writeLink(
            json,
            l.getHref(),
            l.getRel(),
            l.getTitle(),
            null,
            l.getType() == null || l.getType().isBlank() ? null : l.getType());
      }
      writeLink(
          json,
          timeMap.getFeatureHref(),
          "original",
          i18n.get("originalLink", language),
          null,
          null);
      for (TimeMap.Memento m : timeMap.getMementos()) {
        writeLink(
            json,
            m.getHref(),
            "memento",
            i18n.get("mementoLink", language),
            HTTP_DATE.format(m.getStart()),
            null);
      }
      if (timeMap.getLatestStart() != null) {
        writeLink(
            json,
            timeMap.getFeatureHref() + "?datetime=" + timeMap.getLatestStart().toString(),
            "latest-version",
            i18n.get("latestVersionLink", language),
            null,
            null);
      }
      json.writeEndArray();
      json.writeEndObject();
    }
  }

  private static void writeLink(
      JsonGenerator json, String href, String rel, String title, String datetime, String type)
      throws IOException {
    json.writeStartObject();
    json.writeStringField("href", href);
    json.writeStringField("rel", rel);
    if (type != null && !type.isBlank()) {
      json.writeStringField("type", type);
    }
    if (title != null) {
      json.writeStringField("title", title);
    }
    if (datetime != null) {
      json.writeStringField("datetime", datetime);
    }
    json.writeEndObject();
  }
}
