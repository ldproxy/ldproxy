/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.base.Splitter;
import de.ii.ogcapi.features.core.domain.FeatureQueryParameter;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
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
import de.ii.xtraplatform.features.domain.FeatureStream.PipelineSteps;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @langAll Debug option in development environments: Skip feature transformation pipeline steps.
 * @title pipeline-skip
 * @endpoints Features
 */
@Singleton
@AutoBind
public class QueryParameterDebugPipelineSteps extends OgcApiQueryParameterBase
    implements TypedQueryParameter<List<PipelineSteps>>, FeatureQueryParameter {

  private final Schema<?> schema =
      new ArraySchema()
          .items(
              new StringSchema()
                  ._enum(
                      Arrays.stream(PipelineSteps.values())
                          .map(Enum::name)
                          .map(String::toLowerCase)
                          .collect(Collectors.toList())));
  private final boolean isDevEnv;
  private final SchemaValidator schemaValidator;

  @Inject
  public QueryParameterDebugPipelineSteps(AppContext appContext, SchemaValidator schemaValidator) {
    this.isDevEnv = appContext.isDevEnv();
    this.schemaValidator = schemaValidator;
  }

  @Override
  public String getName() {
    return "pipeline-skip";
  }

  @Override
  public List<PipelineSteps> parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    if (Objects.isNull(value)) {
      // no default value
      return null;
    }

    try {
      return Splitter.on(',')
          .omitEmptyStrings()
          .trimResults()
          .splitToStream(value)
          .map(String::toUpperCase)
          .map(PipelineSteps::valueOf)
          .collect(Collectors.toList());
    } catch (Throwable e) {
      throw new IllegalArgumentException(
          String.format("Invalid value for query parameter '%s'.", getName()), e);
    }
  }

  @Override
  public String getDescription() {
    return "Debug option in development environments: Skip feature transformation pipeline steps.";
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
    return isDevEnv && super.isEnabledForApi(apiData);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return isDevEnv && super.isEnabledForApi(apiData, collectionId);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return FeaturesCoreConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return Optional.of(SpecificationMaturity.DRAFT_LDPROXY);
  }

  @Override
  public void applyTo(
      ImmutableFeatureQuery.Builder queryBuilder,
      QueryParameterSet parameters,
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData) {
    parameters.getValue(this).ifPresent(queryBuilder::debugSkipPipelineSteps);
  }
}
