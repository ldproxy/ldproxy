/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import de.ii.ogcapi.styles.domain.ImmutableQueryInputStyle;
import de.ii.ogcapi.styles.domain.StyleQueryParameter;
import de.ii.ogcapi.styles.domain.StylesConfiguration;
import de.ii.ogcapi.tiles.domain.TilesConfiguration;
import de.ii.ogcapi.tiles.domain.TilesProviders;
import de.ii.xtraplatform.tiles.domain.TileMatrixSet;
import de.ii.xtraplatform.tiles.domain.TileMatrixSetRepository;
import de.ii.xtraplatform.tiles.domain.TilesetMetadata;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title tile-matrix-set
 * @endpoints Style, Collection Style
 * @langEn The tile matrix set that should be used by the style. The query parameter only applies to
 *     MapLibre/MapBox styles. The style source will be adjusted to the tile set for the selected
 *     tile matrix set. In addition, level expressions will be adjusted, if the levels of the tile
 *     matrix set differ from the levels in the WebMercatorQuad tile matrix set.
 *     <p>Only supported for tile matrix sets that use a quad tree (e.g., WebMercatorQuad,
 *     WorldMercatorWGS84Quad, AdV_25832, and AdV_25833) and only for tile sets that are available
 *     in one of the tile matrix sets.
 * @langDe Das Tile-Matrix-Set, der vom Style verwendet werden soll. Der Abfrageparameter gilt nur
 *     für MapLibre/MapBox Styles. Die Style-Quelle wird an den Kachelsatz für das ausgewählte
 *     Tile-Matrix-Set angepasst. Zusätzlich werden die Level-Ausdrücke angepasst, wenn die Ebenen
 *     des Tile-Matrix-Sets von den Ebenen im WebMercatorQuad-Tile-Matrix-Set abweichen. Nur
 *     unterstützt für Quad-Tree-Tile-Matrix-Sets (z.B. WebMercatorQuad, WorldMercatorWGS84Quad,
 *     AdV_25832 und AdV_25833) und nur für Kachelsätze, die in einem der Tile-Matrix-Sets verfügbar
 *     sind.
 */
@Singleton
@AutoBind
public class QueryParameterTileMatrixSet extends OgcApiQueryParameterBase
    implements TypedQueryParameter<TileMatrixSet>, StyleQueryParameter {

  private final SchemaValidator schemaValidator;
  private final TilesProviders tilesProviders;
  private final TileMatrixSetRepository tileMatrixSetRepository;

  @Inject
  QueryParameterTileMatrixSet(
      SchemaValidator schemaValidator,
      TilesProviders tilesProviders,
      TileMatrixSetRepository tileMatrixSetRepository) {
    this.schemaValidator = schemaValidator;
    this.tilesProviders = tilesProviders;
    this.tileMatrixSetRepository = tileMatrixSetRepository;
  }

  @Override
  public String getName() {
    return "tile-matrix-set";
  }

  @Override
  public String getId() {
    return "tileMatrixSetStyle";
  }

  @Override
  public String getDescription() {
    return "The tile matrix set that should be used by the style. The query parameter only applies "
        + "to MapLibre/MapBox styles. The style source will be adjusted to the tile set for the "
        + "selected tile matrix set. In addition, level expressions will be adjusted, if the levels "
        + "of the tile matrix set differ from the levels in the WebMercatorQuad tile matrix set. "
        + "Only supported for tile matrix sets that use a quad tree (e.g., WebMercatorQuad, "
        + "WorldMercatorWGS84Quad, AdV_25832, and AdV_25833) and only for tile sets that are "
        + "available in one of the tile matrix sets.";
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return new StringSchema()._enum(getTileMatrixSets(apiData, Optional.empty()));
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    return new StringSchema()
        ._enum(getTileMatrixSets(apiData, apiData.getCollectionData(collectionId)));
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return definitionPath.endsWith("/styles/{styleId}");
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return isExtensionEnabled(apiData, TilesConfiguration.class, TilesConfiguration::isEnabled)
        && isExtensionEnabled(apiData, StylesConfiguration.class, StylesConfiguration::isEnabled)
        && getTileMatrixSets(apiData, Optional.empty()).size() > 1;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    FeatureTypeConfigurationOgcApi collectionData = apiData.getCollections().get(collectionId);
    return Objects.nonNull(collectionData)
        && isExtensionEnabled(
            collectionData, TilesConfiguration.class, TilesConfiguration::isEnabled)
        && isExtensionEnabled(
            collectionData, StylesConfiguration.class, StylesConfiguration::isEnabled)
        && getTileMatrixSets(apiData, Optional.of(collectionData)).size() > 1;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return StylesConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return StylesBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return StylesBuildingBlock.SPEC;
  }

  @Override
  public TileMatrixSet parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    return getTileMatrixSets(api.getData(), optionalCollectionData).stream()
        .filter(tmsId -> tmsId.equals(value))
        .map(tileMatrixSetRepository::get)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst()
        .orElse(null);
  }

  @Override
  public void applyTo(ImmutableQueryInputStyle.Builder builder, QueryParameterSet parameters) {
    parameters.getValue(this).ifPresent(builder::tileMatrixSet);
  }

  private List<String> getTileMatrixSets(
      OgcApiDataV2 apiData, Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    return tilesProviders
        .getTilesetMetadata(apiData, optionalCollectionData)
        .map(TilesetMetadata::getTileMatrixSets)
        .orElse(Set.of())
        .stream()
        .filter(
            tmsId ->
                tileMatrixSetRepository.get(tmsId).filter(TileMatrixSet::isQuadTree).isPresent())
        .sorted()
        .toList();
  }
}
