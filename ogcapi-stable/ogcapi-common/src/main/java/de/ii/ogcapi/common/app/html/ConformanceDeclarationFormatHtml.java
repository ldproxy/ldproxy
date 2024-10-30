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
import de.ii.ogcapi.common.domain.ConformanceDeclaration;
import de.ii.ogcapi.common.domain.ConformanceDeclarationFormatExtension;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
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
public class ConformanceDeclarationFormatHtml
    implements ConformanceDeclarationFormatExtension, FormatHtml {

  private final I18n i18n;

  @Inject
  public ConformanceDeclarationFormatHtml(I18n i18n) {
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
  public Object getEntity(
      ConformanceDeclaration conformanceDeclaration, OgcApi api, ApiRequestContext requestContext) {

    String rootTitle = i18n.get("root", requestContext.getLanguage());
    String conformanceDeclarationTitle =
        i18n.get("conformanceDeclarationTitle", requestContext.getLanguage());

    final URICustomizer uriCustomizer = requestContext.getUriCustomizer().copy().clearParameters();
    final List<NavigationDTO> breadCrumbs =
        new ImmutableList.Builder<NavigationDTO>()
            .add(
                new NavigationDTO(
                    rootTitle,
                    homeUrl(api.getData())
                        .orElse(
                            uriCustomizer
                                .copy()
                                .removeLastPathSegments(api.getData().getSubPath().size() + 1)
                                .toString())))
            .add(
                new NavigationDTO(
                    api.getData().getLabel(),
                    uriCustomizer.copy().removeLastPathSegments(1).toString()))
            .add(new NavigationDTO(conformanceDeclarationTitle))
            .build();

    HtmlConfiguration htmlConfig = api.getData().getExtension(HtmlConfiguration.class).orElse(null);

    OgcApiConformanceDeclarationView ogcApiConformanceDeclarationView =
        new ImmutableOgcApiConformanceDeclarationView.Builder()
            .conformanceDeclaration(conformanceDeclaration)
            .urlPrefix(requestContext.getStaticUrlPrefix())
            .htmlConfig(htmlConfig)
            .noIndex(isNoIndexEnabledForApi(api.getData()))
            .i18n(i18n)
            .rawLinks(conformanceDeclaration.getLinks())
            .description(
                conformanceDeclaration
                    .getDescription()
                    .orElse(
                        i18n.get(
                            "conformanceDeclarationDescription", requestContext.getLanguage())))
            .title(
                conformanceDeclaration
                    .getTitle()
                    .orElse(i18n.get("conformanceDeclarationTitle", requestContext.getLanguage())))
            .language(requestContext.getLanguage())
            .breadCrumbs(breadCrumbs)
            .uriCustomizer(requestContext.getUriCustomizer().copy())
            .user(requestContext.getUser())
            .build();

    return ogcApiConformanceDeclarationView;
  }
}
