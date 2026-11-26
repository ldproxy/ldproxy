/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.async.app.parameter;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.base.Splitter;
import de.ii.ogcapi.async.app.AsyncBuildingBlock;
import de.ii.ogcapi.async.domain.AsyncConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @title outputs
 * @endpoints jobs/{jobId}/results
 * @langEn The outputs of the process that should be included in the results. The parameter value is
 *     a comma-separated list of output identifiers. By default, all outputs that were requested in
 *     the execute request are included. The parameter does not apply to a job whose result is a
 *     single document, e.g. the result of a search or of a transaction.
 * @langDe Die Ausgaben des Prozesses, die in die Ergebnisse aufgenommen werden sollen. Der
 *     Parameterwert ist eine durch Kommata getrennte Liste von Identifikatoren der Ausgaben.
 *     Standardmäßig sind alle Ausgaben enthalten, die im Ausführungsrequest angefordert wurden. Der
 *     Parameter gilt nicht für einen Job, dessen Ergebnis ein einzelnes Dokument ist, z. B. das
 *     Ergebnis einer Suche oder einer Transaktion.
 */
@Singleton
@AutoBind
public class QueryParameterOutputs extends OgcApiQueryParameterBase
    implements TypedQueryParameter<List<String>> {

  public static final String NAME = "outputs";

  private final SchemaValidator schemaValidator;
  private final Schema<?> schema = new ArraySchema().items(new StringSchema());

  @Inject
  public QueryParameterOutputs(SchemaValidator schemaValidator) {
    this.schemaValidator = schemaValidator;
  }

  @Override
  public String getId() {
    return "outputsAsync";
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getDescription() {
    return "The outputs of the process that should be included in the results. The parameter value is a comma-separated list of output identifiers. By default, all outputs that were requested in the execute request are included. The parameter does not apply to a job whose result is a single document, e.g. the result of a search or of a transaction.";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/jobs/{jobId}/results".equals(definitionPath);
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return schema;
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public List<String> parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> collectionData) {
    if (Objects.isNull(value)) {
      return null;
    }
    return Splitter.on(',').omitEmptyStrings().trimResults().splitToList(value);
  }

  // only jobs with named outputs have outputs to select
  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return AsyncBuildingBlock.isAsyncEnabled(apiData)
        && AsyncBuildingBlock.isNamedOutputsEnabled(apiData);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return AsyncConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return AsyncBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return AsyncBuildingBlock.SPEC;
  }
}
