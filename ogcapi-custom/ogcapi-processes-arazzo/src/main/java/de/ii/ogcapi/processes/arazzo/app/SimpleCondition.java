/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * A condition of the type {@code simple}: literals ({@code true}, {@code false}, {@code null},
 * numbers, strings in single quotes with {@code ''} as escape), runtime expressions, the operators
 * {@code == != < <= > >=}, {@code !}, {@code &&}, {@code ||}, grouping with {@code ()}, index
 * {@code []} and member {@code .}. The specification has no precedence, the usual one applies, from
 * high to low: member and index, {@code !}, comparisons, {@code &&}, {@code ||}.
 *
 * <p>String comparisons are case-insensitive, numeric strings are compared as numbers with a
 * number, {@code null} only equals {@code null}, and an ordering comparison with {@code null} is
 * false. A condition passes, if its value is neither false nor null.
 */
final class SimpleCondition {

  private final Node root;

  private SimpleCondition(Node root) {
    this.root = root;
  }

  /**
   * @throws IllegalArgumentException if the condition is not a simple condition
   */
  static SimpleCondition parse(String condition) {
    Parser parser = new Parser(tokenize(condition), condition);
    Node root = parser.or();
    if (!parser.atEnd()) {
      throw parser.error();
    }
    return new SimpleCondition(root);
  }

  /**
   * @param expressions the values of the runtime expressions
   * @return true, if the condition passes
   */
  boolean test(Function<String, Object> expressions) {
    return isTruthy(root.evaluate(expressions));
  }

  private static boolean isTruthy(Object value) {
    return value != null && !Boolean.FALSE.equals(value);
  }

  // --- tokens

  private enum Kind {
    LITERAL,
    EXPRESSION,
    NAME,
    OPERATOR
  }

  private record Token(Kind kind, String text, Object value) {}

  private static List<Token> tokenize(String condition) {
    List<Token> tokens = new ArrayList<>();
    int i = 0;
    while (i < condition.length()) {
      char c = condition.charAt(i);
      if (Character.isWhitespace(c)) {
        i++;
      } else if (c == '$') {
        i = expression(condition, i, tokens);
      } else if (c == '\'') {
        i = string(condition, i, tokens);
      } else if (isNumberStart(condition, i)) {
        i = number(condition, i, tokens);
      } else if (Character.isLetter(c) || c == '_') {
        i = name(condition, i, tokens);
      } else {
        String operator = operatorAt(condition, i);
        tokens.add(new Token(Kind.OPERATOR, operator, null));
        i += operator.length();
      }
    }
    return tokens;
  }

  // the token starts at the position, the result is the position after it
  private static int expression(String condition, int start, List<Token> tokens) {
    int end = start + 1;
    while (end < condition.length() && isExpressionChar(condition.charAt(end))) {
      end++;
    }
    tokens.add(new Token(Kind.EXPRESSION, condition.substring(start, end), null));
    return end;
  }

  // a string in single quotes, a quote in the string is written twice
  private static int string(String condition, int start, List<Token> tokens) {
    StringBuilder value = new StringBuilder();
    int end = start + 1;
    while (end < condition.length()) {
      char next = condition.charAt(end);
      if (next == '\'') {
        if (end + 1 >= condition.length() || condition.charAt(end + 1) != '\'') {
          tokens.add(
              new Token(Kind.LITERAL, condition.substring(start, end + 1), value.toString()));
          return end + 1;
        }
        // an escaped quote
        end++;
      }
      value.append(next);
      end++;
    }
    throw new IllegalArgumentException(
        String.format("The condition '%s' has an unterminated string.", condition));
  }

  private static boolean isNumberStart(String condition, int i) {
    char c = condition.charAt(i);
    return Character.isDigit(c)
        || c == '-' && i + 1 < condition.length() && Character.isDigit(condition.charAt(i + 1));
  }

  private static int number(String condition, int start, List<Token> tokens) {
    int end = start + 1;
    while (end < condition.length()
        && (Character.isDigit(condition.charAt(end))
            || "eE.+-".indexOf(condition.charAt(end)) >= 0)) {
      end++;
    }
    String text = condition.substring(start, end);
    try {
      tokens.add(new Token(Kind.LITERAL, text, new BigDecimal(text)));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          String.format("The condition '%s' has an invalid number '%s'.", condition, text), e);
    }
    return end;
  }

  // true, false, null, or the name of a member
  private static int name(String condition, int start, List<Token> tokens) {
    int end = start + 1;
    while (end < condition.length()
        && (Character.isLetterOrDigit(condition.charAt(end))
            || "_-".indexOf(condition.charAt(end)) >= 0)) {
      end++;
    }
    String text = condition.substring(start, end);
    switch (text) {
      case "true" -> tokens.add(new Token(Kind.LITERAL, text, Boolean.TRUE));
      case "false" -> tokens.add(new Token(Kind.LITERAL, text, Boolean.FALSE));
      case "null" -> tokens.add(new Token(Kind.LITERAL, text, null));
      default -> tokens.add(new Token(Kind.NAME, text, null));
    }
    return end;
  }

  private static boolean isExpressionChar(char c) {
    return Character.isLetterOrDigit(c) || "._-#/~$".indexOf(c) >= 0;
  }

  private static String operatorAt(String condition, int i) {
    for (String operator : List.of("==", "!=", "<=", ">=", "&&", "||", "<", ">", "!", "(", ")")) {
      if (condition.startsWith(operator, i)) {
        return operator;
      }
    }
    for (String operator : List.of("[", "]", ".")) {
      if (condition.startsWith(operator, i)) {
        return operator;
      }
    }
    throw new IllegalArgumentException(
        String.format(
            "The condition '%s' has an unexpected character '%c'.",
            condition, condition.charAt(i)));
  }

  // --- parser

  private static final class Parser {
    private final List<Token> tokens;
    private final String condition;
    private int position;

    Parser(List<Token> tokens, String condition) {
      this.tokens = tokens;
      this.condition = condition;
    }

    boolean atEnd() {
      return position >= tokens.size();
    }

    IllegalArgumentException error() {
      return new IllegalArgumentException(
          atEnd()
              ? String.format("The condition '%s' ends unexpectedly.", condition)
              : String.format(
                  "The condition '%s' has an unexpected '%s'.",
                  condition, tokens.get(position).text()));
    }

    private boolean accept(String operator) {
      if (!atEnd()
          && tokens.get(position).kind() == Kind.OPERATOR
          && tokens.get(position).text().equals(operator)) {
        position++;
        return true;
      }
      return false;
    }

    private void expect(String operator) {
      if (!accept(operator)) {
        throw error();
      }
    }

    Node or() {
      Node left = and();
      while (accept("||")) {
        Node l = left;
        Node r = and();
        left = values -> isTruthy(l.evaluate(values)) || isTruthy(r.evaluate(values));
      }
      return left;
    }

    private Node and() {
      Node left = comparison();
      while (accept("&&")) {
        Node l = left;
        Node r = comparison();
        left = values -> isTruthy(l.evaluate(values)) && isTruthy(r.evaluate(values));
      }
      return left;
    }

    private Node comparison() {
      Node left = unary();
      for (String operator : List.of("==", "!=", "<=", ">=", "<", ">")) {
        if (accept(operator)) {
          Node right = unary();
          return values -> compare(operator, left.evaluate(values), right.evaluate(values));
        }
      }
      return left;
    }

    private Node unary() {
      if (accept("!")) {
        Node operand = unary();
        return values -> !isTruthy(operand.evaluate(values));
      }
      return postfix();
    }

    private Node postfix() {
      Node node = primary();
      while (true) {
        if (accept(".")) {
          node = member(node);
        } else if (accept("[")) {
          node = index(node);
        } else {
          return node;
        }
      }
    }

    private Node member(Node object) {
      if (atEnd() || tokens.get(position).kind() != Kind.NAME) {
        throw error();
      }
      String name = tokens.get(position).text();
      position++;
      return values -> object.evaluate(values) instanceof Map<?, ?> map ? map.get(name) : null;
    }

    private Node index(Node array) {
      if (atEnd() || !(tokens.get(position).value() instanceof BigDecimal number)) {
        throw error();
      }
      position++;
      expect("]");
      int index = number.intValue();
      return values ->
          array.evaluate(values) instanceof List<?> list && index >= 0 && index < list.size()
              ? list.get(index)
              : null;
    }

    private Node primary() {
      if (atEnd()) {
        throw error();
      }
      if (accept("(")) {
        Node inner = or();
        expect(")");
        return inner;
      }
      Token token = tokens.get(position);
      if (token.kind() == Kind.LITERAL) {
        position++;
        Object value = token.value();
        return values -> value;
      }
      if (token.kind() == Kind.EXPRESSION) {
        position++;
        String expression = token.text();
        return values -> values.apply(expression);
      }
      throw error();
    }
  }

  @FunctionalInterface
  private interface Node {
    Object evaluate(Function<String, Object> expressions);
  }

  // --- comparisons

  static boolean compare(String operator, Object left, Object right) {
    if (left == null || right == null) {
      return switch (operator) {
        case "==" -> left == null && right == null;
        case "!=" -> left != null || right != null;
        default -> false;
      };
    }
    BigDecimal leftNumber = number(left, right);
    BigDecimal rightNumber = number(right, left);
    if (leftNumber != null && rightNumber != null) {
      return order(operator, leftNumber.compareTo(rightNumber));
    }
    if (left instanceof String l && right instanceof String r) {
      return order(operator, l.compareToIgnoreCase(r));
    }
    boolean equal = Objects.equals(left, right);
    return switch (operator) {
      case "==" -> equal;
      case "!=" -> !equal;
      default -> false;
    };
  }

  // a number, or a numeric string compared with a number
  private static BigDecimal number(Object value, Object other) {
    if (value instanceof Number) {
      return new BigDecimal(value.toString());
    }
    if (value instanceof String string && other instanceof Number) {
      try {
        return new BigDecimal(string.trim());
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  private static boolean order(String operator, int comparison) {
    return switch (operator) {
      case "==" -> comparison == 0;
      case "!=" -> comparison != 0;
      case "<" -> comparison < 0;
      case "<=" -> comparison <= 0;
      case ">" -> comparison > 0;
      case ">=" -> comparison >= 0;
      default -> throw new IllegalStateException(operator);
    };
  }
}
