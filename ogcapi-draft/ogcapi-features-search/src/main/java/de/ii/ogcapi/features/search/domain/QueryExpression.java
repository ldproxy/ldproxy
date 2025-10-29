/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.base.Preconditions;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true, deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableQueryExpression.Builder.class)
public interface QueryExpression {

  String ADHOC_QUERY_ID = "_adhoc_query_";

  ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new GuavaModule())
          .configure(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, true)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  String SCHEMA_REF = "#/components/schemas/QueryExpression";

  abstract class Builder {}

  static QueryExpression of(InputStream requestBody) throws IOException {
    return MAPPER.readValue(requestBody, QueryExpression.class);
  }

  @Value.Default
  default String getId() {
    return ADHOC_QUERY_ID;
  }

  Optional<String> getTitle();

  Optional<String> getDescription();

  List<SingleQuery> getQueries();

  List<String> getCollections();

  Optional<Cql2Expression> getFilter();

  Optional<String> getFilterCrs();

  Optional<FilterOperator> getFilterOperator();

  List<String> getSortby();

  List<String> getProperties();

  Optional<String> getCrs();

  Optional<String> getVerticalCrs();

  Optional<Double> getMaxAllowableOffset();

  Optional<Integer> getLimit();

  Optional<Integer> getOffset();

  List<String> getProfiles();

  @JsonIgnore
  @Value.Check
  default void check() {
    Preconditions.checkState(
        getQueries().isEmpty() && getCollections().size() == 1
            || !getQueries().isEmpty() && getCollections().isEmpty(),
        "Either one or more queries must be provided or a single collection. Queries: %s. Collections: %s.",
        getQueries(),
        getCollections());
  }
}
