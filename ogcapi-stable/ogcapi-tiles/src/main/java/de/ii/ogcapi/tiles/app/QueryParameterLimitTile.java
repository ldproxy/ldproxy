/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import de.ii.ogcapi.tiles.domain.TileGenerationUserParameter;
import de.ii.ogcapi.tiles.domain.TilesConfiguration;
import de.ii.ogcapi.tiles.domain.TilesProviders;
import de.ii.xtraplatform.tiles.domain.ImmutableTileGenerationParametersTransient;
import de.ii.xtraplatform.tiles.domain.TileGenerationOptions;
import de.ii.xtraplatform.tiles.domain.TileGenerationSchema;
import de.ii.xtraplatform.tiles.domain.TileProvider;
import de.ii.xtraplatform.tiles.domain.TileProviderData;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * @langEn The parameter restricts the number of features that are included in the tile.
 * @langDe Der Parameter begrenzt die Anzahl der Features, die in die Kachel aufgenommen werden.
 * @title limit
 * @endpoints Dataset Tile, Collection Tile
 */
@Singleton
@AutoBind
public class QueryParameterLimitTile extends OgcApiQueryParameterBase
    implements TypedQueryParameter<Integer>, TileGenerationUserParameter {

  private final SchemaValidator schemaValidator;
  private final TilesProviders tilesProviders;

  @Inject
  QueryParameterLimitTile(SchemaValidator schemaValidator, TilesProviders tilesProviders) {
    this.schemaValidator = schemaValidator;
    this.tilesProviders = tilesProviders;
  }

  @Override
  public String getId() {
    return "limitTile";
  }

  @Override
  public String getName() {
    return "limit";
  }

  @Override
  public String getDescription() {
    return "The optional limit parameter limits the number of features that are included in the tile.";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return definitionPath.equals(
        "/collections/{collectionId}/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}");
  }

  private final ConcurrentMap<Integer, Map<String, Schema<?>>> schemaMap =
      new ConcurrentHashMap<>();

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    int apiHashCode = apiData.hashCode();
    if (!schemaMap.containsKey(apiHashCode)) schemaMap.put(apiHashCode, new ConcurrentHashMap<>());
    if (!schemaMap.get(apiHashCode).containsKey("*")) {
      Schema<?> schema = new IntegerSchema().minimum(BigDecimal.valueOf(0));

      Optional<Integer> limit =
          tilesProviders
              .getTileProvider(apiData)
              .map(TileProvider::getData)
              .map(TileProviderData::getTilesetDefaults)
              .filter(defaults -> defaults instanceof TileGenerationOptions)
              .flatMap(
                  defaults ->
                      Optional.ofNullable(((TileGenerationOptions) defaults).getFeatureLimit()));

      limit.ifPresent(integer -> schema.setDefault(BigDecimal.valueOf(integer)));

      schemaMap.get(apiHashCode).put("*", schema);
    }
    return schemaMap.get(apiHashCode).get("*");
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    int apiHashCode = apiData.hashCode();
    if (!schemaMap.containsKey(apiHashCode)) schemaMap.put(apiHashCode, new ConcurrentHashMap<>());
    if (!schemaMap.get(apiHashCode).containsKey(collectionId)) {
      Schema<?> schema = new IntegerSchema().minimum(BigDecimal.valueOf(0));

      Optional<Integer> limit =
          tilesProviders
              .getTileProvider(apiData, apiData.getCollectionData(collectionId))
              .map(TileProvider::getData)
              .map(TileProviderData::getTilesetDefaults)
              .filter(defaults -> defaults instanceof TileGenerationOptions)
              .flatMap(
                  defaults ->
                      Optional.ofNullable(((TileGenerationOptions) defaults).getFeatureLimit()));

      limit.ifPresent(integer -> schema.setDefault(BigDecimal.valueOf(integer)));

      schemaMap.get(apiHashCode).put(collectionId, schema);
    }
    return schemaMap.get(apiHashCode).get(collectionId);
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public Integer parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> collectionData) {
    if (Objects.isNull(value)) {
      return collectionData
          .flatMap(
              cd ->
                  tilesProviders
                      .getTileProvider(api.getData(), cd)
                      .map(TileProvider::getData)
                      .map(TileProviderData::getTilesetDefaults)
                      .filter(defaults -> defaults instanceof TileGenerationOptions)
                      .flatMap(
                          defaults ->
                              Optional.ofNullable(
                                  ((TileGenerationOptions) defaults).getFeatureLimit())))
          .filter(limit -> limit != 100000)
          .orElse(null);
    }

    try {
      return Integer.parseInt(value);
    } catch (Throwable e) {
      throw new IllegalArgumentException(
          String.format("Invalid value for query parameter '%s'.", getName()), e);
    }
  }

  @Override
  public void applyTo(
      ImmutableTileGenerationParametersTransient.Builder userParametersBuilder,
      QueryParameterSet parameters,
      Optional<TileGenerationSchema> generationSchema) {
    parameters.getValue(this).ifPresent(userParametersBuilder::limit);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return isEnabledForApi(apiData, tilesProviders);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return isEnabledForApi(apiData, collectionId, tilesProviders);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return TilesConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return Optional.of(SpecificationMaturity.DRAFT_LDPROXY);
  }
}
