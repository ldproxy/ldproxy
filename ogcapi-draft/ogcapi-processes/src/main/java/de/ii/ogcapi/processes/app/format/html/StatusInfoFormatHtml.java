/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app.format.html;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.html.domain.FormatHtml;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.html.domain.NavigationDTO;
import de.ii.ogcapi.processes.domain.format.StatusInfoFormatExtension;
import de.ii.ogcapi.processes.domain.model.ogc.OgcStatusInfo;
import de.ii.xtraplatform.web.domain.URICustomizer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

// ToDo Proper language handling

/**
 * @title HTML
 */
@Singleton
@AutoBind
public class StatusInfoFormatHtml
    implements StatusInfoFormatExtension, FormatHtml, ConformanceClass {

  private final I18n i18n;

  @Inject
  public StatusInfoFormatHtml(I18n i18n) {
    this.i18n = i18n;
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    if (isEnabledForApi(apiData)) {
      return ImmutableList.of("https://www.opengis.net/spec/ogcapi-processes-1/2.0/conf/html");
    }

    return ImmutableList.of();
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
  public Object getEntity(OgcStatusInfo statusInfo, OgcApi api, ApiRequestContext requestContext) {
    String rootTitle = i18n.get("root", requestContext.getLanguage());
    String jobId = statusInfo.getId();
    // ToDo
    String jobListTitle = i18n.get("jobListTitle", requestContext.getLanguage());

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
                                .removeLastPathSegments(api.getData().getSubPath().size() + 2)
                                .toString())))
            .add(
                new NavigationDTO(
                    api.getData().getLabel(),
                    resourceUri.copy().removeLastPathSegments(2).toString()))
            .add(new NavigationDTO(jobListTitle))
            .add(new NavigationDTO(jobId))
            .build();

    HtmlConfiguration htmlConfig = api.getData().getExtension(HtmlConfiguration.class).orElse(null);
    return ImmutableStatusInfoView.builder()
        .basePath(requestContext.getBasePath())
        .apiPath(requestContext.getApiPath())
        .noIndex(isNoIndexEnabledForApi(api.getData()))
        .breadCrumbs(breadCrumbs)
        .title(jobId)
        .apiData(api.getData())
        .user(requestContext.getUser())
        .language(requestContext.getLanguage())
        .htmlConfig(htmlConfig)
        .uriCustomizer(requestContext.getUriCustomizer().copy())
        .i18n(i18n)
        .rawLinks(statusInfo.getLinks())
        .statusInfo(statusInfo)
        .build();
  }
}
