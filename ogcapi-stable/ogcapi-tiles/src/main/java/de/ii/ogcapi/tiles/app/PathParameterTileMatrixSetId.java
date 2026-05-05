/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.tiles.domain.TilesConfiguration;
import de.ii.ogcapi.tiles.domain.TilesProviders;
import de.ii.xtraplatform.tiles.domain.TileMatrixSetRepository;
import de.ii.xtraplatform.tiles.domain.TilesetMetadata;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * @title tileMatrixSetId
 * @endpoints Dataset Tilesets, Dataset Tileset, Dataset Tile, Collection Tilesets, Collection
 *     Tileset, Collection Tile
 * @langEn The identifier of the tiling scheme.
 * @langDe Der Identifikator des Kachelschemas.
 */
@Singleton
@AutoBind
public class PathParameterTileMatrixSetId implements OgcApiPathParameter {

  public static final String TMS_REGEX = "\\w+";

  private final ConcurrentMap<Integer, Schema<?>> schemaMap = new ConcurrentHashMap<>();
  private final SchemaValidator schemaValidator;
  private final TileMatrixSetRepository tileMatrixSetRepository;
  private final TilesProviders tilesProviders;

  @Inject
  public PathParameterTileMatrixSetId(
      SchemaValidator schemaValidator,
      TileMatrixSetRepository tileMatrixSetRepository,
      TilesProviders tilesProviders) {
    this.schemaValidator = schemaValidator;
    this.tileMatrixSetRepository = tileMatrixSetRepository;
    this.tilesProviders = tilesProviders;
  }

  @Override
  public String getPattern() {
    return TMS_REGEX;
  }

  @Override
  public List<String> getValues(OgcApiDataV2 apiData) {
    List<String> tmsSetMultiCollection =
        apiData
            .getExtension(TilesConfiguration.class)
            .filter(TilesConfiguration::isEnabled)
            .filter(cfg -> cfg.hasDatasetTiles(tilesProviders, apiData))
            .flatMap(cfg -> tilesProviders.getTilesetMetadata(apiData))
            .map(TilesetMetadata::getTileMatrixSets)
            .orElse(ImmutableSet.of())
            .stream()
            .collect(Collectors.toUnmodifiableList());

    Set<String> tmsSet =
        apiData.getCollections().values().stream()
            .filter(collection -> apiData.isCollectionEnabled(collection.getId()))
            .filter(
                collection ->
                    collection
                        .getExtension(TilesConfiguration.class)
                        .filter(TilesConfiguration::isEnabled)
                        .isPresent())
            .map(
                collection ->
                    tilesProviders
                        .getTilesetMetadata(apiData, collection)
                        .map(TilesetMetadata::getTileMatrixSets)
                        .orElse(ImmutableSet.of()))
            .flatMap(Set::stream)
            .collect(Collectors.toSet());

    tmsSet.addAll(tmsSetMultiCollection);

    return tmsSet.stream()
        .filter(tms -> tileMatrixSetRepository.get(tms).isPresent())
        .collect(ImmutableList.toImmutableList());
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    if (!schemaMap.containsKey(apiData.hashCode())) {
      schemaMap.put(
          apiData.hashCode(), new StringSchema()._enum(ImmutableList.copyOf(getValues(apiData))));
    }

    return schemaMap.get(apiData.hashCode());
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public String getId() {
    return "tileMatrixSetIdTiles";
  }

  @Override
  public String getName() {
    return "tileMatrixSetId";
  }

  @Override
  public String getDescription() {
    return "The local identifier of a tile matrix set, unique within the API.";
  }

  @Override
  public boolean isApplicable(OgcApiDataV2 apiData, String definitionPath, String collectionId) {
    if (isApplicable(apiData, definitionPath)) return false;

    return apiData
        .getCollections()
        .get(collectionId)
        .getExtension(TilesConfiguration.class)
        .map(ExtensionConfiguration::isEnabled)
        .orElse(true);
  }

  @Override
  public boolean isApplicable(OgcApiDataV2 apiData, String definitionPath) {
    return isEnabledForApi(apiData)
        && (definitionPath.startsWith("/collections/{collectionId}/tiles/{tileMatrixSetId}")
            || definitionPath.startsWith("/tiles/{tileMatrixSetId}")
            || definitionPath.startsWith("/collections/{collectionId}/map/tiles/{tileMatrixSetId}")
            || definitionPath.startsWith("/map/tiles/{tileMatrixSetId}")
            || definitionPath.startsWith(
                "/collections/{collectionId}/styles/{styleId}/map/tiles/{tileMatrixSetId}")
            || definitionPath.startsWith("/styles/{styleId}/map/tiles/{tileMatrixSetId}"));
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
