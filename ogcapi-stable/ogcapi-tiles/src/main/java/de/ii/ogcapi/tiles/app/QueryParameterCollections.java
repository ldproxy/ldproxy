/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
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
import de.ii.ogcapi.tiles.domain.TileGenerationUserParameter;
import de.ii.ogcapi.tiles.domain.TilesConfiguration;
import de.ii.ogcapi.tiles.domain.TilesProviders;
import de.ii.xtraplatform.features.domain.FeatureTypeConfiguration;
import de.ii.xtraplatform.tiles.domain.ImmutableTileGenerationParametersTransient;
import de.ii.xtraplatform.tiles.domain.TileGenerationSchema;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * @langEn The collections of the dataset that should be included in the tile. The parameter value
 *     is a comma-separated list of collection identifiers.
 * @langDe Die Feature Collections des Datensatzes, die in die Kachel aufgenommen werden sollen. Der
 *     Parameterwert ist eine durch Kommata getrennte Liste von Identifikatoren der Feature
 *     Collections.
 * @title collections
 * @endpoints Dataset Tile
 */
@Singleton
@AutoBind
public class QueryParameterCollections extends OgcApiQueryParameterBase
    implements ConformanceClass, TypedQueryParameter<List<String>>, TileGenerationUserParameter {

  private final SchemaValidator schemaValidator;
  private final TilesProviders tilesProviders;

  @Inject
  QueryParameterCollections(SchemaValidator schemaValidator, TilesProviders tilesProviders) {
    this.schemaValidator = schemaValidator;
    this.tilesProviders = tilesProviders;
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of(
        "http://www.opengis.net/spec/ogcapi-tiles-1/1.0/conf/collection-selection");
  }

  @Override
  public String getName() {
    return "collections";
  }

  @Override
  public String getDescription() {
    return "The collections that should be included. The parameter value is a comma-separated list of collection names.";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return definitionPath.equals("/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}");
  }

  private final Map<Integer, Schema<?>> schemaMap = new ConcurrentHashMap<>();
  private final Map<Integer, List<String>> collectionsMap = new ConcurrentHashMap<>();

  private List<String> getCollectionIds(OgcApiDataV2 apiData) {
    int apiHashCode = apiData.hashCode();
    if (!collectionsMap.containsKey(apiHashCode)) {
      collectionsMap.put(
          apiHashCode,
          apiData.getCollections().values().stream()
              .filter(collection -> apiData.isCollectionEnabled(collection.getId()))
              .filter(
                  collection ->
                      collection
                          .getExtension(TilesConfiguration.class)
                          .filter(ExtensionConfiguration::isEnabled)
                          .isPresent())
              .map(FeatureTypeConfiguration::getId)
              .collect(Collectors.toList()));
    }
    return collectionsMap.get(apiHashCode);
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    int apiHashCode = apiData.hashCode();
    if (!schemaMap.containsKey(apiHashCode)) {
      schemaMap.put(
          apiHashCode,
          new ArraySchema().items(new StringSchema()._enum(getCollectionIds(apiData))));
    }
    return schemaMap.get(apiHashCode);
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return isEnabledForApi(apiData, tilesProviders);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return TilesConfiguration.class;
  }

  @Override
  public List<String> parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> collectionData) {
    if (Objects.isNull(value)) {
      // no default value
      return null;
    }

    try {
      List<String> collections = getCollectionIds(api.getData());
      return Splitter.on(',').omitEmptyStrings().trimResults().splitToList(value).stream()
          .filter(collections::contains)
          .collect(ImmutableList.toImmutableList());
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
    parameters.getValue(this).ifPresent(userParametersBuilder::addAllLayers);
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
