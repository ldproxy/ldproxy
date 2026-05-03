/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.app;

import static de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration.DATETIME_INTERVAL_SEPARATOR;
import static de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration.PARAMETER_DATETIME;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.core.domain.AbstractQueryParameterDatetime;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import de.ii.ogcapi.tiles.domain.TileGenerationUserParameter;
import de.ii.ogcapi.tiles.domain.TilesConfiguration;
import de.ii.ogcapi.tiles.domain.TilesProviders;
import de.ii.xtraplatform.cql.domain.Interval;
import de.ii.xtraplatform.cql.domain.Property;
import de.ii.xtraplatform.cql.domain.TIntersects;
import de.ii.xtraplatform.cql.domain.TemporalLiteral;
import de.ii.xtraplatform.tiles.domain.ImmutableTileGenerationParametersTransient;
import de.ii.xtraplatform.tiles.domain.TileGenerationSchema;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * @title datetime
 * @endpoints Dataset Tile, Collection Tile
 * @langEn Include only features in the tile that have a primary instant or interval that intersects
 *     the provided instant or interval.
 * @langDe Es werden nur Features in die Kachel aufgenommen, deren primäre zeitliche Eigenschaft den
 *     angegebenen Wert (Zeitstempel, Datum oder Intervall) schneidet.
 */
@Singleton
@AutoBind
public class QueryParameterDatetimeTile extends AbstractQueryParameterDatetime
    implements TypedQueryParameter<TemporalLiteral>, TileGenerationUserParameter, ConformanceClass {

  private final TilesProviders tilesProviders;

  @Inject
  QueryParameterDatetimeTile(SchemaValidator schemaValidator, TilesProviders tilesProviders) {
    super(schemaValidator);
    this.tilesProviders = tilesProviders;
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return definitionPath.equals(
        "/collections/{collectionId}/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}");
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
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of("http://www.opengis.net/spec/ogcapi-tiles-1/1.0/conf/datetime");
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return TilesConfiguration.class;
  }

  @Override
  public TemporalLiteral parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> collectionData) {
    if (Objects.isNull(value)) {
      // no default value
      return null;
    }

    try {
      if (value.contains(DATETIME_INTERVAL_SEPARATOR)) {
        return TemporalLiteral.of(Splitter.on(DATETIME_INTERVAL_SEPARATOR).splitToList(value));
      }
      return TemporalLiteral.of(value);
    } catch (Throwable e) {
      throw new IllegalArgumentException(
          "Invalid value for query parameter '" + PARAMETER_DATETIME + "'.", e);
    }
  }

  @Override
  public void applyTo(
      ImmutableTileGenerationParametersTransient.Builder userParametersBuilder,
      QueryParameterSet parameters,
      Optional<TileGenerationSchema> generationSchema) {
    parameters
        .getValue(this)
        .ifPresent(
            temporalLiteral -> {
              Optional<TIntersects> temporalFilter =
                  generationSchema
                      .flatMap(TileGenerationSchema::getTemporalProperty)
                      .map(temporalProperty -> toFilter(temporalProperty, temporalLiteral));

              temporalFilter.ifPresent(userParametersBuilder::addFilters);
            });
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return TilesBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return TilesBuildingBlock.SPEC;
  }

  private static TIntersects toFilter(String property, TemporalLiteral literal) {
    if (property.contains(DATETIME_INTERVAL_SEPARATOR)) {
      Interval interval =
          Interval.of(
              Splitter.on(DATETIME_INTERVAL_SEPARATOR).splitToList(property).stream()
                  .map(Property::of)
                  .collect(Collectors.toList()));

      return TIntersects.of(interval, literal);
    }

    return TIntersects.of(Property.of(property), literal);
  }
}
