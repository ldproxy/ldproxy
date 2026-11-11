/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ii.ogcapi.processes.arazzo.app.ExpressionContext.Exchange;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The runtime expressions of Arazzo: {@code $url}, {@code $method}, {@code $statusCode}, {@code
 * $request.(header|query|path).<name>}, {@code $request.body}, {@code $response.header.<name>},
 * {@code $response.body}, {@code $inputs.<name>}, {@code $outputs.<name>}, {@code
 * $steps.<stepId>.outputs.<name>}, {@code $workflows.<workflowId>.(inputs|outputs).<name>} and
 * {@code $sourceDescriptions.<name>.url}, each optionally with a JSON Pointer ({@code #/...}).
 * After a body or a named value, further names separated by dots select members, as in the
 * conditions of the specification, e.g. {@code $response.body.numberReturned}.
 */
final class RuntimeExpressions {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String HEADER = "header.";
  private static final String BODY = "body";
  private static final String URL = ".url";

  private RuntimeExpressions() {}

  static boolean isExpression(Object value) {
    return value instanceof String string && string.startsWith("$");
  }

  /**
   * A value of a parameter, a payload or an output: a runtime expression, a string with embedded
   * expressions ({@code {$...}}), or a literal. Objects and arrays are resolved member by member,
   * the result is mutable.
   */
  static Object resolve(Object value, ExpressionContext context) {
    if (value instanceof String string) {
      if (string.startsWith("$")) {
        return evaluate(string, context);
      }
      return string.contains("{$") ? interpolate(string, context) : string;
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> resolved = new LinkedHashMap<>();
      map.forEach((key, member) -> resolved.put(String.valueOf(key), resolve(member, context)));
      return resolved;
    }
    if (value instanceof List<?> list) {
      List<Object> resolved = new ArrayList<>();
      list.forEach(item -> resolved.add(resolve(item, context)));
      return resolved;
    }
    return value;
  }

  /** A string with embedded expressions; scalars are inserted as strings, objects as JSON. */
  static String interpolate(String template, ExpressionContext context) {
    StringBuilder result = new StringBuilder();
    int position = 0;
    int start = template.indexOf("{$");
    while (start >= 0) {
      int end = template.indexOf('}', start);
      if (end < 0) {
        break;
      }
      result
          .append(template, position, start)
          .append(stringify(evaluate(template.substring(start + 1, end), context)));
      position = end + 1;
      start = template.indexOf("{$", position);
    }
    result.append(template.substring(position));
    return result.toString();
  }

  static String stringify(Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof Double || value instanceof Float || value instanceof BigDecimal) {
      BigDecimal number = new BigDecimal(value.toString());
      return number.stripTrailingZeros().scale() <= 0
          ? number.toBigInteger().toString()
          : number.stripTrailingZeros().toPlainString();
    }
    if (value instanceof Map<?, ?> || value instanceof List<?>) {
      try {
        return MAPPER.writeValueAsString(value);
      } catch (JsonProcessingException e) {
        throw new IllegalStateException(e);
      }
    }
    return value.toString();
  }

  /**
   * @return the value of the expression, null if there is none
   * @throws IllegalArgumentException if the expression is not supported
   */
  static Object evaluate(String expression, ExpressionContext context) {
    int hash = expression.indexOf('#');
    if (hash < 0) {
      return evaluateWithoutPointer(expression, context);
    }
    Object value = evaluateWithoutPointer(expression.substring(0, hash), context);
    return JsonPointers.get(value, expression.substring(hash + 1));
  }

  // a dispatch over the sources of runtime expressions
  private static Object evaluateWithoutPointer(String expression, ExpressionContext context) {
    Optional<Exchange> exchange = context.exchange();
    switch (expression) {
      case "$url":
        return exchange.map(e -> e.request().url()).orElse(null);
      case "$method":
        return exchange.map(e -> e.request().method()).orElse(null);
      case "$statusCode":
        return exchange.map(e -> e.response().statusCode()).orElse(null);
      case "$inputs":
        return context.inputs();
      case "$outputs":
        return context.outputs();
      default:
        break;
    }
    if (expression.startsWith("$request.")) {
      return exchange.map(e -> request(e, after(expression, "$request."))).orElse(null);
    }
    if (expression.startsWith("$response.")) {
      return exchange.map(e -> response(e, after(expression, "$response."))).orElse(null);
    }
    if (expression.startsWith("$inputs.")) {
      return named(context.inputs(), after(expression, "$inputs."));
    }
    if (expression.startsWith("$outputs.")) {
      return named(context.outputs(), after(expression, "$outputs."));
    }
    if (expression.startsWith("$steps.")) {
      return idThenMember(context.steps(), after(expression, "$steps."), "outputs", expression);
    }
    if (expression.startsWith("$workflows.")) {
      return workflow(context, expression);
    }
    if (expression.startsWith("$sourceDescriptions.")) {
      return source(context, expression);
    }
    throw unsupported(expression);
  }

  // $workflows.<workflowId>.(inputs|outputs).<name>
  private static Object workflow(ExpressionContext context, String expression) {
    String rest = after(expression, "$workflows.");
    int dot = rest.indexOf('.');
    String member = dot < 0 ? "" : rest.substring(dot + 1);
    if (!member.startsWith("inputs") && !member.startsWith("outputs")) {
      throw unsupported(expression);
    }
    Map<String, Object> workflow = context.workflows().get(rest.substring(0, dot));
    return workflow == null ? null : named(workflow, member);
  }

  // $sourceDescriptions.<name>.url
  private static Object source(ExpressionContext context, String expression) {
    String rest = after(expression, "$sourceDescriptions.");
    if (!rest.endsWith(URL)) {
      throw unsupported(expression);
    }
    return context.sources().get(rest.substring(0, rest.length() - URL.length()));
  }

  private static Object request(Exchange exchange, String source) {
    StepRequest request = exchange.request();
    if (source.startsWith(HEADER)) {
      return request.headers().get(after(source, HEADER).toLowerCase(Locale.ROOT));
    }
    if (source.startsWith("query.")) {
      return request.query().get(after(source, "query."));
    }
    if (source.startsWith("path.")) {
      return request.pathParameters().get(after(source, "path."));
    }
    if (isBody(source)) {
      return members(request.body().orElse(null), after(source, BODY));
    }
    throw unsupported("$request." + source);
  }

  private static Object response(Exchange exchange, String source) {
    StepResponse response = exchange.response();
    if (source.startsWith(HEADER)) {
      return response.headers().get(after(source, HEADER).toLowerCase(Locale.ROOT));
    }
    if (isBody(source)) {
      return members(response.body().orElse(null), after(source, BODY));
    }
    throw unsupported("$response." + source);
  }

  private static Object idThenMember(
      Map<String, Map<String, Object>> values, String rest, String member, String expression) {
    int dot = rest.indexOf('.');
    if (dot < 0 || !rest.startsWith(member, dot + 1)) {
      throw unsupported(expression);
    }
    Map<String, Object> value = values.get(rest.substring(0, dot));
    if (value == null) {
      return null;
    }
    String name = rest.substring(dot + 1 + member.length());
    if (name.isEmpty()) {
      return value;
    }
    if (name.charAt(0) != '.') {
      throw unsupported(expression);
    }
    return named(value, name.substring(1));
  }

  // a name that may contain dots: the longest name that exists, then the members of its value
  static Object named(Map<String, Object> values, String path) {
    if (values.containsKey(path)) {
      return values.get(path);
    }
    int dot = path.lastIndexOf('.');
    while (dot > 0) {
      String name = path.substring(0, dot);
      if (values.containsKey(name)) {
        return members(values.get(name), path.substring(dot));
      }
      dot = path.lastIndexOf('.', dot - 1);
    }
    return null;
  }

  // members separated by dots, e.g. ".data.id", as in the conditions of the specification
  private static Object members(Object value, String path) {
    Object current = value;
    if (path.isEmpty()) {
      return current;
    }
    for (String name : path.substring(1).split("\\.")) {
      if (current instanceof Map<?, ?> map) {
        current = map.get(name);
      } else {
        return null;
      }
    }
    return current;
  }

  private static boolean isBody(String source) {
    return BODY.equals(source) || source.startsWith(BODY + ".");
  }

  private static String after(String value, String prefix) {
    return value.substring(prefix.length());
  }

  private static IllegalArgumentException unsupported(String expression) {
    return new IllegalArgumentException(
        String.format("The runtime expression '%s' is not supported.", expression));
  }
}
