/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.collections.domain.AbstractPathParameterCollectionId;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.tiles.domain.TilesConfiguration;
import de.ii.ogcapi.tiles.domain.TilesProviders;
import de.ii.xtraplatform.features.domain.FeatureTypeConfiguration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * @title collectionId
 * @endpoints Collection Tilesets, Collection Tileset, Collection Tile
 * @langEn The identifier of the feature collection.
 * @langDe Der Identifikator der Feature Collection.
 */
@Singleton
@AutoBind
public class PathParameterCollectionIdTiles extends AbstractPathParameterCollectionId {

  private final TilesProviders tilesProviders;

  @Inject
  PathParameterCollectionIdTiles(SchemaValidator schemaValidator, TilesProviders tilesProviders) {
    super(schemaValidator);
    this.tilesProviders = tilesProviders;
  }

  @Override
  public List<String> getValues(OgcApiDataV2 apiData) {
    if (!apiCollectionMap.containsKey(apiData.hashCode())) {
      apiCollectionMap.put(
          apiData.hashCode(),
          apiData.getCollections().values().stream()
              .filter(collection -> apiData.isCollectionEnabled(collection.getId()))
              .filter(
                  collection ->
                      collection
                          .getExtension(TilesConfiguration.class)
                          .map(
                              cfg ->
                                  cfg.hasCollectionTiles(
                                      tilesProviders, apiData, collection.getId()))
                          .orElse(false))
              .filter(
                  collection ->
                      collection
                          .getExtension(TilesConfiguration.class)
                          .filter(ExtensionConfiguration::isEnabled)
                          .isPresent())
              .map(FeatureTypeConfiguration::getId)
              .collect(Collectors.toUnmodifiableList()));
    }

    return apiCollectionMap.get(apiData.hashCode());
  }

  @Override
  public boolean isExplodeInOpenApi(OgcApiDataV2 apiData) {
    return false;
  }

  @Override
  public String getId() {
    return "collectionIdTiles";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return definitionPath.startsWith("/collections/{collectionId}/tiles")
        || definitionPath.startsWith("/collections/{collectionId}/map/tiles")
        || definitionPath.startsWith("/collections/{collectionId}/styles/{styleId}/map/tiles");
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return TilesConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return TilesBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return TilesBuildingBlock.SPEC;
  }
}
