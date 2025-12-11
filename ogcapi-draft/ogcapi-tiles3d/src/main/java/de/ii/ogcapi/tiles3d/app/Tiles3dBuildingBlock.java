/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.tiles3d.domain.ImmutableTiles3dConfiguration;
import de.ii.ogcapi.tiles3d.domain.Tile3dProviders;
import de.ii.ogcapi.tiles3d.domain.Tiles3dConfiguration;
import de.ii.ogcapi.tiles3d.domain.Tiles3dMigrationV5;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.base.domain.util.Tuple;
import de.ii.xtraplatform.entities.domain.EntityFactories;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.tiles3d.domain.Tile3dProviderData;
import de.ii.xtraplatform.tiles3d.domain.spec.Tileset3d;
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title 3D Tiles
 * @langEn Publish geographic data as 3D Tiles.
 * @langDe Veröffentlichen von Geodaten als 3D Tiles.
 * @scopeEn The building block *3D Tiles* enables publishing of 3D Tiles (1.0 or 1.1) from a [3D
 *     Tiles Provider](../../providers/tile3d/README.md).
 *     <p>The 3D Tiles can be inspected in a web browser using an integrated Cesium client.
 * @scopeDe Der Baustein *3D Tiles* ermöglicht die Veröffentlichung von 3D Tiles (1.0 oder 1.1) von
 *     einem [3D Tiles Provider](../../providers/tile3d/README.md).
 *     <p>Die 3D-Kacheln können in einem Webbrowser mit Hilfe eines integrierten Cesium-Clients
 *     inspiziert werden.
 * @conformanceEn *3D Tiles* implements support for the draft specification [OGC API - 3D
 *     GeoVolumes](https://docs.ogc.org/DRAFTS/22-029.html).
 * @conformanceDe *3D Tiles* implementiert Unterstützung für den Entwurf von [OGC API - 3D
 *     GeoVolumes](https://docs.ogc.org/DRAFTS/22-029.html).
 * @ref:cfg {@link de.ii.ogcapi.tiles3d.domain.Tiles3dConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.tiles3d.domain.ImmutableTiles3dConfiguration}
 * @ref:endpoints {@link de.ii.ogcapi.tiles3d.infra.Endpoint3dTilesTileset}, {@link
 *     de.ii.ogcapi.tiles3d.infra.Endpoint3dTilesFiles}
 * @ref:queryParameters {@link de.ii.ogcapi.tiles3d.app.QueryParameterFTileset}
 * @ref:pathParameters {@link de.ii.ogcapi.tiles3d.app.PathParameterCollectionId3dTiles}, {@link
 *     de.ii.ogcapi.tiles3d.app.PathParameter3dTilesFile}
 */
@AutoBind
@Singleton
public class Tiles3dBuildingBlock implements ApiBuildingBlock, ApiExtensionHealth {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://opengeospatial.github.io/ogcna-auto-review/22-029.html",
              "OGC API - 3D GeoVolumes (DRAFT)"));
  public static final String STORE_RESOURCE_TYPE = "tiles3d";
  public static final String DATASET_TILES = "__all__";

  private final Tile3dProviders tile3dProviders;
  private final EntityFactories entityFactories;

  @Inject
  public Tiles3dBuildingBlock(Tile3dProviders tile3dProviders, EntityFactories entityFactories) {
    this.tile3dProviders = tile3dProviders;
    this.entityFactories = entityFactories;
  }

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableTiles3dConfiguration.Builder()
        .enabled(false)
        .firstLevelWithContent(0)
        .maxLevel(0)
        .subtreeLevels(3)
        .geometricErrorRoot(0.0f)
        .clampToEllipsoid(false)
        .build();
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    OgcApiDataV2 apiData = api.getData();

    Optional<Tiles3dConfiguration> tilesConfiguration =
        apiData.getExtension(Tiles3dConfiguration.class).filter(ExtensionConfiguration::isEnabled);

    if (tilesConfiguration.isEmpty()) {
      return ValidationResult.of();
    }
    if (!tile3dProviders.hasTile3dProvider(apiData)) {
      if (Objects.nonNull(tilesConfiguration.get().getTileProvider())) {
        throw new IllegalStateException(
            String.format(
                "3D Tile provider with id '%s' not found.",
                tilesConfiguration.get().getTileProvider()));

      } else {
        boolean tilesIdExists =
            entityFactories.getAll("providers").stream()
                .anyMatch(
                    entityFactory ->
                        Tile3dProviderData.class.isAssignableFrom(entityFactory.dataClass())
                            && entityFactory
                                .instance(Tile3dProviders.to3dTilesId(apiData.getId()))
                                .isPresent());
        if (tilesIdExists) {
          throw new IllegalStateException(
              String.format(
                  "3D Tile provider with id '%s' not found.",
                  Tile3dProviders.to3dTilesId(apiData.getId())));
        }
      }
    }

    if (!isExtensionEnabled(apiData, FeaturesCoreConfiguration.class)) {
      tile3dProviders
          .getTileset3dMetadata(api.getData())
          .flatMap(tileset3d -> tileset3d.getRoot().getBoundingVolume().toBoundingBox())
          .ifPresent(api::setSpatialExtent);
    }

    /*if (apiValidation == MODE.NONE) {
      return ValidationResult.of();
    }*/

    return validate(apiData, apiValidation);
  }

  public ValidationResult validate(OgcApiDataV2 apiData, MODE apiValidation) {

    ImmutableValidationResult.Builder builder =
        ImmutableValidationResult.builder().mode(apiValidation);

    for (Map.Entry<String, Tiles3dConfiguration> entry :
        apiData.getCollections().entrySet().stream()
            .filter(
                entry ->
                    entry.getValue().getEnabled()
                        && entry.getValue().getExtension(Tiles3dConfiguration.class).isPresent())
            .map(
                entry ->
                    new AbstractMap.SimpleImmutableEntry<>(
                        entry.getKey(),
                        entry.getValue().getExtension(Tiles3dConfiguration.class).get()))
            .filter(entry -> entry.getValue().isEnabled())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
            .entrySet()) {
      String collectionId = entry.getKey();
      Tiles3dConfiguration config = entry.getValue();

      Optional<Tileset3d> tileset =
          tile3dProviders.getTileset3dMetadata(apiData, apiData.getCollectionData(collectionId));

      if (tileset.isEmpty()) {
        String tilesetId =
            tile3dProviders
                .getTileset3dId(apiData.getCollectionData(collectionId).orElse(null))
                .orElse("");
        builder.addErrors(
            MessageFormat.format(
                "TILES3D tileset ''{0}'' not found for collection ''{1}''.",
                tilesetId, collectionId));
      }
    }

    return builder.build();
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    Optional<Tiles3dConfiguration> tilesConfiguration =
        apiData.getExtension(Tiles3dConfiguration.class).filter(ExtensionConfiguration::isEnabled);

    if (tilesConfiguration.isPresent()
        && !tile3dProviders.hasTile3dProvider(apiData)
        && (Objects.isNull(tilesConfiguration.get().getTileProvider()))) {
      boolean tilesIdExists =
          entityFactories.getAll("providers").stream()
              .anyMatch(
                  entityFactory ->
                      Tile3dProviderData.class.isAssignableFrom(entityFactory.dataClass())
                          && entityFactory
                              .instance(Tile3dProviders.to3dTilesId(apiData.getId()))
                              .isPresent());
      if (!tilesIdExists) {
        Tiles3dMigrationV5 tiles3dMigrationV5 = new Tiles3dMigrationV5(null);
        Optional<Tuple<Class<? extends Tile3dProviderData>, ? extends Tile3dProviderData>>
            tileProviderData = tiles3dMigrationV5.getTileProviderData(apiData);
        if (tileProviderData.isPresent()) {
          entityFactories
              .get(tileProviderData.get().first())
              .createInstance(tileProviderData.get().second())
              .join();
          LogContext.put(LogContext.CONTEXT.SERVICE, apiData.getId());
        }
      }
    }

    return Set.of(tile3dProviders.getTile3dProviderOrThrow(apiData));
  }
}
