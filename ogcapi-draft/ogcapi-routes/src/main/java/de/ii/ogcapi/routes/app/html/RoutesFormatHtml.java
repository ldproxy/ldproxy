/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.routes.app.html;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.URICustomizer;
import de.ii.ogcapi.html.domain.FormatHtml;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.html.domain.NavigationDTO;
import de.ii.ogcapi.routes.domain.HtmlForm;
import de.ii.ogcapi.routes.domain.HtmlFormDefaults;
import de.ii.ogcapi.routes.domain.ImmutableHtmlFormDefaults;
import de.ii.ogcapi.routes.domain.Routes;
import de.ii.ogcapi.routes.domain.RoutesFormatExtension;
import de.ii.ogcapi.routes.domain.RoutingConfiguration;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title HTML
 */
@Singleton
@AutoBind
public class RoutesFormatHtml implements RoutesFormatExtension, FormatHtml {

  private final I18n i18n;

  @Inject
  public RoutesFormatHtml(I18n i18n) {
    this.i18n = i18n;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return RoutingConfiguration.class;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(RoutingConfiguration.class)
        .filter(RoutingConfiguration::isEnabled)
        .map(RoutingConfiguration::getHtml)
        .filter(HtmlForm::isEnabled)
        .isPresent();
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
  public Object getRoutesEntity(Routes routes, OgcApi api, ApiRequestContext requestContext) {
    String rootTitle = i18n.get("root", requestContext.getLanguage());
    String routesTitle = i18n.get("routesTitle", requestContext.getLanguage());

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
            .add(new NavigationDTO(routesTitle))
            .build();

    HtmlConfiguration htmlConfig = api.getData().getExtension(HtmlConfiguration.class).orElse(null);

    HtmlFormDefaults htmlDefaults =
        api.getData()
            .getExtension(RoutingConfiguration.class)
            .map(RoutingConfiguration::getHtml)
            .flatMap(HtmlForm::getDefaults)
            .orElse(ImmutableHtmlFormDefaults.builder().build());

    return new ImmutableRoutesView.Builder()
        .apiData(api.getData())
        .bbox(
            api.getSpatialExtent()
                .map(
                    boundingBox ->
                        ImmutableMap.of(
                            "minLng", Double.toString(boundingBox.getXmin()),
                            "minLat", Double.toString(boundingBox.getYmin()),
                            "maxLng", Double.toString(boundingBox.getXmax()),
                            "maxLat", Double.toString(boundingBox.getYmax())))
                .orElse(null))
        .routes(routes)
        .htmlDefaults(htmlDefaults)
        .breadCrumbs(breadCrumbs)
        .urlPrefix(requestContext.getStaticUrlPrefix())
        .htmlConfig(htmlConfig)
        .noIndex(isNoIndexEnabledForApi(api.getData()))
        .i18n(i18n)
        .language(requestContext.getLanguage())
        .rawLinks(routes.getLinks())
        .title(i18n.get("routesTitle", requestContext.getLanguage()))
        .description(
            api.getData()
                    .getExtension(RoutingConfiguration.class)
                    .map(RoutingConfiguration::supportsObstacles)
                    .orElse(false)
                ? String.format(
                    "%s %s",
                    i18n.get("routesDescription", requestContext.getLanguage()),
                    i18n.get("routesDescriptionObstacles", requestContext.getLanguage()))
                : i18n.get("routesDescription", requestContext.getLanguage()))
        .uriCustomizer(requestContext.getUriCustomizer().copy())
        .user(requestContext.getUser())
        .build();
  }

  private boolean isNoIndexEnabledForApi(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(HtmlConfiguration.class)
        .map(HtmlConfiguration::getNoIndexEnabled)
        .orElse(true);
  }
}
