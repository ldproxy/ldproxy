/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.html.domain.FormatHtml;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.html.domain.NavigationDTO;
import de.ii.ogcapi.versioned.features.domain.TimeMap;
import de.ii.ogcapi.versioned.features.domain.TimeMapFormatExtension;
import de.ii.xtraplatform.web.domain.URICustomizer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * HTML representation of the Time Map. Reuses the standard {@code header}/{@code footer} so the
 * page integrates with the existing breadcrumb / format-link bar above, then lists each version as
 * a row with its start timestamp linking back to {@code items/{id}?datetime=<start>}.
 */
@Singleton
@AutoBind
public class TimeMapFormatHtml implements TimeMapFormatExtension, FormatHtml {

  private final I18n i18n;

  @Inject
  TimeMapFormatHtml(I18n i18n) {
    this.i18n = i18n;
  }

  @Override
  public ApiMediaType getMediaType() {
    return ApiMediaType.HTML_MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return FormatExtension.HTML_CONTENT;
  }

  @Override
  public Object getEntity(TimeMap timeMap, OgcApi api, ApiRequestContext requestContext) {
    OgcApiDataV2 apiData = api.getData();
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
                    homeUrl(apiData)
                        .orElse(
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

    DateTimeFormatter dateFormat =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneId.of("UTC"));

    List<TimeMapView.Entry> entries =
        timeMap.getMementos().stream()
            .map(
                m -> {
                  ImmutableEntry.Builder b =
                      new ImmutableEntry.Builder()
                          .href(m.getHref())
                          .startLabel(dateFormat.format(m.getStart()))
                          .startInstant(m.getStart());
                  if (Objects.nonNull(m.getEnd())) {
                    b.endLabel(dateFormat.format(m.getEnd()));
                  }
                  return (TimeMapView.Entry) b.build();
                })
            .toList();

    return new ImmutableTimeMapView.Builder()
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
  }
}
