/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.filter.api;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeatureQueryParameter;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.filter.domain.FilterConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import de.ii.xtraplatform.cql.domain.Cql;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.crs.domain.CrsInfo;
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.Optional;

// Internal variant of the filter parameter for single features. It cannot be set by clients and is
// not part of the API definition, it is only used to apply filters from policy obligations. Parsing
// and validation are delegated to the public filter parameter.
@Singleton
@AutoBind
public class QueryParameterFilterFeature extends OgcApiQueryParameterBase
    implements FeatureQueryParameter, TypedQueryParameter<Cql2Expression> {

  private final QueryParameterFilter queryParameterFilter;

  @Inject
  public QueryParameterFilterFeature(
      FeaturesCoreProviders providers,
      SchemaValidator schemaValidator,
      CrsInfo crsInfo,
      Cql cql,
      CrsTransformerFactory crsTransformerFactory) {
    this.queryParameterFilter =
        new QueryParameterFilter(providers, schemaValidator, crsInfo, cql, crsTransformerFactory);
  }

  @Override
  public String getName() {
    return queryParameterFilter.getName();
  }

  @Override
  public String getDescription() {
    return "Internal parameter to restrict access to a single feature, e.g. from a policy obligation.";
  }

  @Override
  public boolean isInternal() {
    return true;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return queryParameterFilter.isEnabledForApi(apiData);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return queryParameterFilter.isEnabledForApi(apiData, collectionId);
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/collections/{collectionId}/items/{featureId}".equals(definitionPath);
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return queryParameterFilter.getSchema(apiData);
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    return queryParameterFilter.getSchema(apiData, collectionId);
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return queryParameterFilter.getSchemaValidator();
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return FilterConfiguration.class;
  }

  @Override
  public boolean isFilterParameter() {
    return true;
  }

  @Override
  public int getPriority() {
    return queryParameterFilter.getPriority();
  }

  @Override
  public Cql2Expression parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    return queryParameterFilter.parse(value, typedValues, api, optionalCollectionData);
  }

  @Override
  public Cql2Expression mergeValues(Object value1, Object value2) {
    return queryParameterFilter.mergeValues(value1, value2);
  }
}
