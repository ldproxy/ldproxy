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
import de.ii.ogcapi.processes.domain.model.ProcessRepository;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// ToDo: Docs
/**
 * @title processId
 * @endpoints process
 * @langEn The identifier of a process.
 * @langDe Der Identifikator eines Prozesses.
 */
@Singleton
@AutoBind
public class PathParameterProcessId implements OgcApiPathParameter {

  public static final String PROCESS_ID_REGEX = "\\w+";

  private final ConcurrentMap<Integer, Schema<?>> schemaMap = new ConcurrentHashMap<>();
  private final SchemaValidator schemaValidator;
  private final ProcessRepository repository;

  @Inject
  public PathParameterProcessId(SchemaValidator schemaValidator, ProcessRepository repository) {
    this.schemaValidator = schemaValidator;
    this.repository = repository;
  }

  @Override
  public String getPattern() {
    return PROCESS_ID_REGEX;
  }

  @Override
  public List<String> getValues(OgcApiDataV2 apiData) {
    return repository.getAll().keySet().stream().toList();
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    if (!schemaMap.containsKey(apiData.hashCode())) {
      schemaMap.put(
          apiData.hashCode(), new StringSchema()._enum(ImmutableList.copyOf(getValues(apiData))));
    }

    return schemaMap.get(apiData.hashCode());
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public String getId() {
    return "processIdProcesses";
  }

  @Override
  public String getName() {
    return "processId";
  }

  @Override
  public String getDescription() {
    return "The local identifier of a process, unique within the API.";
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
        && ("/processes/{processId}".equals(definitionPath)
            || ("/processes/{processId}/execution".equals(definitionPath)));
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
