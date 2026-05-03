/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeatureTransformationQueryParameter;
import de.ii.ogcapi.features.core.domain.ImmutableFeatureTransformationContextGeneric.Builder;
import de.ii.ogcapi.features.geojson.domain.GeoJsonConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * @langEn Debug option in development environments: Log debug information for the GeoJSON output.
 * @langDe Debug-Option in Entwicklungsumgebungen: Protokolliert Debug-Informationen für die
 *     GeoJSON-Ausgabe.
 * @title debug
 * @endpoints Features
 */
@Singleton
@AutoBind
public class QueryParameterDebugFeaturesGeoJson extends OgcApiQueryParameterBase
    implements TypedQueryParameter<Boolean>, FeatureTransformationQueryParameter {

  private final Schema<?> schema = new BooleanSchema()._default(false);
  private final boolean allowDebug;
  private final SchemaValidator schemaValidator;

  @Inject
  public QueryParameterDebugFeaturesGeoJson(
      AppContext appContext, SchemaValidator schemaValidator) {
    this.allowDebug = appContext.isDevEnv();
    this.schemaValidator = schemaValidator;
  }

  @Override
  public String getName() {
    return "debug";
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
  public String getDescription() {
    return "Debug option in development environments: Log debug information for the GeoJSON output.";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return definitionPath.equals("/collections/{collectionId}/items")
        || definitionPath.equals("/collections/{collectionId}/items/{featureId}");
  }

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
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return super.isEnabledForApi(apiData) && allowDebug;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData, collectionId) && allowDebug;
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
    queryParameterSet.getValue(this).ifPresent(builder::debug);
  }
}
