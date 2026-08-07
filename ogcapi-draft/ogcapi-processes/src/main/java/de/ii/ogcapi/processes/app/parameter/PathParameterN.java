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
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * @title index N
 * @endpoints jobs/{jobId}/results/{outputId}/{N}
 * @langEn The zero-based index to access a value of a multi-valued output.
 * @langDe Der nullbasierte Index für den Zugriff auf einen Wert eines mehrwertigen Outputs.
 */
@Singleton
@AutoBind
public class PathParameterN implements OgcApiPathParameter {

  public static final String N_REGEX = "[0-9]+";

  private final SchemaValidator schemaValidator;

  @Inject
  public PathParameterN(SchemaValidator schemaValidator) {
    this.schemaValidator = schemaValidator;
  }

  @Override
  public String getPattern() {
    return N_REGEX;
  }

  @Override
  public List<String> getValues(OgcApiDataV2 apiData) {
    return ImmutableList.of("*");
  }

  private final Schema<?> schema = new IntegerSchema().minimum(BigDecimal.ZERO);

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return schema;
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public String getId() {
    return "NProcesses";
  }

  @Override
  public String getName() {
    return "N";
  }

  @Override
  public String getDescription() {
    return "The index of which to access a multi-valued output.";
  }

  @Override
  public boolean isApplicable(OgcApiDataV2 apiData, String definitionPath) {
    return isEnabledForApi(apiData)
        && "/jobs/{jobId}/results/{outputId}/{N}".equals(definitionPath);
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
