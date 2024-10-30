/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.codelists.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.codelists.domain.CodelistFormatExtension;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.URICustomizer;
import de.ii.ogcapi.html.domain.FormatHtml;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.html.domain.NavigationDTO;
import de.ii.xtraplatform.codelists.domain.Codelist;
import java.util.AbstractMap.SimpleEntry;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title HTML
 */
@Singleton
@AutoBind
public class CodelistFormatHtml implements CodelistFormatExtension, FormatHtml {

  private final I18n i18n;

  @Inject
  public CodelistFormatHtml(I18n i18n) {
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

  private boolean isNoIndexEnabledForApi(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(HtmlConfiguration.class)
        .map(HtmlConfiguration::getNoIndexEnabled)
        .orElse(true);
  }

  @Override
  public Object getCodelist(
      Codelist codelist,
      String codelistId,
      OgcApiDataV2 apiData,
      ApiRequestContext requestContext,
      List<Link> links) {
    String rootTitle = i18n.get("root", requestContext.getLanguage());
    String codelistsTitle = i18n.get("codelistsTitle", requestContext.getLanguage());
    String label = codelist.getLabel().orElse(codelistId);

    URICustomizer resourceUri = requestContext.getUriCustomizer().copy().clearParameters();
    final List<NavigationDTO> breadCrumbs =
        new ImmutableList.Builder<NavigationDTO>()
            .add(
                new NavigationDTO(
                    rootTitle,
                    homeUrl(apiData)
                        .orElse(
                            resourceUri
                                .copy()
                                .removeLastPathSegments(apiData.getSubPath().size() + 2)
                                .toString())))
            .add(
                new NavigationDTO(
                    apiData.getLabel(), resourceUri.copy().removeLastPathSegments(2).toString()))
            .add(
                new NavigationDTO(
                    codelistsTitle, resourceUri.copy().removeLastPathSegments(1).toString()))
            .add(new NavigationDTO(label))
            .build();

    HtmlConfiguration htmlConfig = apiData.getExtension(HtmlConfiguration.class).orElse(null);

    boolean intAsKey =
        codelist.getEntries().keySet().stream()
            .allMatch(
                key -> {
                  try {
                    Integer.parseInt(key);
                    return true;
                  } catch (Exception e) {
                    return false;
                  }
                });

    List<SimpleEntry<String, String>> codelistEntries =
        codelist.getEntries().entrySet().stream()
            .sorted(
                intAsKey
                    ? Comparator.comparingInt(entry -> Integer.parseInt(entry.getKey()))
                    : Map.Entry.comparingByKey())
            .map(e -> new SimpleEntry<>(e.getKey(), e.getValue()))
            .collect(Collectors.toList());

    return new ImmutableCodelistView.Builder()
        .apiData(apiData)
        .breadCrumbs(breadCrumbs)
        .htmlConfig(htmlConfig)
        .noIndex(isNoIndexEnabledForApi(apiData))
        .urlPrefix(requestContext.getStaticUrlPrefix())
        .rawLinks(links)
        .title(
            i18n.get("codelistTitle", requestContext.getLanguage()).replace("{{codelist}}", label))
        .none(i18n.get("none", requestContext.getLanguage()))
        .codelistEntries(codelistEntries)
        .fallback(codelist.getFallback())
        .uriCustomizer(requestContext.getUriCustomizer().copy())
        .user(requestContext.getUser())
        .build();
  }
}
