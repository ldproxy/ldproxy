/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.search.domain.ImmutableStoredQueryExpression;
import de.ii.ogcapi.features.search.domain.QueryExpressionQueryParameter;
import de.ii.ogcapi.features.search.domain.SearchConfiguration;
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
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title offset
 * @endpoints Stored Query
 * @langEn The index of the first feature in the response in the overall result set. This parameter
 *     is used for response paging.
 * @langDe Der Index des ersten Features in der Antwort in der Gesamtergebnismenge. Dieser Parameter
 *     wird für das Paging verwendet.
 */
@Singleton
@AutoBind
public class QueryParameterOffsetStoredQuery extends OgcApiQueryParameterBase
    implements TypedQueryParameter<Integer>, QueryExpressionQueryParameter {

  private Schema<?> schema;

  private final SchemaValidator schemaValidator;

  @Inject
  QueryParameterOffsetStoredQuery(SchemaValidator schemaValidator) {
    super();
    this.schemaValidator = schemaValidator;
  }

  @Override
  public String getId() {
    return "offsetStoredQuery";
  }

  @Override
  public String getName() {
    return "offset";
  }

  @Override
  public Integer parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    try {
      return Objects.nonNull(value) ? Integer.parseInt(value) : 0;
    } catch (Throwable e) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid value for query parameter '%s'. The value must be a non-negative integer. Found: %s.",
              getName(), value),
          e);
    }
  }

  @Override
  public String getDescription() {
    return "The optional offset parameter identifies the index of the first feature in the response in the overall "
        + "result set.";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/search/{queryId}".equals(definitionPath);
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    if (schema == null) {
      schema = new IntegerSchema()._default(0).minimum(BigDecimal.ZERO);
    }
    return schema;
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return SearchConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return SearchBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return SearchBuildingBlock.SPEC;
  }

  @Override
  public void applyTo(
      ImmutableStoredQueryExpression.Builder builder, QueryParameterSet queryParameterSet) {
    builder.offset(queryParameterSet.getValue(this));
  }
}
