/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.projections.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.base.Splitter;
import de.ii.ogcapi.features.core.domain.FeatureQueryParameter;
import de.ii.ogcapi.features.core.domain.SchemaInfo;
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
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery.Builder;
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
import java.util.concurrent.ConcurrentMap;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title properties
 * @endpoints Features, Feature, Vector Tile
 * @langEn The properties that should be included for each feature. The parameter value is a
 *     comma-separated list of property names. By default, all feature properties with a value are
 *     returned.
 * @langDe Die Eigenschaften, die für jedes Feature enthalten sein sollen. Der Parameterwert ist
 *     eine kommagetrennte Liste von Eigenschaftsnamen. Standardmäßig werden alle
 *     Feature-Eigenschaften mit einem Wert zurückgegeben.
 */
@Singleton
@AutoBind
public class QueryParameterProperties extends OgcApiQueryParameterBase
    implements TypedQueryParameter<List<String>>,
        FeatureQueryParameter,
        TileGenerationUserParameter,
        ConformanceClass {

  private final SchemaInfo schemaInfo;
  private final SchemaValidator schemaValidator;

  @Inject
  public QueryParameterProperties(SchemaInfo schemaInfo, SchemaValidator schemaValidator) {
    this.schemaInfo = schemaInfo;
    this.schemaValidator = schemaValidator;
  }

  @Override
  public String getId(String collectionId) {
    return "properties_" + collectionId;
  }

  @Override
  public String getName() {
    return "properties";
  }

  @Override
  public String getDescription() {
    return "The properties that should be included for each feature. The parameter value is a comma-separated list of property names. By default, all feature properties with a value are returned.";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return definitionPath.equals("/collections/{collectionId}/items")
        || definitionPath.equals("/collections/{collectionId}/items/{featureId}")
        || definitionPath.equals(
            "/collections/{collectionId}/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}");
  }

  private ConcurrentMap<Integer, ConcurrentMap<String, Schema<?>>> schemaMap =
      new ConcurrentHashMap<>();

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    int apiHashCode = apiData.hashCode();
    if (!schemaMap.containsKey(apiHashCode)) schemaMap.put(apiHashCode, new ConcurrentHashMap<>());
    if (!schemaMap.get(apiHashCode).containsKey("*")) {
      schemaMap.get(apiHashCode).put("*", new ArraySchema().items(new StringSchema()).minItems(1));
    }
    return schemaMap.get(apiHashCode).get("*");
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    int apiHashCode = apiData.hashCode();
    if (!schemaMap.containsKey(apiHashCode)) schemaMap.put(apiHashCode, new ConcurrentHashMap<>());
    if (!schemaMap.get(apiHashCode).containsKey(collectionId)) {
      schemaMap
          .get(apiHashCode)
          .put(
              collectionId,
              new ArraySchema()
                  .items(
                      new StringSchema()
                          ._enum(schemaInfo.getPropertyNames(apiData, collectionId, true, false)))
                  .minItems(1));
    }
    return schemaMap.get(apiHashCode).get(collectionId);
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProjectionsConfiguration.class;
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
      return Splitter.on(',').omitEmptyStrings().trimResults().splitToList(value);
    } catch (Throwable e) {
      throw new IllegalArgumentException(
          String.format("Invalid value for query parameter '%s'.", getName()), e);
    }
  }

  @Override
  public void applyTo(
      Builder queryBuilder,
      QueryParameterSet parameters,
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData) {
    parameters.getValue(this).ifPresent(queryBuilder::fields);
  }

  @Override
  public void applyTo(
      ImmutableTileGenerationParametersTransient.Builder userParametersBuilder,
      QueryParameterSet parameters,
      Optional<TileGenerationSchema> generationSchema) {
    parameters.getValue(this).ifPresent(userParametersBuilder::fields);
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return ProjectionsBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return ProjectionsBuildingBlock.SPEC;
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return List.of(
        "http://www.opengis.net/spec/ogcapi-features-6/0.0/conf/properties",
        "http://www.opengis.net/spec/ogcapi-features-6/0.0/conf/properties-features");
  }
}
