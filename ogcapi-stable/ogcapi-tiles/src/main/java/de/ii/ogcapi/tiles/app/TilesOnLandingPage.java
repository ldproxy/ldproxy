/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.common.domain.ImmutableLandingPage;
import de.ii.ogcapi.common.domain.ImmutableLandingPage.Builder;
import de.ii.ogcapi.common.domain.LandingPageExtension;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.tiles.domain.TileFormatExtension;
import de.ii.ogcapi.tiles.domain.TileSet.DataType;
import de.ii.ogcapi.tiles.domain.TilesConfiguration;
import de.ii.ogcapi.tiles.domain.TilesConfiguration.WmtsScope;
import de.ii.ogcapi.tiles.domain.TilesProviders;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** add tiling information to the dataset metadata */
@Singleton
@AutoBind
public class TilesOnLandingPage implements LandingPageExtension {

  private final I18n i18n;
  private final ExtensionRegistry extensionRegistry;
  private final TilesProviders tilesProviders;

  @Inject
  public TilesOnLandingPage(
      I18n i18n, ExtensionRegistry extensionRegistry, TilesProviders tilesProviders) {
    this.i18n = i18n;
    this.extensionRegistry = extensionRegistry;
    this.tilesProviders = tilesProviders;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    Optional<TilesConfiguration> extension = apiData.getExtension(TilesConfiguration.class);

    return extension
        .filter(TilesConfiguration::isEnabled)
        .filter(cfg -> cfg.hasDatasetTiles(tilesProviders, apiData))
        .isPresent();
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return TilesConfiguration.class;
  }

  @Override
  public ImmutableLandingPage.Builder process(
      Builder landingPageBuilder,
      OgcApi api,
      URICustomizer uriCustomizer,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      Optional<Locale> language) {

    if (isEnabledForApi(api.getData())) {
      boolean hasVectorTiles =
          extensionRegistry.getExtensionsForType(TileFormatExtension.class).stream()
              .anyMatch(
                  format ->
                      format.getDataType() == DataType.vector
                          && format.isEnabledForApi(api.getData())
                          && format.isApplicable(
                              api.getData(),
                              "/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}"));
      boolean hasMapTiles =
          extensionRegistry.getExtensionsForType(TileFormatExtension.class).stream()
              .anyMatch(
                  format ->
                      format.getDataType() == DataType.map
                          && format.isEnabledForApi(api.getData())
                          && format.isApplicable(
                              api.getData(),
                              "/map/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}"));

      if (!hasVectorTiles && !hasMapTiles) {
        // no tile format is enabled
        return landingPageBuilder;
      }

      boolean hasWmts =
          api.getData()
                  .getExtension(TilesConfiguration.class)
                  .map(TilesConfiguration::getWmts)
                  .orElse(WmtsScope.NONE)
              != WmtsScope.NONE;

      final TilesLinkGenerator tilesLinkGenerator = new TilesLinkGenerator();
      List<Link> links =
          tilesLinkGenerator.generateLandingPageLinks(
              uriCustomizer, hasVectorTiles, hasMapTiles, hasWmts, i18n, language);
      landingPageBuilder.addAllLinks(links);
    }
    return landingPageBuilder;
  }

  private boolean checkTilesEnabled(OgcApiDataV2 apiData) {
    return isEnabledForApi(apiData)
        && apiData.getCollections().values().stream()
            .anyMatch(
                featureTypeConfigurationOgcApi ->
                    isExtensionEnabled(featureTypeConfigurationOgcApi, TilesConfiguration.class));
  }
}
