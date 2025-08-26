/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.collections.domain.CollectionExtension;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiCollection;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiCollection.Builder;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.tiles.domain.TileFormatExtension;
import de.ii.ogcapi.tiles.domain.TileSet.DataType;
import de.ii.ogcapi.tiles.domain.TilesConfiguration;
import de.ii.ogcapi.tiles.domain.TilesProviders;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** add tiling information to the collection metadata (supported tiling schemes, links) */
@Singleton
@AutoBind
public class TilesOnCollection implements CollectionExtension {

  private final I18n i18n;
  private final ExtensionRegistry extensionRegistry;
  private final TilesProviders tilesProviders;

  @Inject
  public TilesOnCollection(
      I18n i18n, ExtensionRegistry extensionRegistry, TilesProviders tilesProviders) {
    this.i18n = i18n;
    this.extensionRegistry = extensionRegistry;
    this.tilesProviders = tilesProviders;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return TilesConfiguration.class;
  }

  @Override
  public ImmutableOgcApiCollection.Builder process(
      Builder collection,
      FeatureTypeConfigurationOgcApi featureTypeConfiguration,
      OgcApi api,
      URICustomizer uriCustomizer,
      boolean isNested,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      Optional<Locale> language) {
    // The hrefs are URI templates and not URIs, so the templates should not be percent encoded!
    final TilesLinkGenerator tilesLinkGenerator = new TilesLinkGenerator();

    if (isExtensionEnabled(featureTypeConfiguration, TilesConfiguration.class)
        && isExtensionEnabled(
            featureTypeConfiguration,
            TilesConfiguration.class,
            cfg ->
                cfg.hasCollectionTiles(
                    tilesProviders, api.getData(), featureTypeConfiguration.getId()))) {

      boolean hasVectorTiles =
          extensionRegistry.getExtensionsForType(TileFormatExtension.class).stream()
              .anyMatch(
                  format ->
                      format.getDataType() == DataType.vector
                          && format.isEnabledForApi(api.getData(), featureTypeConfiguration.getId())
                          && format.isApplicable(
                              api.getData(),
                              featureTypeConfiguration.getId(),
                              "/collections/{collectionId}/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}"));
      boolean hasMapTiles =
          extensionRegistry.getExtensionsForType(TileFormatExtension.class).stream()
              .anyMatch(
                  format ->
                      format.getDataType() == DataType.map
                          && format.isEnabledForApi(api.getData(), featureTypeConfiguration.getId())
                          && format.isApplicable(
                              api.getData(),
                              featureTypeConfiguration.getId(),
                              "/collections/{collectionId}/map/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}"));

      if (!hasVectorTiles && !hasMapTiles) {
        // no tile format is enabled
        return collection;
      }

      collection.addAllLinks(
          tilesLinkGenerator.generateCollectionLinks(
              uriCustomizer,
              featureTypeConfiguration.getId(),
              hasVectorTiles,
              hasMapTiles,
              i18n,
              language));
    }

    return collection;
  }
}
