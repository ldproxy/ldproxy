/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.resources.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true, deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableSingleQuery.Builder.class)
public interface SingleQuery {

  @JsonIgnore String SCHEMA_REF = "#/components/schemas/Query";

  List<String> getCollections(); // String or Parameter

  Optional<Object> getFilter();

  List<String> getSortby(); // String or Parameter

  List<String> getProperties(); // String or Parameter

  @JsonIgnore
  @Value.Check
  default void check() {
    Preconditions.checkState(
        !getCollections().isEmpty(), "Each query must have a collection. Found no collection.");
    Preconditions.checkState(
        getCollections().size() < 2,
        "Each query must be for a single collection. Join queries are currently not supported. Found collections: %s.",
        getCollections());
  }
}
