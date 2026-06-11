/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.versioned.features.domain.EncodingContextTimeMap;
import de.ii.ogcapi.versioned.features.domain.FeatureEncoderTimeMap;
import de.ii.ogcapi.versioned.features.domain.TimeMap;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class FeatureEncoderTimeMapLinkFormat extends FeatureEncoderTimeMap {

  private static final DateTimeFormatter HTTP_DATE =
      DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT"));

  public FeatureEncoderTimeMapLinkFormat(EncodingContextTimeMap encodingContext) {
    super(encodingContext);
  }

  @Override
  protected void encode(TimeMap timeMap) {
    I18n i18n = encodingContext.getI18n();
    Optional<java.util.Locale> language = encodingContext.getRequestContext().getLanguage();

    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (Link l : timeMap.getResourceLinks()) {
      appendLink(sb, l.getHref(), l.getRel(), l.getTitle(), null, first);
      first = false;
    }
    appendLink(
        sb, timeMap.getFeatureHref(), "original", i18n.get("originalLink", language), null, first);
    first = false;
    for (TimeMap.Memento m : timeMap.getMementos()) {
      appendLink(
          sb,
          m.getHref(),
          "memento",
          i18n.get("mementoLink", language),
          HTTP_DATE.format(m.getStart()),
          false);
    }
    if (timeMap.getLatestStart() != null) {
      appendLink(
          sb,
          timeMap.getFeatureHref() + "?datetime=" + timeMap.getLatestStart().toString(),
          "latest-version",
          i18n.get("latestVersionLink", language),
          null,
          false);
    }
    push(sb.toString().getBytes(StandardCharsets.UTF_8));
  }

  // RFC 6690 §2: entries separated by `,`. Each entry: <uri-reference>;<link-param>=<value>...
  // Quoted strings use double quotes; embedded quotes and backslashes are escaped.
  private static void appendLink(
      StringBuilder sb, String href, String rel, String title, String datetime, boolean first) {
    if (!first) {
      sb.append(",\n");
    }
    sb.append('<').append(href).append('>');
    sb.append(";rel=\"").append(quote(rel)).append('"');
    if (title != null && !title.isEmpty()) {
      sb.append(";title=\"").append(quote(title)).append('"');
    }
    if (datetime != null) {
      sb.append(";datetime=\"").append(quote(datetime)).append('"');
    }
  }

  private static String quote(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
