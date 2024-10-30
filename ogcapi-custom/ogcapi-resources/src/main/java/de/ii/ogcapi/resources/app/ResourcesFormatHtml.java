/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.resources.app;

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
import de.ii.ogcapi.resources.domain.ResourcesFormatExtension;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title HTML
 */
@Singleton
@AutoBind
public class ResourcesFormatHtml implements ResourcesFormatExtension, FormatHtml {

  private final I18n i18n;

  @Inject
  public ResourcesFormatHtml(I18n i18n) {
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
  public Object getResourcesEntity(
      Resources resources, OgcApiDataV2 apiData, ApiRequestContext requestContext) {
    String rootTitle = i18n.get("root", requestContext.getLanguage());
    String resourcesTitle = i18n.get("resourcesTitle", requestContext.getLanguage());

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
                                .removeLastPathSegments(apiData.getSubPath().size() + 1)
                                .toString())))
            .add(
                new NavigationDTO(
                    apiData.getLabel(), resourceUri.copy().removeLastPathSegments(1).toString()))
            .add(new NavigationDTO(resourcesTitle))
            .build();

    HtmlConfiguration htmlConfig = apiData.getExtension(HtmlConfiguration.class).orElse(null);

    return new ImmutableResourcesView.Builder()
        .apiData(apiData)
        .breadCrumbs(breadCrumbs)
        .htmlConfig(htmlConfig)
        .noIndex(isNoIndexEnabledForApi(apiData))
        .urlPrefix(requestContext.getStaticUrlPrefix())
        .rawLinks(resources.getLinks())
        .title(i18n.get("resourcesTitle", requestContext.getLanguage()))
        .description(i18n.get("resourcesDescription", requestContext.getLanguage()))
        .none(i18n.get("none", requestContext.getLanguage()))
        .resources(resources.getResources())
        .uriCustomizer(requestContext.getUriCustomizer().copy())
        .user(requestContext.getUser())
        .build();
  }
}
