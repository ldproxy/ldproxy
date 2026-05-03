/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.geometry.simplification.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeatureQueryParameter;
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
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery.Builder;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @title maxAllowableOffset
 * @endpoints Features, Feature
 * @langEn *Deprecated* (replaced by `zoom-level`). All geometries are simplified using the [Douglas
 *     Peucker
 *     algorithm](https://en.wikipedia.org/wiki/Ramer%E2%80%93Douglas%E2%80%93Peucker_algorithm).
 *     The value defines the maximum distance between original and simplified geometry ([Hausdorff
 *     distance](https://en.wikipedia.org/wiki/Hausdorff_distance)). The value has to use the unit
 *     of the given coordinate reference system (`CRS84` or the value of parameter `crs`).
 * @langDe *Deprecated* (ersetzt durch `zoom-level`). Alle Geometrien werden mit dem
 *     [Douglas-Peucker-Algorithmus](https://en.wikipedia.org/wiki/Ramer%E2%80%93Douglas%E2%80%93Peucker_algorithm)
 *     vereinfacht. Der Wert von `maxAllowableOffset` legt den maximalen Abstand zwischen der
 *     Originalgeometrie und der vereinfachten Geometrie fest
 *     ([Hausdorff-Abstand](https://en.wikipedia.org/wiki/Hausdorff_distance)). Der Wert ist in den
 *     Einheiten des Koordinatenreferenzsystems der Ausgabe (`CRS84` bzw. der Wert des Parameters
 *     Query-Parameters `crs`) angegeben.
 */
@Singleton
@AutoBind
@Deprecated(since = "4.2.0", forRemoval = true)
public class QueryParameterMaxAllowableOffsetFeatures extends OgcApiQueryParameterBase
    implements FeatureQueryParameter, TypedQueryParameter<Double> {

  private final SchemaValidator schemaValidator;

  @Inject
  QueryParameterMaxAllowableOffsetFeatures(SchemaValidator schemaValidator) {
    this.schemaValidator = schemaValidator;
  }

  @Override
  public String getName() {
    return "maxAllowableOffset";
  }

  @Override
  public Double parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    if (Objects.isNull(value)) {
      // no default value
      return null;
    }

    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid value for query parameter '%s'. The value must be a number. Found: %s.",
              getName(), value),
          e);
    }
  }

  @Override
  public void applyTo(
      Builder queryBuilder,
      QueryParameterSet parameters,
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData) {
    parameters.getValue(this).ifPresent(queryBuilder::maxAllowableOffset);
  }

  @Override
  public String getDescription() {
    return "This option can be used to specify the maxAllowableOffset to be used for simplifying the geometries in the response. "
        + "The maxAllowableOffset is in the units of the response coordinate reference system.";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/collections/{collectionId}/items".equals(definitionPath)
        || "/collections/{collectionId}/items/{featureId}".equals(definitionPath);
  }

  private final Schema<?> schema = new NumberSchema()._default(BigDecimal.valueOf(0)).example(0.05);

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return schema;
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    return schema;
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return GeometrySimplificationConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return GeometrySimplificationBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return GeometrySimplificationBuildingBlock.SPEC;
  }
}
