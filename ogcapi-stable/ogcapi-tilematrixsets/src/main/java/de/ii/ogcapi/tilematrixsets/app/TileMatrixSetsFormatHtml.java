/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tilematrixsets.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
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
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSets;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetsFormatExtension;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title HTML
 */
@Singleton
@AutoBind
public class TileMatrixSetsFormatHtml implements TileMatrixSetsFormatExtension, FormatHtml {

  private final I18n i18n;

  @Inject
  public TileMatrixSetsFormatHtml(I18n i18n) {
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
      TileMatrixSets tileMatrixSets, OgcApi api, ApiRequestContext requestContext) {
    String rootTitle = i18n.get("root", requestContext.getLanguage());
    String tileMatrixSetsTitle = i18n.get("tileMatrixSetsTitle", requestContext.getLanguage());

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
                                .removeLastPathSegments(api.getData().getSubPath().size() + 1)
                                .toString())))
            .add(
                new NavigationDTO(
                    api.getData().getLabel(),
                    resourceUri.copy().removeLastPathSegments(1).toString()))
            .add(new NavigationDTO(tileMatrixSetsTitle))
            .build();

    HtmlConfiguration htmlConfig = api.getData().getExtension(HtmlConfiguration.class).orElse(null);

    return ImmutableTileMatrixSetsView.builder()
        .apiData(api.getData())
        .urlPrefix(requestContext.getStaticUrlPrefix())
        .tileMatrixSets(tileMatrixSets.getTileMatrixSets())
        .breadCrumbs(breadCrumbs)
        .htmlConfig(htmlConfig)
        .noIndex(isNoIndexEnabledForApi(api.getData()))
        .uriCustomizer(requestContext.getUriCustomizer().copy())
        .i18n(i18n)
        .language(requestContext.getLanguage().orElse(null))
        .description(i18n.get("tileMatrixSetsDescription", requestContext.getLanguage()))
        .title(i18n.get("tileMatrixSetsTitle", requestContext.getLanguage()))
        .rawLinks(tileMatrixSets.getLinks())
        .user(requestContext.getUser())
        .build();
  }
}
