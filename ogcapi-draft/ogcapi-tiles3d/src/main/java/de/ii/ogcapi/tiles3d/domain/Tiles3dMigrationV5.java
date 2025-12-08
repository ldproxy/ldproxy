/*
 * Copyright 2023 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.domain;

import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.base.domain.util.Tuple;
import de.ii.xtraplatform.entities.domain.EntityData;
import de.ii.xtraplatform.entities.domain.EntityMigration;
import de.ii.xtraplatform.tiles.domain.MinMax;
import de.ii.xtraplatform.tiles3d.domain.ImmutableSeedingOptions3d;
import de.ii.xtraplatform.tiles3d.domain.ImmutableTile3dProviderFeaturesData;
import de.ii.xtraplatform.tiles3d.domain.ImmutableTileset3dFeatures;
import de.ii.xtraplatform.tiles3d.domain.ImmutableTileset3dFeaturesDefaults;
import de.ii.xtraplatform.tiles3d.domain.Tile3dProviderData;
import de.ii.xtraplatform.tiles3d.domain.Tile3dProviderFeaturesData;
import de.ii.xtraplatform.tiles3d.domain.Tileset3dFeatures;
import de.ii.xtraplatform.values.domain.Identifier;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class Tiles3dMigrationV5 extends EntityMigration<OgcApiDataV2, OgcApiDataV2> {

  public Tiles3dMigrationV5(EntityMigrationContext context) {
    super(context);
  }

  @Override
  public String getSubject() {
    return "building block TILES3D";
  }

  @Override
  public String getDescription() {
    return "is deprecated and will be upgraded to a separate 3DTILE provider entity";
  }

  @Override
  public boolean isApplicable(EntityData entityData, Optional<EntityData> defaults) {
    if (!(entityData instanceof OgcApiDataV2)
        || (defaults.isPresent() && !(defaults.get() instanceof OgcApiDataV2))) {
      return false;
    }

    OgcApiDataV2 apiData = (OgcApiDataV2) entityData;
    Optional<OgcApiDataV2> apiDefaults = defaults.map(d -> (OgcApiDataV2) d);

    Optional<Tiles3dConfiguration> tilesConfiguration = getTilesConfiguration(apiData, apiDefaults);

    if (tilesConfiguration.isEmpty()
        || Objects.nonNull(tilesConfiguration.get().getTileProvider())) {
      return false;
    }

    return !getContext()
        .exists(
            identifier ->
                Objects.equals(identifier.id(), Tile3dProviders.to3dTilesId(apiData.getId()))
                    && identifier.path().get(identifier.path().size() - 1).equals("providers"));
  }

  @Override
  public OgcApiDataV2 migrate(OgcApiDataV2 entityData, Optional<OgcApiDataV2> defaults) {
    Optional<Tiles3dConfiguration> tilesConfigurationOld =
        getTilesConfiguration(entityData, defaults);

    if (tilesConfigurationOld.isEmpty()) {
      return entityData;
    }

    Tiles3dConfiguration tilesConfiguration =
        new ImmutableTiles3dConfiguration.Builder()
            .from(tilesConfigurationOld.get())
            .tileProvider(Tile3dProviders.to3dTilesId(entityData.getId()))
            .geometricErrorRoot(null)
            .clampToEllipsoid(null)
            .subtreeLevels(null)
            .firstLevelWithContent(null)
            .maxLevel(null)
            .contentFilters(List.of())
            .tileFilters(List.of())
            .seeding(Optional.empty())
            .build();

    return new ImmutableOgcApiDataV2.Builder()
        .from(OgcApiDataV2.replaceOrAddExtensions(entityData, tilesConfiguration))
        .collections(
            entityData.getCollections().entrySet().stream()
                .map(
                    entry -> {
                      if (entry.getValue().getExtension(Tiles3dConfiguration.class).isPresent()) {
                        return Map.entry(
                            entry.getKey(),
                            new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                                .from(entry.getValue())
                                .extensions(
                                    entry.getValue().getExtensions().stream()
                                        .filter(e -> !(e instanceof Tiles3dConfiguration))
                                        .collect(Collectors.toList()))
                                .build());
                      }
                      return entry;
                    })
                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue)))
        .build();
  }

  @Override
  public Map<Identifier, ? extends EntityData> getAdditionalEntities(
      EntityData entityData, Optional<EntityData> defaults) {
    OgcApiDataV2 apiData =
        defaults.isEmpty()
            ? (OgcApiDataV2) entityData
            : new ImmutableOgcApiDataV2.Builder().from(defaults.get()).from(entityData).build();

    Optional<Tuple<Class<? extends Tile3dProviderData>, ? extends Tile3dProviderData>>
        tileProviderData = getTileProviderData(apiData);

    if (tileProviderData.isPresent()) {
      return Map.of(
          Identifier.from(tileProviderData.get().second().getId(), "providers"),
          tileProviderData.get().second());
    }

    return Map.of();
  }

  public Optional<Tuple<Class<? extends Tile3dProviderData>, ? extends Tile3dProviderData>>
      getTileProviderData(OgcApiDataV2 apiData) {
    Optional<Tiles3dConfiguration> tiles = getTilesConfiguration(apiData, Optional.empty());

    if (tiles.isEmpty()) {
      return Optional.empty();
    }

    Optional<FeaturesCoreConfiguration> featuresCore =
        apiData.getExtension(FeaturesCoreConfiguration.class);

    Map<String, FeatureTypeConfigurationOgcApi> collections =
        apiData.getCollections().entrySet().stream()
            .filter(
                entry -> {
                  Optional<Tiles3dConfiguration> collectionData =
                      entry.getValue().getExtension(Tiles3dConfiguration.class);
                  return collectionData.isEmpty()
                      || !Objects.equals(collectionData.get().getEnabled(), false);
                })
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

    return Optional.of(
        Tuple.of(
            Tile3dProviderFeaturesData.class,
            getFeaturesData(apiData.getId(), tiles.get(), featuresCore, collections)));
  }

  private static Tile3dProviderFeaturesData getFeaturesData(
      String apiId,
      Tiles3dConfiguration cfg,
      Optional<FeaturesCoreConfiguration> featuresCore,
      Map<String, FeatureTypeConfigurationOgcApi> collections) {
    Map<String, Tiles3dConfiguration> collectionConfigs =
        collections.entrySet().stream()
            .map(
                entry ->
                    new SimpleImmutableEntry<>(
                        entry.getKey(),
                        entry.getValue().getExtension(Tiles3dConfiguration.class).get()))
            .collect(ImmutableMap.toImmutableMap(Entry::getKey, Entry::getValue));

    return new ImmutableTile3dProviderFeaturesData.Builder()
        .id(Tile3dProviders.to3dTilesId(apiId))
        .providerType(Tile3dProviderFeaturesData.PROVIDER_TYPE)
        .providerSubType(Tile3dProviderFeaturesData.PROVIDER_SUBTYPE)
        // .addAllCaches(getCaches(tilesConfiguration, collectionConfigs))
        .seeding(
            cfg.getSeeding()
                .map(seeding -> new ImmutableSeedingOptions3d.Builder().from(seeding).build()))
        .tilesetDefaults(
            new ImmutableTileset3dFeaturesDefaults.Builder()
                .featureProvider(
                    featuresCore.flatMap(FeaturesCoreConfiguration::getFeatureProvider))
                .geometricErrorRoot(cfg.getGeometricErrorRoot())
                .clampToEllipsoid(cfg.getClampToEllipsoid())
                .subtreeLevels(cfg.getSubtreeLevels())
                .contentLevels(MinMax.of(cfg.getFirstLevelWithContent(), cfg.getMaxLevel()))
                .contentFilters(cfg.getContentFilters())
                .tileFilters(
                    Objects.equals(
                            cfg.getTileFilters(),
                            Tiles3dConfiguration.getTileFilters(cfg.getContentFilters()))
                        ? List.of()
                        : cfg.getTileFilters())
                .build())
        .putAllTilesets(
            collectionConfigs.entrySet().stream()
                .map(
                    entry ->
                        new SimpleImmutableEntry<>(
                            entry.getKey(),
                            getFeatureLayer(entry.getKey(), entry.getValue(), collections)))
                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue)))
        .build();
  }

  private static Tileset3dFeatures getFeatureLayer(
      String id,
      Tiles3dConfiguration cfg,
      Map<String, FeatureTypeConfigurationOgcApi> collections) {
    return new ImmutableTileset3dFeatures.Builder()
        .id(id)
        .featureProvider(
            collections
                .get(id)
                .getExtension(FeaturesCoreConfiguration.class)
                .flatMap(FeaturesCoreConfiguration::getFeatureProvider))
        .featureType(
            collections
                .get(id)
                .getExtension(FeaturesCoreConfiguration.class)
                .flatMap(FeaturesCoreConfiguration::getFeatureType))
        .geometricErrorRoot(cfg.getGeometricErrorRoot())
        .clampToEllipsoid(cfg.getClampToEllipsoid())
        .subtreeLevels(cfg.getSubtreeLevels())
        .contentLevels(MinMax.of(cfg.getFirstLevelWithContent(), cfg.getMaxLevel()))
        .contentFilters(cfg.getContentFilters())
        .tileFilters(
            Objects.equals(
                    cfg.getTileFilters(),
                    Tiles3dConfiguration.getTileFilters(cfg.getContentFilters()))
                ? List.of()
                : cfg.getTileFilters())
        .build();
  }

  private static Optional<Tiles3dConfiguration> getTilesConfiguration(
      OgcApiDataV2 entityData, Optional<OgcApiDataV2> defaults) {
    Optional<Tiles3dConfiguration> tilesConfiguration =
        entityData.getExtension(Tiles3dConfiguration.class);
    Optional<Tiles3dConfiguration> tilesConfigurationDefaults =
        defaults.flatMap(d -> d.getExtension(Tiles3dConfiguration.class));

    return tilesConfiguration
        .map(
            tc ->
                tilesConfigurationDefaults
                    .map(tcd -> (Tiles3dConfiguration) tc.mergeInto((ExtensionConfiguration) tcd))
                    .orElse(tc))
        .or(() -> tilesConfigurationDefaults)
        .filter(ExtensionConfiguration::isEnabled);
  }
}
