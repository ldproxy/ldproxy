/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.domain;

import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.base.domain.resiliency.OptionalVolatileCapability;
import de.ii.xtraplatform.tiles3d.domain.Tile3dAccess;
import de.ii.xtraplatform.tiles3d.domain.Tile3dProvider;
import de.ii.xtraplatform.tiles3d.domain.spec.Tileset3d;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface Tile3dProviders {

  String API_URI = "apiUri";
  String SERVICE_URL = "serviceUrl";
  Function<String, Map<String, String>> DEFAULT_SUBSTITUTIONS =
      apiUri -> ImmutableMap.of(API_URI, apiUri, SERVICE_URL, apiUri);
  BiFunction<String, Map<String, String>, Map<String, String>> DEFAULT_SUBSTITUTIONS_PLUS =
      (apiUri, plus) ->
          ImmutableMap.<String, String>builder()
              .putAll(DEFAULT_SUBSTITUTIONS.apply(apiUri))
              .putAll(plus)
              .build();

  static String to3dTilesId(String apiId) {
    return String.format("%s-3dtiles", apiId);
  }

  boolean hasTile3dProvider(OgcApiDataV2 apiData);

  Optional<Tile3dProvider> getTile3dProvider(OgcApiDataV2 apiData);

  default <T> Optional<T> getTile3dProvider(
      OgcApiDataV2 apiData, Function<Tile3dProvider, OptionalVolatileCapability<T>> capability) {
    return getTile3dProvider(apiData)
        .map(capability)
        .filter(OptionalVolatileCapability::isAvailable)
        .map(OptionalVolatileCapability::get);
  }

  default Tile3dProvider getTile3dProviderOrThrow(OgcApiDataV2 apiData) {
    return getTile3dProvider(apiData)
        .orElseThrow(() -> new IllegalStateException("No 3d tile provider found."));
  }

  default <T> T getTile3dProviderOrThrow(
      OgcApiDataV2 apiData, Function<Tile3dProvider, OptionalVolatileCapability<T>> capability) {
    return getTile3dProvider(apiData, capability)
        .orElseThrow(() -> new IllegalStateException("No tile provider found."));
  }

  default Optional<Tileset3d> getTileset3dMetadata(OgcApiDataV2 apiData) {
    Optional<Tile3dAccess> optionalProvider =
        getTile3dProvider(apiData)
            .filter(provider -> provider.access().isAvailable())
            .map(provider -> provider.access().get());
    return getTileset3dId(apiData)
        .flatMap(
            tilesetId -> optionalProvider.flatMap(provider -> provider.getMetadata(tilesetId)));
  }

  default Optional<String> getTileset3dId(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(Tiles3dConfiguration.class)
        .map(Tiles3dConfiguration::getDatasetTileset);
  }

  default Tileset3d getTileset3dMetadataOrThrow(OgcApiDataV2 apiData) {
    return getTileset3dMetadata(apiData)
        .orElseThrow(() -> new IllegalStateException("No 3d tileset metadata found."));
  }

  boolean hasTile3dProvider(OgcApiDataV2 apiData, FeatureTypeConfigurationOgcApi collectionData);

  Optional<Tile3dProvider> getTile3dProvider(
      OgcApiDataV2 apiData, FeatureTypeConfigurationOgcApi collectionData);

  default <T> Optional<T> getTile3dProvider(
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData,
      Function<Tile3dProvider, OptionalVolatileCapability<T>> capability) {
    return getTile3dProvider(apiData, collectionData)
        .map(capability)
        .filter(OptionalVolatileCapability::isAvailable)
        .map(OptionalVolatileCapability::get);
  }

  Tile3dProvider getTile3dProviderOrThrow(
      OgcApiDataV2 apiData, FeatureTypeConfigurationOgcApi collectionData);

  default <T> T getTile3dProviderOrThrow(
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData,
      Function<Tile3dProvider, OptionalVolatileCapability<T>> capability) {
    return getTile3dProvider(apiData, collectionData, capability)
        .orElseThrow(() -> new IllegalStateException("No tile provider found."));
  }

  default Optional<Tileset3d> getTileset3dMetadata(
      OgcApiDataV2 apiData, FeatureTypeConfigurationOgcApi collectionData) {
    Optional<Tile3dAccess> optionalProvider =
        getTile3dProvider(apiData, collectionData)
            .filter(provider -> provider.access().isAvailable())
            .map(provider -> provider.access().get());
    return getTileset3dId(collectionData)
        .flatMap(
            tilesetId -> optionalProvider.flatMap(provider -> provider.getMetadata(tilesetId)));
  }

  default Optional<String> getTileset3dId(FeatureTypeConfigurationOgcApi collectionData) {
    return collectionData
        .getExtension(Tiles3dConfiguration.class)
        .map(cfg -> cfg.getCollectionTileset(collectionData.getId()));
  }

  default Tileset3d getTileset3dMetadataOrThrow(
      OgcApiDataV2 apiData, FeatureTypeConfigurationOgcApi collectionData) {
    return getTileset3dMetadata(apiData, collectionData)
        .orElseThrow(() -> new IllegalStateException("No 3d tileset metadata found."));
  }

  default boolean hasTile3dProvider(
      OgcApiDataV2 apiData, Optional<FeatureTypeConfigurationOgcApi> collectionData) {
    return collectionData.isPresent()
        ? hasTile3dProvider(apiData, collectionData.get())
        : hasTile3dProvider(apiData);
  }

  default Optional<Tile3dProvider> getTile3dProvider(
      OgcApiDataV2 apiData, Optional<FeatureTypeConfigurationOgcApi> collectionData) {
    return collectionData.isPresent()
        ? getTile3dProvider(apiData, collectionData.get())
        : getTile3dProvider(apiData);
  }

  default <T> Optional<T> getTile3dProvider(
      OgcApiDataV2 apiData,
      Optional<FeatureTypeConfigurationOgcApi> collectionData,
      Function<Tile3dProvider, OptionalVolatileCapability<T>> capability) {
    return collectionData.isPresent()
        ? getTile3dProvider(apiData, collectionData.get(), capability)
        : getTile3dProvider(apiData, capability);
  }

  default Tile3dProvider getTile3dProviderOrThrow(
      OgcApiDataV2 apiData, Optional<FeatureTypeConfigurationOgcApi> collectionData) {
    return collectionData.isPresent()
        ? getTile3dProviderOrThrow(apiData, collectionData.get())
        : getTile3dProviderOrThrow(apiData);
  }

  default <T> T getTile3dProviderOrThrow(
      OgcApiDataV2 apiData,
      Optional<FeatureTypeConfigurationOgcApi> collectionData,
      Function<Tile3dProvider, OptionalVolatileCapability<T>> capability) {
    return collectionData.isPresent()
        ? getTile3dProviderOrThrow(apiData, collectionData.get(), capability)
        : getTile3dProviderOrThrow(apiData, capability);
  }

  default Optional<Tileset3d> getTileset3dMetadata(
      OgcApiDataV2 apiData, Optional<FeatureTypeConfigurationOgcApi> collectionData) {
    return collectionData.isPresent()
        ? getTileset3dMetadata(apiData, collectionData.get())
        : getTileset3dMetadata(apiData);
  }

  default Tileset3d getTileset3dMetadataOrThrow(
      OgcApiDataV2 apiData, Optional<FeatureTypeConfigurationOgcApi> collectionData) {
    return collectionData.isPresent()
        ? getTileset3dMetadataOrThrow(apiData, collectionData.get())
        : getTileset3dMetadataOrThrow(apiData);
  }
}
