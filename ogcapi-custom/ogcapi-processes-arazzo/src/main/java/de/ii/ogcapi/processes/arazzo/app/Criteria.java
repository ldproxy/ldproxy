/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import de.ii.ogcapi.processes.arazzo.domain.ArazzoCriterion;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** The criteria of steps and actions, of the types {@code simple} and {@code regex}. */
final class Criteria {

  private Criteria() {}

  /** The type of a criterion; without a type, a criterion is {@code simple}. */
  static String type(ArazzoCriterion criterion) {
    return Objects.isNull(criterion.getType())
        ? ArazzoCriterion.SIMPLE
        : typeName(criterion.getType());
  }

  /** The name of a type, a string or an expression type object with {@code type}. */
  static String typeName(Object type) {
    return String.valueOf(
        type instanceof Map<?, ?> expressionType ? expressionType.get("type") : type);
  }

  /**
   * @return true, if all criteria pass; an error while evaluating a criterion fails it
   */
  static boolean all(List<ArazzoCriterion> criteria, ExpressionContext context) {
    return criteria.stream().allMatch(criterion -> test(criterion, context));
  }

  static boolean test(ArazzoCriterion criterion, ExpressionContext context) {
    try {
      if (ArazzoCriterion.REGEX.equals(type(criterion))) {
        Object value =
            criterion
                .getContext()
                .map(expression -> RuntimeExpressions.evaluate(expression, context))
                .orElse(null);
        if (value == null) {
          return false;
        }
        String condition = criterion.getCondition();
        if (condition.contains("{$")) {
          condition = RuntimeExpressions.interpolate(condition, context);
        }
        return Pattern.compile(condition).matcher(RuntimeExpressions.stringify(value)).find();
      }
      return SimpleCondition.parse(criterion.getCondition())
          .test(expression -> RuntimeExpressions.evaluate(expression, context));
    } catch (IllegalArgumentException e) {
      // an invalid expression, condition or regex fails the criterion
      return false;
    }
  }
}
