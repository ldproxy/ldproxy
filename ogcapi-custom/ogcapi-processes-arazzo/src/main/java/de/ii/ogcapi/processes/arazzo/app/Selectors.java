/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import io.github.treetrail.jsonpath.EvaluationLimits;
import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.JsonPathEvaluationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathEvaluationResult;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathNodes;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * The selector objects of Arazzo 1.1 and the criteria of the types {@code jsonpath} and {@code
 * xpath}: JSONPath as in RFC 9535, XPath 1.0 on XML content and JSON Pointer.
 *
 * <p>A selector that selects one node has the value of the node, a selector that selects several
 * nodes the list of their values, one that selects nothing has no value. The specification leaves
 * the value open; a filter that selects exactly one document can then be used like a single value,
 * e.g. as a path parameter.
 */
final class Selectors {

  static final String JSONPATH = "jsonpath";
  static final String XPATH = "xpath";
  static final String JSONPOINTER = "jsonpointer";
  static final Set<String> TYPES = Set.of(JSONPATH, XPATH, JSONPOINTER);

  private static final Pattern ROOT_FILTER = Pattern.compile("\\$\\s*\\[\\s*\\?");
  private static final String CONTEXT = "context";
  private static final String SELECTOR = "selector";
  private static final String TYPE = "type";
  // a run of a query in a workflow visits at most this many nodes, about 0.2 seconds
  private static final EvaluationLimits LIMITS =
      EvaluationLimits.DEFAULT.withMaxVisitedNodes(10_000_000);

  private Selectors() {}

  /**
   * @return true, if the value is a selector object: a context, a selector, a type and only
   *     extensions besides
   */
  static boolean isSelector(Object value) {
    return value instanceof Map<?, ?> map
        && map.containsKey(CONTEXT)
        && map.containsKey(SELECTOR)
        && map.containsKey(TYPE)
        && map.keySet().stream()
            .map(String::valueOf)
            .allMatch(
                key ->
                    CONTEXT.equals(key)
                        || SELECTOR.equals(key)
                        || TYPE.equals(key)
                        || key.startsWith("x-"));
  }

  /**
   * @return the problem of a selector object, null if there is none
   */
  static String problem(Map<?, ?> selector) {
    if (!(selector.get(CONTEXT) instanceof String context) || !context.startsWith("$")) {
      return "the context of a selector must be a runtime expression";
    }
    if (!(selector.get(SELECTOR) instanceof String expression)) {
      return "the selector of a selector object must be a string";
    }
    String type = Criteria.typeName(selector.get(TYPE));
    if (!TYPES.contains(type)) {
      return String.format(
          "the selector type '%s' is unknown, only 'jsonpath', 'xpath' and 'jsonpointer' are"
              + " supported",
          type);
    }
    return expression.contains("{$") ? null : syntaxProblem(type, expression);
  }

  /**
   * @return the syntax problem of a JSONPath, XPath or JSON Pointer expression, null if there is
   *     none
   */
  static String syntaxProblem(String type, String expression) {
    try {
      switch (type) {
        case JSONPATH -> JsonPath.compile(expression);
        case XPATH -> XPathFactory.newInstance().newXPath().compile(expression);
        default -> JsonPointers.tokens(expression);
      }
      return null;
    } catch (IllegalArgumentException | XPathExpressionException e) {
      return String.format("the %s expression '%s' is invalid: %s", type, expression, message(e));
    }
  }

  /**
   * @return the value of the selector object
   * @throws IllegalArgumentException if the selector cannot be evaluated
   */
  static Object select(Map<?, ?> selector, ExpressionContext context) {
    Object document = RuntimeExpressions.evaluate(String.valueOf(selector.get(CONTEXT)), context);
    String expression = String.valueOf(selector.get(SELECTOR));
    if (expression.contains("{$")) {
      expression = RuntimeExpressions.interpolate(expression, context);
    }
    return switch (Criteria.typeName(selector.get(TYPE))) {
      case JSONPATH -> single(jsonPath(document, expression));
      case XPATH -> single(xpathNodes(document, expression));
      default -> JsonPointers.get(document, expression);
    };
  }

  /**
   * A JSONPath criterion passes, if the query selects at least one node. A query that starts with a
   * filter, e.g. {@code $[?count(@.pets) > 0]}, tests an object context itself, as in the examples
   * of the specification; in RFC 9535 it would test the members of the object. For an array
   * context, it tests the items.
   *
   * @throws IllegalArgumentException if the query is invalid or cannot be evaluated
   */
  static boolean matchesJsonPath(Object document, String query) {
    Object root =
        document instanceof Map<?, ?> && ROOT_FILTER.matcher(query).lookingAt()
            ? List.of(document)
            : document;
    return !jsonPath(root, query).isEmpty();
  }

  /**
   * @return true, if the XPath expression is true, a number other than 0, a non-empty string or a
   *     non-empty node set, as in the specification
   * @throws IllegalArgumentException if the expression is invalid or the document is not XML
   */
  static boolean matchesXPath(Object document, String expression) {
    XPathEvaluationResult<?> result = xpath(document, expression);
    Object value = result.value();
    return switch (result.type()) {
      case BOOLEAN -> Boolean.TRUE.equals(value);
      case NUMBER -> value instanceof Number number && number.doubleValue() != 0;
      case STRING -> value instanceof String string && !string.isEmpty();
      case NODE -> Objects.nonNull(value);
      case NODESET -> value instanceof XPathNodes nodes && nodes.size() > 0;
      default -> false;
    };
  }

  private static List<Object> jsonPath(Object document, String query) {
    try {
      return JsonPath.compile(query).withLimits(LIMITS).query(document).values();
    } catch (JsonPathEvaluationException e) {
      throw new IllegalArgumentException(
          String.format("The JSONPath query '%s' cannot be evaluated: %s", query, e.getMessage()),
          e);
    }
  }

  // the string values of the selected nodes, or the value of an expression that is no node set
  private static List<Object> xpathNodes(Object document, String expression) {
    XPathEvaluationResult<?> result = xpath(document, expression);
    if (result.value() instanceof XPathNodes nodes) {
      List<Object> values = new ArrayList<>();
      nodes.forEach(node -> values.add(node.getTextContent()));
      return values;
    }
    if (result.value() instanceof Node node) {
      return List.of(node.getTextContent());
    }
    return Objects.isNull(result.value()) ? List.of() : List.of(result.value());
  }

  private static XPathEvaluationResult<?> xpath(Object document, String expression) {
    try {
      return XPathFactory.newInstance()
          .newXPath()
          .evaluateExpression(expression, xml(document), XPathEvaluationResult.class);
    } catch (XPathExpressionException e) {
      throw new IllegalArgumentException(
          String.format("The XPath expression '%s' is invalid: %s", expression, message(e)), e);
    }
  }

  // the content of a response that is not JSON is a string; DTDs and external entities are refused
  private static Document xml(Object document) {
    if (!(document instanceof String content)) {
      throw new IllegalArgumentException("The context of an XPath expression is not XML.");
    }
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      DocumentBuilder builder = factory.newDocumentBuilder();
      return builder.parse(new InputSource(new StringReader(content)));
    } catch (ParserConfigurationException | SAXException | IOException e) {
      throw new IllegalArgumentException(
          "The context of an XPath expression is not XML: " + e.getMessage(), e);
    }
  }

  private static Object single(List<Object> values) {
    if (values.isEmpty()) {
      return null;
    }
    return values.size() == 1 ? values.get(0) : new ArrayList<>(values);
  }

  private static String message(Exception e) {
    return Objects.requireNonNullElse(
        Objects.nonNull(e.getCause()) ? e.getCause().getMessage() : e.getMessage(), e.toString());
  }
}
