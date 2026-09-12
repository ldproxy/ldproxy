/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import de.ii.xtraplatform.cql.domain.BinaryScalarOperation;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.cql.domain.CqlNode;
import de.ii.xtraplatform.cql.domain.CqlVisitorCopy;
import de.ii.xtraplatform.cql.domain.ImmutableInResultSet;
import de.ii.xtraplatform.cql.domain.ImmutableInResultSetByKey;
import de.ii.xtraplatform.cql.domain.InResultSet;
import de.ii.xtraplatform.cql.domain.InResultSetByKey;
import de.ii.xtraplatform.cql.domain.Scalar;
import jakarta.ws.rs.BadRequestException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves result-set references in a query filter. Each {@code inResultSet} and {@code
 * inResultSetByKey} predicate is replaced by a copy that carries the feature type and the effective
 * filter of the query that defines the result set, so that the feature provider can evaluate the
 * predicate without knowledge of the query expression.
 */
public class ResultSetResolver extends CqlVisitorCopy {

  public static class ResolvedResultSet {
    private final String type;
    private final Optional<Cql2Expression> filter;
    private final Optional<String> values;
    private final Map<String, String> key;

    public ResolvedResultSet(
        String type,
        Optional<Cql2Expression> filter,
        Optional<String> values,
        Map<String, String> key) {
      this.type = type;
      this.filter = filter;
      this.values = values;
      this.key = key;
    }
  }

  private final Map<String, ResolvedResultSet> resultSets;

  public ResultSetResolver(Map<String, ResolvedResultSet> resultSets) {
    this.resultSets = resultSets;
  }

  @Override
  public CqlNode visit(BinaryScalarOperation scalarOperation, List<CqlNode> children) {
    if (scalarOperation instanceof InResultSetByKey) {
      InResultSetByKey inResultSet = (InResultSetByKey) scalarOperation;
      ResolvedResultSet resultSet = resultSets.get(inResultSet.getSetName());

      if (Objects.isNull(resultSet)) {
        throw new BadRequestException(
            String.format(
                "The filter references the result set '%s', but no such result set is defined by an earlier query.",
                inResultSet.getSetName()));
      }

      if (resultSet.key.isEmpty()) {
        throw new BadRequestException(
            String.format(
                "The filter consumes the result set '%s' with '%s', but that result set is a set of single values. Use '%s' instead.",
                inResultSet.getSetName(), InResultSetByKey.TYPE, InResultSet.TYPE));
      }

      Set<String> defined = new LinkedHashSet<>(resultSet.key.keySet());
      Set<String> used = new LinkedHashSet<>(inResultSet.getKeyNames());

      if (!defined.equals(used)) {
        throw new BadRequestException(
            String.format(
                "The filter consumes the result set '%s' with the key parts %s, but that result set defines the key parts %s.",
                inResultSet.getSetName(), used, defined));
      }

      return new ImmutableInResultSetByKey.Builder()
          .from(inResultSet)
          .args(
              children.stream()
                  .filter(child -> child instanceof Scalar)
                  .map(child -> (Scalar) child)
                  .toList())
          .producerType(resultSet.type)
          .producerFilter(resultSet.filter)
          .producerKey(resultSet.key)
          .build();
    }

    if (scalarOperation instanceof InResultSet) {
      InResultSet inResultSet = (InResultSet) scalarOperation;
      ResolvedResultSet resultSet = resultSets.get(inResultSet.getSetName());

      if (Objects.isNull(resultSet)) {
        throw new BadRequestException(
            String.format(
                "The filter references the result set '%s', but no such result set is defined by an earlier query.",
                inResultSet.getSetName()));
      }

      if (!resultSet.key.isEmpty()) {
        throw new BadRequestException(
            String.format(
                "The filter consumes the result set '%s' with '%s', but that result set is a set of composite keys. Use '%s' instead.",
                inResultSet.getSetName(), InResultSet.TYPE, InResultSetByKey.TYPE));
      }

      return new ImmutableInResultSet.Builder()
          .from(inResultSet)
          .args(
              children.stream()
                  .filter(child -> child instanceof Scalar)
                  .map(child -> (Scalar) child)
                  .toList())
          .producerType(resultSet.type)
          .producerFilter(resultSet.filter)
          .producerValues(resultSet.values)
          .build();
    }

    return super.visit(scalarOperation, children);
  }
}
