/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import de.ii.xtraplatform.cql.domain.CqlVisitorExtractParameters;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaRef;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ParametersInStoredQuery implements StoredQueryVisitor<Map<String, JsonSchema>> {

  private final Map<String, JsonSchema> globalParameters;

  public ParametersInStoredQuery(Map<String, JsonSchema> globalParameters) {
    this.globalParameters = globalParameters;
  }

  public Map<String, JsonSchema> visit(StoredQueryExpression storedQuery) {
    if (Objects.isNull(storedQuery)) {
      return Map.of();
    }

    Map<String, JsonSchema> parameters = new HashMap<>();

    storedQuery.getCollections().forEach(v -> v.accept(this).forEach(parameters::putIfAbsent));
    storedQuery.getCrs().ifPresent(v -> v.accept(this).forEach(parameters::putIfAbsent));
    storedQuery.getVerticalCrs().ifPresent(v -> v.accept(this).forEach(parameters::putIfAbsent));
    storedQuery.getFilterCrs().ifPresent(v -> v.accept(this).forEach(parameters::putIfAbsent));
    storedQuery.getFilterOperator().ifPresent(v -> v.accept(this).forEach(parameters::putIfAbsent));
    storedQuery.getLimit().ifPresent(v -> v.accept(this).forEach(parameters::putIfAbsent));
    storedQuery
        .getMaxAllowableOffset()
        .ifPresent(v -> v.accept(this).forEach(parameters::putIfAbsent));

    storedQuery.getProfiles().ifPresent(v -> v.accept(this).forEach(parameters::putIfAbsent));
    storedQuery.getProperties().ifPresent(v -> v.accept(this).forEach(parameters::putIfAbsent));
    storedQuery.getSortby().ifPresent(v -> v.accept(this).forEach(parameters::putIfAbsent));

    storedQuery
        .getFilter()
        .ifPresent(
            v ->
                v.accept(new CqlVisitorExtractParameters(globalParameters), true)
                    .forEach(parameters::putIfAbsent));

    storedQuery
        .getQueries()
        .forEach(
            query -> {
              query.accept(this).forEach(parameters::putIfAbsent);
            });

    return Map.copyOf(parameters);
  }

  public Map<String, JsonSchema> visit(SingleQueryWithParameters query) {
    Map<String, JsonSchema> parameters = new HashMap<>();
    query.getCollections().forEach(v -> v.accept(this).forEach(parameters::putIfAbsent));
    query.getProperties().ifPresent(v -> v.accept(this).forEach(parameters::putIfAbsent));
    query.getSortby().ifPresent(v -> v.accept(this).forEach(parameters::putIfAbsent));
    query
        .getFilter()
        .ifPresent(
            v ->
                v.accept(new CqlVisitorExtractParameters(globalParameters), true)
                    .forEach(parameters::putIfAbsent));
    return Map.copyOf(parameters);
  }

  @Override
  public Map<String, JsonSchema> visit(ParameterValue param) {
    if (param.getSchema() instanceof JsonSchemaRef) {
      String ref = ((JsonSchemaRef) param.getSchema()).getRef();
      String name = ref.substring(ref.lastIndexOf('/') + 1);
      if (globalParameters.containsKey(name)) {
        return Map.of(param.getName(), globalParameters.get(name));
      }
    }
    return Map.of(param.getName(), param.getSchema());
  }

  public Map<String, JsonSchema> visit(StringOrParameter value) {
    return value.getParameter().map(param -> param.accept(this)).orElse(Map.of());
  }

  public Map<String, JsonSchema> visit(IntegerOrParameter value) {
    return value.getParameter().map(param -> param.accept(this)).orElse(Map.of());
  }

  public Map<String, JsonSchema> visit(DoubleOrParameter value) {
    return value.getParameter().map(param -> param.accept(this)).orElse(Map.of());
  }

  public Map<String, JsonSchema> visit(FilterOperatorOrParameter value) {
    return value.getParameter().map(param -> param.accept(this)).orElse(Map.of());
  }

  public Map<String, JsonSchema> visit(ParameterOrListOfStringOrParameter value) {
    return value
        .getParameter()
        .map(param -> param.accept(this))
        .or(
            () ->
                value
                    .getValue()
                    .map(
                        list -> {
                          Map<String, JsonSchema> parameters = new HashMap<>();
                          list.forEach(v -> v.accept(this).forEach(parameters::putIfAbsent));
                          return Map.copyOf(parameters);
                        }))
        .orElse(Map.of());
  }
}
