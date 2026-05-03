/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ExtendableConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.tiles.domain.TilesConfiguration;
import de.ii.ogcapi.tiles.domain.TilesProviders;
import de.ii.xtraplatform.entities.domain.EntityRegistry;
import de.ii.xtraplatform.tiles.domain.TileProvider;
import java.util.Optional;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class TilesProvidersImpl implements TilesProviders {

  private static final Logger LOGGER = LoggerFactory.getLogger(TilesProvidersImpl.class);

  private final EntityRegistry entityRegistry;

  @Inject
  public TilesProvidersImpl(EntityRegistry entityRegistry) {
    this.entityRegistry = entityRegistry;
  }

  @Override
  public boolean hasTileProvider(OgcApiDataV2 apiData) {
    return getTileProvider(apiData).isPresent();
  }

  @Override
  public Optional<TileProvider> getTileProvider(OgcApiDataV2 apiData) {
    Optional<TileProvider> optionalTileProvider = getOptionalTileProvider(apiData);

    if (!optionalTileProvider.isPresent()) {
      optionalTileProvider =
          entityRegistry.getEntity(TileProvider.class, TilesProviders.toTilesId(apiData.getId()));
    }
    return optionalTileProvider;
  }

  @Override
  public boolean hasTileProvider(
      OgcApiDataV2 apiData, FeatureTypeConfigurationOgcApi collectionData) {
    return getTileProvider(apiData, collectionData).isPresent();
  }

  @Override
  public Optional<TileProvider> getTileProvider(
      OgcApiDataV2 apiData, FeatureTypeConfigurationOgcApi collectionData) {
    return getOptionalTileProvider(collectionData).or(() -> getTileProvider(apiData));
  }

  @Override
  public TileProvider getTileProviderOrThrow(
      OgcApiDataV2 apiData, FeatureTypeConfigurationOgcApi collectionData) {
    return getOptionalTileProvider(collectionData).orElse(getTileProviderOrThrow(apiData));
  }

  private Optional<TileProvider> getOptionalTileProvider(
      ExtendableConfiguration extendableConfiguration) {
    return extendableConfiguration
        .getExtension(TilesConfiguration.class)
        .filter(ExtensionConfiguration::isEnabled)
        .flatMap(cfg -> Optional.ofNullable(cfg.getTileProvider()))
        .flatMap(id -> entityRegistry.getEntity(TileProvider.class, id));
  }
}
