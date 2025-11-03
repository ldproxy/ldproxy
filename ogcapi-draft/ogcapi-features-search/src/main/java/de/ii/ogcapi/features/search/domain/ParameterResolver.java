/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ParameterResolver implements ParameterResolverBase {

  private final QueryParameterSet queryParameterSet;
  private final SchemaValidator schemaValidator;

  public ParameterResolver(QueryParameterSet queryParameterSet, SchemaValidator schemaValidator) {
    this.queryParameterSet = queryParameterSet;
    this.schemaValidator = schemaValidator;
  }

  public QueryExpression visit(StoredQueryExpression storedQuery) {
    if (Objects.isNull(storedQuery)) {
      return null;
    }

    final Map<String, JsonSchema> parameters = storedQuery.getParameters();

    ImmutableQueryExpression.Builder builder =
        new ImmutableQueryExpression.Builder()
            .id(storedQuery.getId())
            .title(storedQuery.getTitle())
            .description(storedQuery.getDescription())
            .offset(storedQuery.getOffset());

    storedQuery
        .getCollections()
        .forEach(v -> builder.addCollections(resolveParameter(v, parameters)));
    storedQuery.getCrs().ifPresent(v -> builder.crs(resolveParameter(v, parameters)));
    storedQuery
        .getVerticalCrs()
        .ifPresent(v -> builder.verticalCrs(resolveParameter(v, parameters)));
    storedQuery.getFilterCrs().ifPresent(v -> builder.filterCrs(resolveParameter(v, parameters)));
    storedQuery
        .getFilterOperator()
        .ifPresent(v -> builder.filterOperator(resolveParameter(v, parameters)));
    storedQuery.getLimit().ifPresent(v -> builder.limit(resolveParameter(v, parameters)));
    storedQuery
        .getMaxAllowableOffset()
        .ifPresent(v -> builder.maxAllowableOffset(resolveParameter(v, parameters)));
    storedQuery.getProfiles().ifPresent(v -> builder.profiles(resolveParameters(v, parameters)));
    storedQuery
        .getProperties()
        .ifPresent(v -> builder.properties(resolveParameters(v, parameters)));
    storedQuery.getSortby().ifPresent(v -> builder.sortby(resolveParameters(v, parameters)));

    ParameterResolverCql cqlParameterResolver =
        new ParameterResolverCql(queryParameterSet, storedQuery.getParameters(), schemaValidator);
    storedQuery
        .getFilter()
        .ifPresent(v -> builder.filter((Cql2Expression) v.accept(cqlParameterResolver)));

    storedQuery
        .getQueries()
        .forEach(
            query -> {
              ImmutableSingleQuery.Builder builder2 = new ImmutableSingleQuery.Builder();

              query
                  .getCollections()
                  .forEach(v -> builder2.addCollections(resolveParameter(v, parameters)));
              query
                  .getProperties()
                  .ifPresent(v -> builder2.properties(resolveParameters(v, parameters)));
              query.getSortby().ifPresent(v -> builder2.sortby(resolveParameters(v, parameters)));
              query
                  .getFilter()
                  .ifPresent(v -> builder2.filter((Cql2Expression) v.accept(cqlParameterResolver)));

              builder.addQueries(builder2.build());
            });

    return builder.build();
  }

  private String resolveParameter(StringOrParameter value, Map<String, JsonSchema> parameters) {
    return value
        .getValue()
        .or(() -> value.getParameter().map(param -> (String) resolveParameter(param, parameters)))
        .orElseThrow();
  }

  private FilterOperator resolveParameter(
      FilterOperatorOrParameter value, Map<String, JsonSchema> parameters) {
    return value
        .getValue()
        .or(
            () ->
                value
                    .getParameter()
                    .map(param -> (FilterOperator) resolveParameter(param, parameters)))
        .orElseThrow();
  }

  private int resolveParameter(IntegerOrParameter value, Map<String, JsonSchema> parameters) {
    return value
        .getValue()
        .or(() -> value.getParameter().map(param -> (Integer) resolveParameter(param, parameters)))
        .orElseThrow();
  }

  private double resolveParameter(DoubleOrParameter value, Map<String, JsonSchema> parameters) {
    return value
        .getValue()
        .or(() -> value.getParameter().map(param -> (Double) resolveParameter(param, parameters)))
        .orElseThrow();
  }

  private List<String> resolveParameters(
      ParameterOrListOfStringOrParameter value, Map<String, JsonSchema> parameters) {
    return value
        .getValue()
        .map(list -> list.stream().map(v -> resolveParameter(v, parameters)).toList())
        .or(
            () ->
                value
                    .getParameter()
                    .map(param -> (List<String>) resolveParameter(param, parameters)))
        .orElseThrow();
  }

  private Object resolveParameter(ParameterValue param, Map<String, JsonSchema> parameters) {
    return resolveParameter(param.getName(), param.getSchema(), queryParameterSet, schemaValidator);
  }
}
