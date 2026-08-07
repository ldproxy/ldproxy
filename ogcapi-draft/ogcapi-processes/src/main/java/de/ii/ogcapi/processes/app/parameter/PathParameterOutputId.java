/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app.parameter;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.processes.app.ProcessesCoreBuildingBlock;
import de.ii.ogcapi.processes.domain.ProcessesCoreConfiguration;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;

/**
 * @title outputId
 * @endpoints jobs/{jobId}/results/{outputId}, jobs/{jobId}/results/{outputId}/{N}
 * @langEn The identifier of a job's output.
 * @langDe Der Identifikator eines Outputs von einem Job.
 */
@Singleton
@AutoBind
public class PathParameterOutputId implements OgcApiPathParameter {

  public static final String OUTPUT_ID_REGEX = "[^/ ]+";

  private final SchemaValidator schemaValidator;

  @Inject
  public PathParameterOutputId(SchemaValidator schemaValidator) {
    this.schemaValidator = schemaValidator;
  }

  @Override
  public String getPattern() {
    return OUTPUT_ID_REGEX;
  }

  @Override
  public List<String> getValues(OgcApiDataV2 apiData) {
    return ImmutableList.of("*");
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
  public String getId() {
    return "outputIdProcesses";
  }

  @Override
  public String getName() {
    return "outputId";
  }

  @Override
  public String getDescription() {
    return "The identifier of an output of a job as described in the process description of the executed process.";
  }

  @Override
  public boolean isApplicable(OgcApiDataV2 apiData, String definitionPath) {
    return isEnabledForApi(apiData)
        && ("/jobs/{jobId}/results/{outputId}".equals(definitionPath)
            || "/jobs/{jobId}/results/{outputId}/{N}".equals(definitionPath));
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProcessesCoreConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return ProcessesCoreBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return ProcessesCoreBuildingBlock.SPEC;
  }
}
