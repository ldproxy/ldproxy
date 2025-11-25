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
import com.google.common.base.Preconditions;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.immutables.value.Value;

public interface StoredQueryBase extends StoredQueryComponent {

  String getId();

  Optional<String> getTitle();

  Optional<String> getDescription();

  List<SingleQueryWithParameters> getQueries();

  // If provided, it must be a list with a single string or parameter
  @JsonInclude(Include.NON_EMPTY)
  List<StringOrParameter> getCollections();

  // CQL2 filter object
  Optional<Map<String, Object>> getFilter();

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
  @Nullable
  String getStableHash();

  @JsonIgnore
  @Value.Check
  default void check() {
    Preconditions.checkState(
        getQueries().isEmpty() && getCollections().size() == 1
            || !getQueries().isEmpty() && getCollections().isEmpty(),
        "Either one or more queries must be provided or a single collection. Query: %s. Collections: %s.",
        getQueries(),
        getCollections());

    List<String> errors = this.accept(new StoredQueryValidator(getParameters()));
    Preconditions.checkState(
        errors.isEmpty(), "The stored query %s is invalid: %s", getId(), String.join("; ", errors));
  }
}
