/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.app.html;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.collections.domain.CollectionFormatExtension;
import de.ii.ogcapi.collections.domain.OgcApiCollection;
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
public class CollectionFormatHtml implements CollectionFormatExtension, FormatHtml {

  private final I18n i18n;

  @Inject
  public CollectionFormatHtml(I18n i18n) {
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

  private boolean showCollectionDescriptionsInOverview(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(HtmlConfiguration.class)
        .map(HtmlConfiguration::getCollectionDescriptionsInOverview)
        .orElse(false);
  }

  @Override
  public Object getEntity(
      OgcApiCollection ogcApiCollection, OgcApi api, ApiRequestContext requestContext) {

    String rootTitle = i18n.get("root", requestContext.getLanguage());
    String collectionsTitle = i18n.get("collectionsTitle", requestContext.getLanguage());

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
            .add(
                new NavigationDTO(
                    collectionsTitle, resourceUri.copy().removeLastPathSegments(1).toString()))
            .add(new NavigationDTO(ogcApiCollection.getTitle().orElse(ogcApiCollection.getId())))
            .build();

    HtmlConfiguration htmlConfig =
        api.getData()
            .getCollections()
            .get(ogcApiCollection.getId())
            .getExtension(HtmlConfiguration.class)
            .orElse(null);

    OgcApiCollectionView collectionView =
        new ImmutableOgcApiCollectionView.Builder()
            .apiData(api.getData())
            .breadCrumbs(breadCrumbs)
            .htmlConfig(htmlConfig)
            .noIndex(isNoIndexEnabledForApi(api.getData()))
            .urlPrefix(requestContext.getStaticUrlPrefix())
            .rawLinks(ogcApiCollection.getLinks())
            .title(ogcApiCollection.getTitle().orElse(ogcApiCollection.getId()))
            .description(ogcApiCollection.getDescription().orElse(null))
            .uriCustomizer(requestContext.getUriCustomizer().copy())
            .extent(ogcApiCollection.getExtent())
            .language(requestContext.getLanguage())
            .collection(ogcApiCollection)
            .i18n(i18n)
            .hasGeometry(api.getSpatialExtent(ogcApiCollection.getId()).isPresent())
            .user(requestContext.getUser())
            .build();

    return collectionView;
  }
}
