/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.resources.domain;

import static de.ii.ogcapi.resources.domain.StoredQueryExpression.PARAMETER;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true, deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableSingleQueryWithParameters.Builder.class)
public interface SingleQueryWithParameters {

  @JsonIgnore String SCHEMA_REF = "#/components/schemas/Query";

  // If provided, it must be a list with a single string or parameter
  List<JsonNode> getCollections();

  // a CQL2 filter object
  Optional<JsonNode> getFilter();

  // List of string or parameter, or a parameter that is a string array
  Optional<JsonNode> getSortby();

  // List of string or parameter, or a parameter that is a string array
  Optional<JsonNode> getProperties();

  @JsonIgnore
  @Value.Check
  default void check() {
    Preconditions.checkState(
        !getCollections().isEmpty(), "Each query must have a collection. Found no collection.");
    Preconditions.checkState(
        getCollections().size() < 2,
        "Each query must be for a single collection. Join queries are currently not supported. Found collections: %s.",
        getCollections());
    Preconditions.checkState(
        getCollections().stream()
            .allMatch(v -> v.isTextual() || (v.isObject() && v.has(PARAMETER))),
        "Each collection must be a string or a parameter. Collections: %s.",
        getCollections());
    Preconditions.checkState(
        getSortby().isEmpty()
            || getSortby().filter(v -> v.isObject() && v.has(PARAMETER)).isPresent()
            || getSortby().stream()
                .allMatch(v -> v.isTextual() || (v.isObject() && v.has(PARAMETER))),
        "Sortby must be a list of strings/parameters or a parameter that is a string array. Value: %s.",
        getSortby());
    Preconditions.checkState(
        getProperties().isEmpty()
            || getProperties().filter(v -> v.isObject() && v.has(PARAMETER)).isPresent()
            || getProperties().stream()
                .allMatch(v -> v.isTextual() || (v.isObject() && v.has(PARAMETER))),
        "Properties must be a list of strings/parameters or a parameter that is a string array. Value: %s.",
        getProperties());
  }
}
