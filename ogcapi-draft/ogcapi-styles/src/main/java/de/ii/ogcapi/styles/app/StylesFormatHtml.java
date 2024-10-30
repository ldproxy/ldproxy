/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.URICustomizer;
import de.ii.ogcapi.html.domain.FormatHtml;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.html.domain.NavigationDTO;
import de.ii.ogcapi.styles.domain.Styles;
import de.ii.ogcapi.styles.domain.StylesFormatExtension;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title HTML
 */
@Singleton
@AutoBind
public class StylesFormatHtml implements StylesFormatExtension, FormatHtml {

  private final I18n i18n;

  @Inject
  public StylesFormatHtml(I18n i18n) {
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
  public Object getStylesEntity(
      Styles styles,
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      ApiRequestContext requestContext) {
    String rootTitle = i18n.get("root", requestContext.getLanguage());
    String stylesTitle = i18n.get("stylesTitle", requestContext.getLanguage());
    String collectionsTitle = i18n.get("collectionsTitle", requestContext.getLanguage());

    URICustomizer resourceUri = requestContext.getUriCustomizer().copy().clearParameters();
    final List<NavigationDTO> breadCrumbs =
        collectionId.isPresent()
            ? new ImmutableList.Builder<NavigationDTO>()
                .add(
                    new NavigationDTO(
                        rootTitle,
                        homeUrl(apiData)
                            .orElse(
                                resourceUri
                                    .copy()
                                    .removeLastPathSegments(apiData.getSubPath().size() + 3)
                                    .toString())))
                .add(
                    new NavigationDTO(
                        apiData.getLabel(),
                        resourceUri.copy().removeLastPathSegments(3).toString()))
                .add(
                    new NavigationDTO(
                        collectionsTitle, resourceUri.copy().removeLastPathSegments(2).toString()))
                .add(
                    new NavigationDTO(
                        apiData.getCollections().get(collectionId.get()).getLabel(),
                        resourceUri.copy().removeLastPathSegments(1).toString()))
                .add(new NavigationDTO(stylesTitle))
                .build()
            : new ImmutableList.Builder<NavigationDTO>()
                .add(
                    new NavigationDTO(
                        rootTitle,
                        homeUrl(apiData)
                            .orElse(
                                resourceUri
                                    .copy()
                                    .removeLastPathSegments(apiData.getSubPath().size() + 1)
                                    .toString())))
                .add(
                    new NavigationDTO(
                        apiData.getLabel(),
                        resourceUri.copy().removeLastPathSegments(1).toString()))
                .add(new NavigationDTO(stylesTitle))
                .build();

    HtmlConfiguration htmlConfig = apiData.getExtension(HtmlConfiguration.class).orElse(null);

    return new ImmutableStylesView.Builder()
        .apiData(apiData)
        .styles(styles)
        .urlPrefix(requestContext.getStaticUrlPrefix())
        .rawLinks(styles.getLinks())
        .breadCrumbs(breadCrumbs)
        .htmlConfig(htmlConfig)
        .noIndex(isNoIndexEnabledForApi(apiData))
        .uriCustomizer(requestContext.getUriCustomizer().copy())
        .i18n(i18n)
        .description(i18n.get("stylesDescription", requestContext.getLanguage()))
        .title(i18n.get("stylesTitle", requestContext.getLanguage()))
        .language(requestContext.getLanguage())
        .user(requestContext.getUser())
        .build();
  }
}
