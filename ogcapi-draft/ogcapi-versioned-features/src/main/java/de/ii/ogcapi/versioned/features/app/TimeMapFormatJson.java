/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.versioned.features.domain.TimeMap;
import de.ii.ogcapi.versioned.features.domain.TimeMapFormatExtension;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration;
import io.swagger.v3.oas.models.media.ObjectSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * JSON representation of the Time Map. Encodes the version intervals as {@code memento} link
 * entries with their RFC 7089 {@code datetime} attribute (RFC 1123 HTTP-date), plus {@code self} /
 * {@code original} / {@code latest-version} entries.
 */
@Singleton
@AutoBind
public class TimeMapFormatJson implements TimeMapFormatExtension {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final ApiMediaTypeContent CONTENT =
      new ImmutableApiMediaTypeContent.Builder()
          .schema(new ObjectSchema())
          .schemaRef("#/components/schemas/TimeMap")
          .ogcApiMediaType(ApiMediaType.JSON_MEDIA_TYPE)
          .build();

  private final I18n i18n;

  @Inject
  TimeMapFormatJson(I18n i18n) {
    this.i18n = i18n;
  }

  @Override
  public ApiMediaType getMediaType() {
    return ApiMediaType.JSON_MEDIA_TYPE;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    // Gate on Versioned Features being enabled rather than the (sometimes absent) JsonConfiguration
    // — the format is logically a Time Map representation, not a generic JSON encoder.
    return VersionedFeaturesConfiguration.class;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return CONTENT;
  }

  @Override
  public Object getEntity(TimeMap timeMap, OgcApi api, ApiRequestContext requestContext) {
    var language = requestContext.getLanguage();

    ObjectNode root = JSON.createObjectNode();
    ArrayNode links = root.putArray("links");
    // Standard self/alternate links from DefaultLinksGenerator
    for (de.ii.ogcapi.foundation.domain.Link l : timeMap.getResourceLinks()) {
      links.add(
          linkNode(
              l.getHref(),
              l.getRel(),
              l.getTitle(),
              null,
              l.getType().isBlank() ? null : l.getType()));
    }
    links.add(
        linkNode(
            timeMap.getFeatureHref(), "original", i18n.get("originalLink", language), null, null));
    DateTimeFormatter httpDate = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT"));
    for (TimeMap.Memento m : timeMap.getMementos()) {
      links.add(
          linkNode(
              m.getHref(),
              "memento",
              i18n.get("mementoLink", language),
              httpDate.format(m.getStart()),
              null));
    }
    String latestHref =
        timeMap.getFeatureHref() + "?datetime=" + timeMap.getLatestStart().toString();
    links.add(
        linkNode(
            latestHref, "latest-version", i18n.get("latestVersionLink", language), null, null));
    return root.toString();
  }

  private static ObjectNode linkNode(
      String href, String rel, String title, String datetime, String type) {
    ObjectNode n = JSON.createObjectNode();
    n.put("href", href);
    n.put("rel", rel);
    if (type != null && !type.isBlank()) n.put("type", type);
    if (title != null) n.put("title", title);
    if (datetime != null) n.put("datetime", datetime);
    return n;
  }
}
