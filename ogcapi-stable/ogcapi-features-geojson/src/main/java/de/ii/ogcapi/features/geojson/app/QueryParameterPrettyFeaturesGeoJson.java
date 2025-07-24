/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.common.domain.QueryParameterPretty;
import de.ii.ogcapi.features.core.domain.FeatureTransformationQueryParameter;
import de.ii.ogcapi.features.core.domain.ImmutableFeatureTransformationContextGeneric.Builder;
import de.ii.ogcapi.features.geojson.domain.GeoJsonConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import de.ii.xtraplatform.base.domain.AppContext;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @langEn Debug option in development environments: Pretty print the GeoJSON output.
 * @langDe Debug-Option in Entwicklungsumgebungen: Formatierte GeoJSON-Ausgabe.
 * @title pretty
 * @endpoints Features
 */
@Singleton
@AutoBind
public class QueryParameterPrettyFeaturesGeoJson extends QueryParameterPretty
    implements TypedQueryParameter<Boolean>, FeatureTransformationQueryParameter {

  private final Schema<?> schema = new BooleanSchema()._default(false);
  private final boolean allowDebug;

  @Inject
  public QueryParameterPrettyFeaturesGeoJson(
      AppContext appContext, SchemaValidator schemaValidator, ExtensionRegistry extensionRegistry) {
    super(extensionRegistry, schemaValidator);
    this.allowDebug = appContext.isDevEnv();
  }

  @Override
  public Boolean parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    return Objects.nonNull(value) && Boolean.parseBoolean(value);
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return definitionPath.equals("/collections/{collectionId}/items")
        || definitionPath.equals("/collections/{collectionId}/items/{featureId}");
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    return schema;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return super.isEnabledForApi(apiData);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData, collectionId);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return GeoJsonConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return Optional.of(SpecificationMaturity.DRAFT_LDPROXY);
  }

  @Override
  public void applyTo(Builder builder, QueryParameterSet queryParameterSet) {
    queryParameterSet.getValue(this).ifPresent(builder::prettify);
  }
}
