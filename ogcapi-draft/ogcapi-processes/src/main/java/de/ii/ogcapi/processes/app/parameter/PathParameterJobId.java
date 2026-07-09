/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app.parameter;

import com.github.azahnen.dagger.annotations.AutoBind;
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

// ToDo: Docs

/**
 * @title jobId
 * @endpoints jobs/{jobId}
 * @langEn The identifier of a job.
 * @langDe Der Identifikator eines Jobs.
 */
@Singleton
@AutoBind
public class PathParameterJobId implements OgcApiPathParameter {

  public static final String JOB_ID_REGEX = "[\\w-]+";

  private final SchemaValidator schemaValidator;

  @Inject
  public PathParameterJobId(SchemaValidator schemaValidator) {
    this.schemaValidator = schemaValidator;
  }

  @Override
  public String getPattern() {
    return JOB_ID_REGEX;
  }

  @Override
  public List<String> getValues(OgcApiDataV2 apiData) {
    return List.of();
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    Schema<String> schema = new StringSchema();
    schema.format("uuid");
    return schema;
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public String getId() {
    return "jobIdProcesses";
  }

  @Override
  public String getName() {
    return "jobId";
  }

  @Override
  public String getDescription() {
    return "The local identifier of a job, unique within the API.";
  }

  @Override
  public boolean isApplicable(OgcApiDataV2 apiData, String definitionPath, String collectionId) {
    // ToDo Shouldn't this return true?
    if (isApplicable(apiData, definitionPath)) return false;

    return apiData
        .getExtension(ProcessesCoreConfiguration.class)
        .map(ExtensionConfiguration::isEnabled)
        .orElse(true);
  }

  @Override
  public boolean isApplicable(OgcApiDataV2 apiData, String definitionPath) {
    return isEnabledForApi(apiData)
        && ("/jobs/{jobId}".equals(definitionPath)
            || "/jobs/{jobId}/results".equals(definitionPath));
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
