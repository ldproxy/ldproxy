/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import de.ii.xtraplatform.base.domain.LogContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoMultiBind
public interface ParameterExtension extends ApiExtension {

  Logger LOGGER = LoggerFactory.getLogger(ParameterExtension.class);
  Schema<String> SCHEMA = new StringSchema();

  default String getId() {
    return getName();
  }

  default String getId(String collectionId) {
    return getId();
  }

  default String getId(Optional<String> collectionId) {
    return collectionId.isPresent() ? getId(collectionId.get()) : getId();
  }

  String getName();

  String getDescription();

  default boolean getRequired(OgcApiDataV2 apiData) {
    return false;
  }

  default boolean getRequired(
      OgcApiDataV2 apiData, @SuppressWarnings("unused") String collectionId) {
    return getRequired(apiData);
  }

  default boolean getRequired(OgcApiDataV2 apiData, Optional<String> collectionId) {
    return collectionId.map(s -> getRequired(apiData, s)).orElseGet(() -> getRequired(apiData));
  }

  default Schema<?> getSchema(OgcApiDataV2 apiData) {
    return SCHEMA;
  }

  default Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    return getSchema(apiData);
  }

  default Schema<?> getSchema(OgcApiDataV2 apiData, Optional<String> collectionId) {
    return collectionId.isPresent() ? getSchema(apiData, collectionId.get()) : getSchema(apiData);
  }

  default boolean getExplode() {
    return false;
  }

  /**
   * The schema that is used to validate the parameter values. By default this is the schema that is
   * also published in the API definition. Parameters that accept additional value forms, for
   * example wildcards, can relax the schema depending on the values that were submitted.
   */
  default Schema<?> getSchemaForValidation(
      OgcApiDataV2 apiData, Optional<String> collectionId, List<String> values) {
    return getSchema(apiData, collectionId);
  }

  default Optional<String> validate(
      OgcApiDataV2 apiData, Optional<String> collectionId, List<String> values) {
    // first validate against the schema
    Optional<String> result = validateSchema(apiData, collectionId, values);
    if (result.isPresent()) {
      return result;
    }
    // if the values are schema-valid, validate against any additional parameter-specific checks
    return validateOther(apiData, collectionId, values);
  }

  default Optional<String> validateOther(
      OgcApiDataV2 apiData, Optional<String> collectionId, List<String> values) {
    return Optional.empty();
  }

  // we can only inject the schema validator in the leaf classes, but we need it here, so we need to
  // make it
  // available via an interface method, although only validateSchema() should use the validator
  SchemaValidator getSchemaValidator();

  default Optional<String> validateSchema(
      OgcApiDataV2 apiData, Optional<String> collectionId, List<String> values) {
    try {
      Schema<?> schema = getSchemaForValidation(apiData, collectionId, values);
      if (Objects.isNull(schema)) {
        // a parameter without a schema cannot be schema-validated; validateOther() still runs
        LOGGER.warn(
            "Parameter '{}': no schema available for validation, skipping schema validation.",
            getName());
        return Optional.empty();
      }
      String type = schema.getType();
      String schemaContent = Json.mapper().writeValueAsString(schema);
      String valueContent = getJsonContent(values.get(0), schema);
      if (Objects.nonNull(type) && "array".equals(type)) {
        if (getExplode()) {
          // each value is an item
          schemaContent = Json.mapper().writeValueAsString(schema.getItems());
        } else {
          if (values.size() == 1) {
            valueContent =
                Splitter.on(",")
                    .trimResults()
                    .omitEmptyStrings()
                    .splitToList(values.get(0))
                    .stream()
                    .map(v -> getJsonContent(v, schema.getItems()))
                    .collect(Collectors.joining(",", "[", "]"));
          }
        }
      }

      return getSchemaValidator()
          .validate(schemaContent, valueContent)
          .map(
              s ->
                  String.format(
                      "Parameter value '%s' is invalid for parameter '%s': %s",
                      values, getName(), s));

    } catch (IOException e) {
      if (LOGGER.isDebugEnabled(LogContext.MARKER.STACKTRACE)) {
        LOGGER.debug(LogContext.MARKER.STACKTRACE, "Stacktrace: ", e);
      }
      return Optional.of(
          String.format(
              "An exception occurred while validating the parameter value '%s' for parameter '%s'",
              values, getName()));
    }
  }

  private String getJsonContent(String value, Schema<?> schema) {
    if (("object".equals(schema.getType()) && value.trim().startsWith("{"))
        || ("array".equals(schema.getType()) && value.trim().startsWith("["))
        || "number".equals(schema.getType())
        || "integer".equals(schema.getType())
        || "boolean".equals(schema.getType())
        || "null".equals(schema.getType())) {
      return value;
    }

    return "\"" + value + "\"";
  }

  default void setOpenApiDescription(OgcApiDataV2 apiData, Parameter param) {
    if (apiData
        .getExtension(FoundationConfiguration.class)
        .map(FoundationConfiguration::includesSpecificationInformation)
        .orElse(false)) {
      param.setDescription(
          getSpecificationMaturity()
              .map(
                  maturity ->
                      String.format(
                          "%s\n\n_%s_",
                          getDescription(), String.format(maturity.toString(), "parameter")))
              .orElse(getDescription()));
      getSpecificationMaturity()
          .ifPresent(
              maturity -> param.setExtensions(ImmutableMap.of("x-maturity", maturity.name())));
      getSpecificationMaturity()
          .filter(m -> Objects.equals(m, SpecificationMaturity.DEPRECATED))
          .ifPresent(ignore -> param.setDeprecated(true));
    } else {
      param.setDescription(getDescription());
    }
  }
}
