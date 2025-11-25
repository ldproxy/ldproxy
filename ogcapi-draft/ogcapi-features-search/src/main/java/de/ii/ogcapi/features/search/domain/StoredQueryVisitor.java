/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

public interface StoredQueryVisitor<T> {

  default T visit(StoredQueryComponent component) {
    if (component instanceof StoredQueryExpression) {
      return visit((StoredQueryExpression) component);
    } else if (component instanceof StoredQueryBase) {
      return visit((StoredQueryBase) component);
    } else if (component instanceof SingleQueryWithParameters) {
      return visit((SingleQueryWithParameters) component);
    } else if (component instanceof ParameterValue) {
      return visit((ParameterValue) component);
    } else if (component instanceof StringOrParameter) {
      return visit((StringOrParameter) component);
    } else if (component instanceof IntegerOrParameter) {
      return visit((IntegerOrParameter) component);
    } else if (component instanceof DoubleOrParameter) {
      return visit((DoubleOrParameter) component);
    } else if (component instanceof FilterOperatorOrParameter) {
      return visit((FilterOperatorOrParameter) component);
    } else if (component instanceof ParameterOrListOfStringOrParameter) {
      return visit((ParameterOrListOfStringOrParameter) component);
    } else {
      throw new IllegalArgumentException(
          "Unknown stored query component: " + component.getClass().getSimpleName());
    }
  }

  default T visit(StoredQueryExpression storedQuery) {
    return visit((StoredQueryBase) storedQuery);
  }

  T visit(StoredQueryBase storedQuery);

  T visit(SingleQueryWithParameters query);

  T visit(ParameterValue param);

  T visit(StringOrParameter value);

  T visit(IntegerOrParameter value);

  T visit(DoubleOrParameter value);

  T visit(FilterOperatorOrParameter op);

  T visit(ParameterOrListOfStringOrParameter list);
}
