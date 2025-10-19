/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Funnel;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import de.ii.xtraplatform.values.domain.StoredValue;
import de.ii.xtraplatform.values.domain.ValueBuilder;
import de.ii.xtraplatform.values.domain.ValueEncoding.FORMAT;
import de.ii.xtraplatform.values.domain.annotations.FromValueStore;
import io.swagger.v3.oas.models.media.Schema;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true, deepImmutablesDetection = true, builder = "new")
@FromValueStore(type = "queries", defaultFormat = FORMAT.JSON)
@JsonDeserialize(builder = ImmutableStoredQueryExpression.Builder.class)
public interface StoredQueryExpression extends StoredValue, StoredQueryComponent {

  ObjectMapper MAPPER = QueryExpression.MAPPER;

  @SuppressWarnings("UnstableApiUsage")
  Funnel<StoredQueryExpression> FUNNEL =
      (from, into) -> {
        try {
          into.putString(MAPPER.writeValueAsString(from), StandardCharsets.UTF_8);
        } catch (JsonProcessingException ignore) {
          // ignore
          into.putInt(from.hashCode());
        }
      };

  String SCHEMA_REF = "#/components/schemas/StoredQueryExpression";
  String REF = "$ref";
  String PARAMETER = "$parameter";

  abstract class Builder implements ValueBuilder<StoredQueryExpression> {}

  static StoredQueryExpression of(InputStream requestBody) throws IOException {
    return MAPPER.readValue(requestBody, StoredQueryExpression.class);
  }

  String getId();

  Optional<String> getTitle();

  Optional<String> getDescription();

  List<SingleQueryWithParameters> getQueries();

  // If provided, it must be a list with a single string or parameter
  @JsonInclude(Include.NON_EMPTY)
  List<StringOrParameter> getCollections();

  // CQL2 filter object
  Optional<Cql2Expression> getFilter();

  // CRS URI or a parameter
  Optional<StringOrParameter> getFilterCrs();

  // FilterOperator or parameter
  Optional<FilterOperatorOrParameter> getFilterOperator();

  // List of string or parameter, or a parameter that is a string array
  Optional<ParameterOrListOfStringOrParameter> getSortby();

  // List of string or parameter, or a parameter that is a string array
  Optional<ParameterOrListOfStringOrParameter> getProperties();

  // CRS URI or a parameter
  Optional<StringOrParameter> getCrs();

  // CRS URI or a parameter
  Optional<StringOrParameter> getVerticalCrs();

  // Double or Parameter
  Optional<DoubleOrParameter> getMaxAllowableOffset();

  // Integer or Parameter
  Optional<IntegerOrParameter> getLimit();

  // should not be provided in a stored query as it will be set when executing the query
  Optional<Integer> getOffset();

  // List of string or parameter, or a parameter that is a string array
  Optional<ParameterOrListOfStringOrParameter> getProfiles();

  // If provided, it must be an object with schemas as the values
  @JsonInclude(Include.NON_EMPTY)
  Map<String, JsonSchema> getParameters();

  @JsonIgnore
  @Value.Check
  default void check() {
    Preconditions.checkState(
        getQueries().isEmpty() && getCollections().size() == 1
            || !getQueries().isEmpty() && getCollections().isEmpty(),
        "Either one or more queries must be provided or a single collection. Query: %s. Collections: %s.",
        getQueries(),
        getCollections());

    List<String> errors = this.accept(new StoredQueryValidator(getParameters(), Optional.empty()));
    Preconditions.checkState(
        errors.isEmpty(),
        "The stored query ''{0}'' is invalid: {1}",
        getId(),
        String.join("; ", errors));
  }

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default boolean hasParameters() {
    return !getAllParameters().isEmpty();
  }

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default Map<String, JsonSchema> getAllParameters() {
    return this.accept(new ParametersInStoredQuery(this.getParameters()));
  }

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default Set<String> getParameterNames() {
    return getAllParameters().keySet();
  }

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default Map<String, Schema<?>> getParametersWithOpenApiSchema() {
    OpenApiSchemaDeriver schemaDeriver = new OpenApiSchemaDeriver();
    return getAllParameters().entrySet().stream()
        .map(
            entry ->
                new SimpleImmutableEntry<>(entry.getKey(), entry.getValue().accept(schemaDeriver)))
        .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
