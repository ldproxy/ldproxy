/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.versioned.features.domain.TimeMap;
import de.ii.ogcapi.versioned.features.domain.TimeMapFormatExtension;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * RFC 6690 CoRE Link Format representation of the Time Map. Each link is one comma-separated entry
 * of the form {@code <uri>;rel="...";title="...";datetime="..."}; the {@code datetime} link
 * attribute (RFC 7089, RFC 1123 HTTP-date) is added to every {@code memento} entry. The Versioned
 * Features draft (Time Map Link Format requirements class) expects this media type alongside JSON.
 */
@Singleton
@AutoBind
public class TimeMapFormatLinkFormat implements TimeMapFormatExtension {

  public static final ApiMediaType LINK_FORMAT_MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("application", "link-format"))
          .label("CoRE Link Format")
          .parameter("linkformat")
          .build();

  private static final ApiMediaTypeContent CONTENT =
      new ImmutableApiMediaTypeContent.Builder()
          .schema(new StringSchema().example("</...>;rel=\"self\"\n</...>;rel=\"memento\"..."))
          .schemaRef("#/components/schemas/linkFormat")
          .ogcApiMediaType(LINK_FORMAT_MEDIA_TYPE)
          .build();

  private final I18n i18n;

  @Inject
  TimeMapFormatLinkFormat(I18n i18n) {
    this.i18n = i18n;
  }

  @Override
  public ApiMediaType getMediaType() {
    return LINK_FORMAT_MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return CONTENT;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    // No format-specific config; gate the format on the building block enabling the resource.
    return VersionedFeaturesConfiguration.class;
  }

  @Override
  public Object getEntity(TimeMap timeMap, OgcApi api, ApiRequestContext requestContext) {
    var language = requestContext.getLanguage();
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    // Standard self/alternate links from DefaultLinksGenerator
    for (de.ii.ogcapi.foundation.domain.Link l : timeMap.getResourceLinks()) {
      appendLink(sb, l.getHref(), l.getRel(), l.getTitle(), null, first);
      first = false;
    }
    appendLink(
        sb, timeMap.getFeatureHref(), "original", i18n.get("originalLink", language), null, first);
    first = false;
    DateTimeFormatter httpDate = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT"));
    for (TimeMap.Memento m : timeMap.getMementos()) {
      appendLink(
          sb,
          m.getHref(),
          "memento",
          i18n.get("mementoLink", language),
          httpDate.format(m.getStart()),
          false);
    }
    appendLink(
        sb,
        timeMap.getFeatureHref() + "?datetime=" + timeMap.getLatestStart().toString(),
        "latest-version",
        i18n.get("latestVersionLink", language),
        null,
        false);
    return sb.toString();
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
