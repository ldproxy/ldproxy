/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.html.domain.NavigationDTO;
import de.ii.ogcapi.versioned.features.domain.EncodingContextTimeMap;
import de.ii.ogcapi.versioned.features.domain.FeatureEncoderTimeMap;
import de.ii.ogcapi.versioned.features.domain.TimeMap;
import de.ii.xtraplatform.streams.domain.OutputStreamToByteConsumer;
import de.ii.xtraplatform.web.domain.MustacheRenderer;
import de.ii.xtraplatform.web.domain.URICustomizer;
import io.dropwizard.views.common.View;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class FeatureEncoderTimeMapHtml extends FeatureEncoderTimeMap {

  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneId.of("UTC"));

  // A DATE-typed interval value is displayed as the date it is instead of promoting it to a
  // start-of-day timestamp.
  private static String label(String rawValue, boolean isDate, java.time.Instant instant) {
    return isDate ? rawValue : DATE_FORMAT.format(instant);
  }

  private final MustacheRenderer mustacheRenderer;
  private final Optional<String> homeUrl;

  public FeatureEncoderTimeMapHtml(
      EncodingContextTimeMap encodingContext,
      MustacheRenderer mustacheRenderer,
      Optional<String> homeUrl) {
    super(encodingContext);
    this.mustacheRenderer = mustacheRenderer;
    this.homeUrl = homeUrl;
  }

  @Override
  protected void encode(TimeMap timeMap) throws IOException {
    ApiRequestContext requestContext = encodingContext.getRequestContext();
    OgcApiDataV2 apiData = encodingContext.getApi().getData();
    I18n i18n = encodingContext.getI18n();
    Locale language = requestContext.getLanguage().orElse(Locale.ENGLISH);
    HtmlConfiguration htmlConfig = apiData.getExtension(HtmlConfiguration.class).orElse(null);

    String title =
        i18n.get("versionsTitle", requestContext.getLanguage()) + " — " + timeMap.getFeatureId();
    String description = i18n.get("versionsDescription", requestContext.getLanguage());

    URICustomizer uri = requestContext.getUriCustomizer().copy().clearParameters();

    // Standard breadcrumb chain: Home / Service / Data / <collection> / <featureId> / Versions
    // URI ends with `.../collections/<cid>/items/<fid>/versions` (5 segments under the service).
    // removeLastPathSegments counts back from the end:
    //   1 → feature page  /.../items/<fid>
    //   3 → collection    /.../collections/<cid>
    //   4 → collections   /.../collections
    //   5 → service root  /<service>
    List<NavigationDTO> breadCrumbs =
        new ImmutableList.Builder<NavigationDTO>()
            .add(
                new NavigationDTO(
                    i18n.get("root", requestContext.getLanguage()),
                    homeUrl.orElse(
                        uri.copy()
                            .removeLastPathSegments(apiData.getSubPath().size() + 5)
                            .toString())))
            .add(
                new NavigationDTO(
                    apiData.getLabel(), uri.copy().removeLastPathSegments(5).toString()))
            .add(
                new NavigationDTO(
                    i18n.get("collectionsTitle", requestContext.getLanguage()),
                    uri.copy().removeLastPathSegments(4).toString()))
            .add(
                new NavigationDTO(
                    apiData.getCollections().get(timeMap.getCollectionId()).getLabel(),
                    uri.copy().removeLastPathSegments(3).toString()))
            .add(
                new NavigationDTO(
                    timeMap.getFeatureId(), uri.copy().removeLastPathSegments(1).toString()))
            .add(new NavigationDTO(i18n.get("versionsTitle", requestContext.getLanguage())))
            .build();

    List<TimeMapView.Entry> entries =
        timeMap.getMementos().stream()
            .map(
                m -> {
                  ImmutableEntry.Builder b =
                      new ImmutableEntry.Builder()
                          .href(m.getHref())
                          .startLabel(label(m.getStartValue(), m.isStartDate(), m.getStart()))
                          .startInstant(m.getStart());
                  if (Objects.nonNull(m.getEnd())) {
                    b.endLabel(label(m.getEndValue(), m.isEndDate(), m.getEnd()));
                  }
                  return (TimeMapView.Entry) b.build();
                })
            .toList();

    View view =
        new ImmutableTimeMapView.Builder()
            .apiData(apiData)
            .htmlConfig(htmlConfig)
            .basePath(requestContext.getBasePath())
            .apiPath(requestContext.getApiPath())
            .uriCustomizer(requestContext.getUriCustomizer().copy())
            .breadCrumbs(breadCrumbs)
            .title(title)
            .description(description)
            .featureId(timeMap.getFeatureId())
            .featureHref(timeMap.getFeatureHref())
            .entries(entries)
            .i18n(i18n)
            .language(language)
            // Resource self/alternate links — OgcApiView.getFormats() picks the `alternate` ones
            // out of rawLinks to render the format-link bar at the top of the page.
            .rawLinks(timeMap.getResourceLinks())
            .user(requestContext.getUser())
            .build();

    OutputStreamWriter writer =
        new OutputStreamWriter(new OutputStreamToByteConsumer(this::push), StandardCharsets.UTF_8);
    mustacheRenderer.render(view, writer);
    writer.flush();
  }
}
