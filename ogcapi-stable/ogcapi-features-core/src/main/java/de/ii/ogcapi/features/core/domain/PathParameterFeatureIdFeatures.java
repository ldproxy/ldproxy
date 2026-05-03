/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.core.app.FeaturesCoreBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import java.util.Optional;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title featureId
 * @endpoints Feature
 * @langEn The local identifier of the feature in the feature collection `collectionId`.
 * @langDe Der lokale Identifikator des Features in der Feature Collection `collectionId`.
 */
@Singleton
@AutoBind
public class PathParameterFeatureIdFeatures implements OgcApiPathParameter {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(PathParameterFeatureIdFeatures.class);

  public static final String FEATURE_ID_PATTERN = "[^/ ]+";

  private final SchemaValidator schemaValidator;

  @Inject
  public PathParameterFeatureIdFeatures(SchemaValidator schemaValidator) {
    this.schemaValidator = schemaValidator;
  }
  ;

  @Override
  public String getPattern() {
    return FEATURE_ID_PATTERN;
  }

  @Override
  public List<String> getValues(OgcApiDataV2 apiData) {
    return ImmutableList.of();
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return new StringSchema().pattern(getPattern());
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public String getName() {
    return "featureId";
  }

  @Override
  public String getDescription() {
    return "The local identifier of a feature, unique within the feature collection.";
  }

  @Override
  public boolean isApplicable(OgcApiDataV2 apiData, String definitionPath) {
    return isEnabledForApi(apiData)
        && definitionPath.equals("/collections/{collectionId}/items/{featureId}");
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return FeaturesCoreConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return FeaturesCoreBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return FeaturesCoreBuildingBlock.SPEC;
  }
}
