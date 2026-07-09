/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app.parameter;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import de.ii.ogcapi.processes.domain.ProcessesCoreConfiguration;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @title limit
 * @endpoints Processes
 * @langEn
 * @langDe
 */
@Singleton
@AutoBind
public class QueryParameterLimitProcesses extends OgcApiQueryParameterBase
    implements TypedQueryParameter<Integer> {

  private final SchemaValidator schemaValidator;

  private final ConcurrentMap<Integer, Schema<?>> schemaMap = new ConcurrentHashMap<>();

  @Inject
  public QueryParameterLimitProcesses(SchemaValidator schemaValidator) {
    this.schemaValidator = schemaValidator;
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/processes".equals(definitionPath);
  }

  @Override
  public String getId() {
    return "limitProcesses";
  }

  @Override
  public String getName() {
    return "limit";
  }

  private ProcessesCoreConfiguration getConfig(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(ProcessesCoreConfiguration.class)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Could not process query parameter '%s', paging default values not provided.",
                        getName())));
  }

  @Override
  public Optional<String> validateSchema(
      OgcApiDataV2 apiData, Optional<String> collectionId, List<String> values) {

    if (values.size() != 1)
      return Optional.of(
          String.format(
              "Parameter value '%s' is invalid for parameter '%s': The must be a single value.",
              values, getName()));

    int limit;
    try {
      limit = Integer.parseInt(values.get(0));
    } catch (NumberFormatException exception) {
      return Optional.of(
          String.format(
              "Parameter value '%s' is invalid for parameter '%s': The value is not an integer.",
              values, getName()));
    }

    ProcessesCoreConfiguration cfg = getConfig(apiData);

    // Only check minimum, the limit will be clamped in parse if too high.
    int minLimit = Integer.max(cfg.getMinimumPageSize(), 1);
    if (limit < minLimit)
      return Optional.of(
          String.format(
              "Parameter value '%s' is invalid for parameter '%s': The value is smaller than the minimum value '%d'.",
              values, getName(), minLimit));

    return Optional.empty();
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    int apiHashCode = apiData.hashCode();

    if (!schemaMap.containsKey(apiHashCode)) {
      schemaMap.put(apiHashCode, new IntegerSchema());

      ProcessesCoreConfiguration cfg = getConfig(apiData);

      Schema<?> schema = schemaMap.get(apiHashCode);
      schema.setDefault(BigDecimal.valueOf(cfg.getDefaultPageSize()));
      schema.setMinimum(BigDecimal.valueOf(cfg.getMinimumPageSize()));
      schema.setMaximum(BigDecimal.valueOf(cfg.getMaximumPageSize()));
    }
    return schemaMap.get(apiHashCode);
  }

  // ToDo Evaluate: Keep defensive code or omit since `validateSchema` already enforces
  // requirements?
  @Override
  public Integer parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {

    ProcessesCoreConfiguration cfg = getConfig(api.getData());

    if (Objects.isNull(value)) {
      return cfg.getDefaultPageSize();
    }

    int limit;
    try {
      limit = Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid value for query parameter '%s'. The value must be a non-negative non-zero integer. Found: %s.",
              getName(), value),
          e);
    }

    // Never give the client less than allowed / more than it asked for
    if (limit < Integer.max(cfg.getMinimumPageSize(), 1)) {
      throw new IllegalArgumentException(
          "Invalid value for query parameter 'limit'. The value must be at least "
              + cfg.getMinimumPageSize()
              + ". Found: "
              + value);
    }

    // Never give the client more than allowed
    if (limit > cfg.getMaximumPageSize()) {
      limit = cfg.getMaximumPageSize();
    }

    return limit;
  }

  public Optional<Integer> parse(QueryParameterSet parameters) {
    if (parameters.getTypedValues().containsKey(getName())) {
      Integer value = (Integer) parameters.getTypedValues().get(getName());
      return Optional.ofNullable(value);
    }
    return Optional.empty();
  }

  @Override
  public String getDescription() {
    return "The optional limit parameter limits the number of process descriptions that are included in the list.";
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return Optional.of(SpecificationMaturity.DRAFT_OGC);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProcessesCoreConfiguration.class;
  }
}
