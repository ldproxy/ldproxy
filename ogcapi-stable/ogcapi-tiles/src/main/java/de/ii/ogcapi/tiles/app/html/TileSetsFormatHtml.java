/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.app.html;

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
import de.ii.ogcapi.html.domain.MapClient;
import de.ii.ogcapi.html.domain.NavigationDTO;
import de.ii.ogcapi.tiles.domain.TileSet.DataType;
import de.ii.ogcapi.tiles.domain.TileSets;
import de.ii.ogcapi.tiles.domain.TileSetsFormatExtension;
import de.ii.ogcapi.tiles.domain.TilesConfiguration;
import de.ii.xtraplatform.services.domain.ServicesContext;
import de.ii.xtraplatform.tiles.domain.TileMatrixSet;
import de.ii.xtraplatform.tiles.domain.TileMatrixSetRepository;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title HTML
 */
@Singleton
@AutoBind
public class TileSetsFormatHtml implements TileSetsFormatExtension, FormatHtml {

  private final I18n i18n;
  private final URI servicesUri;
  private final TileMatrixSetRepository tileMatrixSetRepository;

  @Inject
  public TileSetsFormatHtml(
      I18n i18n, ServicesContext servicesContext, TileMatrixSetRepository tileMatrixSetRepository) {
    this.i18n = i18n;
    this.servicesUri = servicesContext.getUri();
    this.tileMatrixSetRepository = tileMatrixSetRepository;
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
  public Object getTileSetsEntity(
      TileSets tiles,
      DataType dataType,
      Optional<String> styleId,
      Optional<String> collectionId,
      OgcApi api,
      ApiRequestContext requestContext) {
    String rootTitle = i18n.get("root", requestContext.getLanguage());
    String collectionsTitle = i18n.get("collectionsTitle", requestContext.getLanguage());
    String stylesTitle = i18n.get("stylesTitle", requestContext.getLanguage());
    String title =
        dataType == DataType.vector
            ? i18n.get("tilesTitle", requestContext.getLanguage())
            : i18n.get("mapTilesTitle", requestContext.getLanguage());
    int diff = dataType == DataType.vector ? 0 : 1;

    URICustomizer resourceUri = requestContext.getUriCustomizer().copy().clearParameters();
    final List<NavigationDTO> breadCrumbs =
        styleId
            .map(
                s ->
                    (collectionId
                        .map(
                            string ->
                                new ImmutableList.Builder<NavigationDTO>()
                                    .add(
                                        new NavigationDTO(
                                            rootTitle,
                                            homeUrl(api.getData())
                                                .orElse(
                                                    resourceUri
                                                        .copy()
                                                        .removeLastPathSegments(
                                                            api.getData().getSubPath().size()
                                                                + 5
                                                                + diff)
                                                        .toString())))
                                    .add(
                                        new NavigationDTO(
                                            api.getData().getLabel(),
                                            resourceUri
                                                .copy()
                                                .removeLastPathSegments(5 + diff)
                                                .toString()))
                                    .add(
                                        new NavigationDTO(
                                            collectionsTitle,
                                            resourceUri
                                                .copy()
                                                .removeLastPathSegments(4 + diff)
                                                .toString()))
                                    .add(
                                        new NavigationDTO(
                                            api.getData().getCollections().get(string).getLabel(),
                                            resourceUri
                                                .copy()
                                                .removeLastPathSegments(3 + diff)
                                                .toString()))
                                    .add(
                                        new NavigationDTO(
                                            stylesTitle,
                                            resourceUri
                                                .copy()
                                                .removeLastPathSegments(2 + diff)
                                                .toString()))
                                    .add(
                                        new NavigationDTO(
                                            s,
                                            resourceUri
                                                .copy()
                                                .removeLastPathSegments(1 + diff)
                                                .toString()))
                                    .add(new NavigationDTO(title))
                                    .build())
                        .orElseGet(
                            () ->
                                new ImmutableList.Builder<NavigationDTO>()
                                    .add(
                                        new NavigationDTO(
                                            rootTitle,
                                            homeUrl(api.getData())
                                                .orElse(
                                                    resourceUri
                                                        .copy()
                                                        .removeLastPathSegments(
                                                            api.getData().getSubPath().size()
                                                                + 3
                                                                + diff)
                                                        .toString())))
                                    .add(
                                        new NavigationDTO(
                                            api.getData().getLabel(),
                                            resourceUri
                                                .copy()
                                                .removeLastPathSegments(3 + diff)
                                                .toString()))
                                    .add(
                                        new NavigationDTO(
                                            stylesTitle,
                                            resourceUri
                                                .copy()
                                                .removeLastPathSegments(2 + diff)
                                                .toString()))
                                    .add(
                                        new NavigationDTO(
                                            s,
                                            resourceUri
                                                .copy()
                                                .removeLastPathSegments(1 + diff)
                                                .toString()))
                                    .add(new NavigationDTO(title))
                                    .build())))
            .orElseGet(
                () ->
                    (collectionId
                        .map(
                            value ->
                                new ImmutableList.Builder<NavigationDTO>()
                                    .add(
                                        new NavigationDTO(
                                            rootTitle,
                                            homeUrl(api.getData())
                                                .orElse(
                                                    resourceUri
                                                        .copy()
                                                        .removeLastPathSegments(
                                                            api.getData().getSubPath().size()
                                                                + 3
                                                                + diff)
                                                        .toString())))
                                    .add(
                                        new NavigationDTO(
                                            api.getData().getLabel(),
                                            resourceUri
                                                .copy()
                                                .removeLastPathSegments(3 + diff)
                                                .toString()))
                                    .add(
                                        new NavigationDTO(
                                            collectionsTitle,
                                            resourceUri
                                                .copy()
                                                .removeLastPathSegments(2 + diff)
                                                .toString()))
                                    .add(
                                        new NavigationDTO(
                                            api.getData().getCollections().get(value).getLabel(),
                                            resourceUri
                                                .copy()
                                                .removeLastPathSegments(1 + diff)
                                                .toString()))
                                    .add(new NavigationDTO(title))
                                    .build())
                        .orElseGet(
                            () ->
                                new ImmutableList.Builder<NavigationDTO>()
                                    .add(
                                        new NavigationDTO(
                                            rootTitle,
                                            homeUrl(api.getData())
                                                .orElse(
                                                    resourceUri
                                                        .copy()
                                                        .removeLastPathSegments(
                                                            api.getData().getSubPath().size()
                                                                + 1
                                                                + diff)
                                                        .toString())))
                                    .add(
                                        new NavigationDTO(
                                            api.getData().getLabel(),
                                            resourceUri
                                                .copy()
                                                .removeLastPathSegments(1 + diff)
                                                .toString()))
                                    .add(new NavigationDTO(title))
                                    .build())));

    Optional<HtmlConfiguration> htmlConfig =
        collectionId.isPresent()
            ? api.getData().getExtension(HtmlConfiguration.class, collectionId.get())
            : api.getData().getExtension(HtmlConfiguration.class);

    Map<String, TileMatrixSet> tileMatrixSets = tileMatrixSetRepository.getAll();

    Optional<TilesConfiguration> tilesConfig =
        collectionId.isEmpty()
            ? api.getData().getExtension(TilesConfiguration.class)
            : api.getData().getExtension(TilesConfiguration.class, collectionId.get());
    MapClient.Type mapClientType =
        tilesConfig.map(TilesConfiguration::getMapClientType).orElse(MapClient.Type.MAP_LIBRE);
    String serviceUrl =
        new URICustomizer(servicesUri)
            .ensureLastPathSegments(api.getData().getSubPath().toArray(String[]::new))
            .toString();
    String styleUrl =
        tiles.getTilesets().stream().allMatch(t -> t.getDataType() == DataType.vector)
            ? htmlConfig
                .map(
                    cfg ->
                        cfg.getStyle(
                            tilesConfig.map(TilesConfiguration::getStyle),
                            collectionId,
                            serviceUrl,
                            mapClientType))
                .orElse(null)
            : null;
    boolean removeZoomLevelConstraints =
        tilesConfig.map(TilesConfiguration::getRemoveZoomLevelConstraints).orElse(false);

    return new ImmutableTileSetsView.Builder()
        .apiData(api.getData())
        .tiles(tiles)
        .collectionId(collectionId)
        .tileMatrixSets(tileMatrixSets)
        .breadCrumbs(breadCrumbs)
        .rawLinks(tiles.getLinks())
        .urlPrefix(requestContext.getStaticUrlPrefix())
        .mapClientType(mapClientType)
        .styleUrl(styleUrl)
        .removeZoomLevelConstraints(removeZoomLevelConstraints)
        .htmlConfig(htmlConfig.orElseThrow())
        .noIndex(isNoIndexEnabledForApi(api.getData()))
        .uriCustomizer(requestContext.getUriCustomizer().copy())
        .i18n(i18n)
        .description(tiles.getDescription().orElse(null))
        .title(tiles.getTitle().orElse(null))
        .language(requestContext.getLanguage())
        .user(requestContext.getUser())
        .build();
  }
}
