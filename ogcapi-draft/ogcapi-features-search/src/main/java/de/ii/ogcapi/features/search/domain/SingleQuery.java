/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true, deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableSingleQuery.Builder.class)
public interface SingleQuery {

  @JsonIgnore String SCHEMA_REF = "#/components/schemas/Query";

  List<String> getCollections(); // String or Parameter

  Optional<Cql2Expression> getFilter();

  List<String> getSortby(); // String or Parameter

  List<String> getProperties(); // String or Parameter

  Map<String, ResultSetDefinition> getResultSets();

  // shorthand for a single result set with the ids of the selected features
  Optional<String> getResultSet();

  @JsonIgnore
  @Value.Lazy
  default Map<String, ResultSetDefinition> getAllResultSets() {
    if (getResultSet().isEmpty()) {
      return getResultSets();
    }
    Map<String, ResultSetDefinition> resultSets = new LinkedHashMap<>();
    resultSets.put(getResultSet().get(), new ImmutableResultSetDefinition.Builder().build());
    resultSets.putAll(getResultSets());
    return resultSets;
  }

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
        getResultSet().isEmpty() || !getResultSets().containsKey(getResultSet().get()),
        "The name of a result set must be unique. Found '%s' in both 'resultSet' and 'resultSets'.",
        getResultSet().orElse(""));
  }
}
