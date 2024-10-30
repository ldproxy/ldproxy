/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.app.html;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.common.domain.LandingPage;
import de.ii.ogcapi.common.domain.LandingPageFormatExtension;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.URICustomizer;
import de.ii.ogcapi.html.domain.FormatHtml;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.html.domain.NavigationDTO;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title HTML
 */
@Singleton
@AutoBind
public class LandingPageFormatHtml
    implements LandingPageFormatExtension, ConformanceClass, FormatHtml {

  private final I18n i18n;

  @Inject
  public LandingPageFormatHtml(I18n i18n) {
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
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of("http://www.opengis.net/spec/ogcapi-common-1/1.0/conf/html");
  }

  private boolean isNoIndexEnabledForApi(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(HtmlConfiguration.class)
        .map(HtmlConfiguration::getNoIndexEnabled)
        .orElse(true);
  }

  @Override
  public Object getEntity(
      LandingPage apiLandingPage, OgcApi api, ApiRequestContext requestContext) {

    String rootTitle = i18n.get("root", requestContext.getLanguage());

    URICustomizer resourceUri = requestContext.getUriCustomizer().copy().clearParameters();
    final List<NavigationDTO> breadCrumbs =
        new ImmutableList.Builder<NavigationDTO>()
            .add(
                new NavigationDTO(
                    rootTitle,
                    homeUrl(api.getData())
                        .orElse(
                            resourceUri
                                .copy()
                                .removeLastPathSegments(api.getData().getSubPath().size())
                                .toString())))
            .add(new NavigationDTO(api.getData().getLabel()))
            .build();

    HtmlConfiguration htmlConfig = api.getData().getExtension(HtmlConfiguration.class).orElse(null);

    OgcApiLandingPageView landingPageView =
        new ImmutableOgcApiLandingPageView.Builder()
            .apiData(api.getData())
            .breadCrumbs(breadCrumbs)
            .apiLandingPage(apiLandingPage)
            .urlPrefix(requestContext.getStaticUrlPrefix())
            .rawLinks(apiLandingPage.getLinks())
            .htmlConfig(htmlConfig)
            .uriCustomizer(requestContext.getUriCustomizer().copy())
            .noIndex(isNoIndexEnabledForApi(api.getData()))
            .i18n(i18n)
            .title(apiLandingPage.getTitle().orElse(api.getData().getId()))
            .description(apiLandingPage.getDescription().orElse(null))
            .extent(apiLandingPage.getExtent())
            .language(requestContext.getLanguage())
            .user(requestContext.getUser())
            .build();

    return landingPageView;
  }
}
