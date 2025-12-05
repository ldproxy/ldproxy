/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ExtendableConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.tiles3d.domain.Tile3dProviders;
import de.ii.ogcapi.tiles3d.domain.Tiles3dConfiguration;
import de.ii.xtraplatform.entities.domain.EntityRegistry;
import de.ii.xtraplatform.tiles3d.domain.Tile3dProvider;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class Tile3dProvidersImpl implements Tile3dProviders {

  private final EntityRegistry entityRegistry;

  @Inject
  public Tile3dProvidersImpl(EntityRegistry entityRegistry) {
    this.entityRegistry = entityRegistry;
  }

  @Override
  public boolean hasTile3dProvider(OgcApiDataV2 apiData) {
    return getTile3dProvider(apiData).isPresent();
  }

  @Override
  public Optional<Tile3dProvider> getTile3dProvider(OgcApiDataV2 apiData) {
    Optional<Tile3dProvider> optionalTileProvider = getOptionalTile3dProvider(apiData);

    if (!optionalTileProvider.isPresent()) {
      optionalTileProvider =
          entityRegistry.getEntity(
              Tile3dProvider.class, Tile3dProviders.to3dTilesId(apiData.getId()));
    }
    return optionalTileProvider;
  }

  @Override
  public boolean hasTile3dProvider(
      OgcApiDataV2 apiData, FeatureTypeConfigurationOgcApi collectionData) {
    return getTile3dProvider(apiData, collectionData).isPresent();
  }

  @Override
  public Optional<Tile3dProvider> getTile3dProvider(
      OgcApiDataV2 apiData, FeatureTypeConfigurationOgcApi collectionData) {
    return getOptionalTile3dProvider(collectionData).or(() -> getTile3dProvider(apiData));
  }

  @Override
  public Tile3dProvider getTile3dProviderOrThrow(
      OgcApiDataV2 apiData, FeatureTypeConfigurationOgcApi collectionData) {
    return getOptionalTile3dProvider(collectionData).orElse(getTile3dProviderOrThrow(apiData));
  }

  private Optional<Tile3dProvider> getOptionalTile3dProvider(
      ExtendableConfiguration extendableConfiguration) {
    return extendableConfiguration
        .getExtension(Tiles3dConfiguration.class)
        .filter(ExtensionConfiguration::isEnabled)
        .flatMap(cfg -> Optional.ofNullable(cfg.getTileProvider()))
        .flatMap(id -> entityRegistry.getEntity(Tile3dProvider.class, id));
  }
}
