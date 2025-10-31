/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import de.ii.ogcapi.styles.domain.ArrayValue;
import de.ii.ogcapi.styles.domain.ImmutableArrayValue;
import de.ii.ogcapi.styles.domain.ImmutableNumberValue;
import de.ii.ogcapi.styles.domain.ImmutableObjectValue;
import de.ii.ogcapi.styles.domain.MbStyleExpression;
import de.ii.ogcapi.styles.domain.MbStyleExpressionVisitor;
import de.ii.ogcapi.styles.domain.NumberValue;
import de.ii.ogcapi.styles.domain.ObjectValue;
import de.ii.ogcapi.styles.domain.StringValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

public class AdjustZoomLevels implements MbStyleExpressionVisitor<MbStyleExpression> {

  private final Function<Double, Double> adjustZoomLevel;

  public AdjustZoomLevels(Function<Double, Double> adjustZoomLevel) {
    this.adjustZoomLevel = adjustZoomLevel;
  }

  @Override
  public MbStyleExpression visit(MbStyleExpression expression) {
    if (expression instanceof ArrayValue arrayValue) {
      if (arrayValue.getValue().isEmpty()) {
        return expression;
      }
      MbStyleExpression op = arrayValue.getValue().get(0);
      if (op instanceof StringValue opStringValue) {
        String opString = opStringValue.getValue();
        return switch (opString) {
          case "step" -> ImmutableArrayValue.of(
              process(arrayValue.getValue(), opString, 1, i -> i > 2 && i % 2 == 1));
          case "interpolate", "interpolate-hcl", "interpolate-lab" -> ImmutableArrayValue.of(
              process(arrayValue.getValue(), opString, 2, i -> i > 2 && i % 2 == 1));
          default -> ImmutableArrayValue.of(
              arrayValue.getValue().stream().map(exp -> exp.accept(this)).toList());
        };
      }
    } else if (expression instanceof ObjectValue objectValue) {
      if (objectValue.getValue().isEmpty()) {
        return expression;
      }
      Map<String, MbStyleExpression> newMap = new HashMap<>();
      for (Entry<String, MbStyleExpression> entry : objectValue.getValue().entrySet()) {
        MbStyleExpression value = entry.getValue();
        if (entry.getKey().equals("stops")) {
          if (value instanceof ArrayValue stopsArray) {
            Builder<ArrayValue> newStopsBuilder = ImmutableList.builder();
            for (MbStyleExpression stopValue : stopsArray.getValue()) {
              if (!(stopValue instanceof ArrayValue stop)
                  || stop.getValue().size() != 2
                  || !(stop.getValue().get(0) instanceof NumberValue)) {
                throw new IllegalArgumentException(
                    "Expected an array for 'stops' with two items where the first item is a zoom level, but got: "
                        + stopValue);
              }
              newStopsBuilder.add(
                  ImmutableArrayValue.of(
                      List.of(
                          ImmutableNumberValue.of(
                              adjustZoomLevel.apply(
                                  ((NumberValue) stop.getValue().get(0)).getValue().doubleValue())),
                          stop.getValue().get(1).accept(this))));
            }
            newMap.put(entry.getKey(), ImmutableArrayValue.of(newStopsBuilder.build()));
          } else {
            throw new IllegalArgumentException("Expected an array for 'stops', but got: " + value);
          }
        } else {
          newMap.put(entry.getKey(), entry.getValue().accept(this));
        }
      }
      return ImmutableObjectValue.of(newMap);
    }

    return expression;
  }

  private List<MbStyleExpression> process(
      List<MbStyleExpression> expression,
      String function,
      int inputIndex,
      IntPredicate indexPredicate) {
    if (expression.size() <= inputIndex) {
      throw new IllegalArgumentException(
          String.format(
              "A '%s' expression in a MapLibre style expression requires at least %d arguments, found %d.",
              function, inputIndex + 1, expression.size()));
    }
    MbStyleExpression input = expression.get(inputIndex);
    if (input instanceof ArrayValue inputArray) {
      if (inputArray.getValue().get(0) instanceof StringValue inputString
          && inputString.getValue().equals("zoom")) {
        // Adjust zoom levels in the expression
        // Keep the other arguments unchanged
        return IntStream.range(0, expression.size())
            .mapToObj(
                i -> {
                  if (indexPredicate.test(i)) {
                    // Adjust the zoom level for every input step
                    MbStyleExpression step = expression.get(i);
                    if (step instanceof NumberValue numberStep) {
                      return ImmutableNumberValue.of(
                          adjustZoomLevel.apply(numberStep.getValue().doubleValue()));
                    } else {
                      throw new IllegalArgumentException(
                          "Expected a number for zoom adjustment, but got: " + step);
                    }
                  } else {
                    // Keep the other arguments unchanged
                    return expression.get(i).accept(this);
                  }
                })
            .toList();
      } else {
        return inputArray.getValue().stream().map(exp -> exp.accept(this)).toList();
      }
    } else {
      throw new IllegalArgumentException(
          String.format("Expected an array for '%s' input, but got: %s", function, input));
    }
  }
}
