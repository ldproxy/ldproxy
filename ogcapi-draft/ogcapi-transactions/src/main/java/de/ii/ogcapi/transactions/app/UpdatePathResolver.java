/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app;

import de.ii.xtraplatform.features.domain.FeatureSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resolves a wfs:Update / JSON-transaction property path against a {@link FeatureSchema}.
 *
 * <p>Inputs are parsed as a list of segments. Each property segment is matched against either the
 * schema's id or its alias, depending on the caller-supplied {@code inputUseAlias} flag (driven by
 * the input format's {@code useAlias}, e.g. {@code GmlConfiguration.useAlias} for {@code
 * wfs:Transaction}).
 *
 * <p>{@code inputHasObjectTypeSteps} controls XPath-style object-type wrapper segments:
 *
 * <ul>
 *   <li>{@code true} ({@code wfs:ValueReference} input, XPath convention): an intermediate
 *       object-type element segment is required and validated against the property's {@code
 *       objectType}. Mismatches raise {@link IllegalArgumentException}.
 *   <li>{@code false} (JSON-transaction, config, and other ldproxy-canonical contexts): the path is
 *       just the property identifiers; no object-type segment is expected.
 * </ul>
 *
 * <p>An input path may also address a property through the element chain the GML building block's
 * {@code xmlPaths} option maps it to (see {@link #resolve(FeatureSchema, List, boolean, boolean,
 * Map)}): the chain's elements name no schema property, so they are consumed as a whole and resolve
 * to the mapped property — e.g. {@code lebenszeitintervall/AA_Lebenszeitintervall/beginnt} for a
 * flat {@code lzi_beg}.
 *
 * <p>The resolved path is a list of {@link FeatureSchema} property nodes (object-type segments, if
 * any, are consumed but not stored); convert to an output form with {@link #toOutputPath}.
 */
final class UpdatePathResolver {

  private UpdatePathResolver() {}

  static List<FeatureSchema> resolve(
      FeatureSchema root, List<String> inputPath, boolean inputUseAlias) {
    return resolve(root, inputPath, inputUseAlias, false);
  }

  static List<FeatureSchema> resolve(
      FeatureSchema root,
      List<String> inputPath,
      boolean inputUseAlias,
      boolean inputHasObjectTypeSteps) {
    return resolve(root, inputPath, inputUseAlias, inputHasObjectTypeSteps, Map.of());
  }

  /**
   * @param xmlPaths the {@code xmlPaths} chains configured for the collection, keyed by property
   *     path (id or alias form) — the element chains the input may use to address a property whose
   *     own element the wire does not carry. Empty for input formats without such a mapping.
   */
  static List<FeatureSchema> resolve(
      FeatureSchema root,
      List<String> inputPath,
      boolean inputUseAlias,
      boolean inputHasObjectTypeSteps,
      Map<String, List<String>> xmlPaths) {
    if (inputPath == null || inputPath.isEmpty()) {
      throw new IllegalArgumentException("Property path must not be empty");
    }
    List<FeatureSchema> resolved = new ArrayList<>(inputPath.size());
    FeatureSchema parent = root;
    int i = 0;
    while (i < inputPath.size()) {
      String segment = inputPath.get(i);
      FeatureSchema matched = findProperty(parent, segment, inputUseAlias);
      if (matched == null && !xmlPaths.isEmpty()) {
        ChainMatch chainMatch = matchXmlPathChain(parent, resolved, inputPath, i, xmlPaths);
        if (chainMatch != null) {
          resolved.add(chainMatch.property);
          parent = chainMatch.property;
          i = chainMatch.nextIndex;
          continue;
        }
      }
      if (matched == null) {
        throw new IllegalArgumentException(
            "Property path segment '"
                + segment
                + "' does not match any property of '"
                + parent.getName()
                + "'. Use the schema "
                + (inputUseAlias ? "alias" : "id")
                + " of the target property.");
      }
      resolved.add(matched);
      i++;

      // XPath input only: consume the object-type element segment (e.g. AA_Lebenszeitintervall
      // between `lebenszeitintervall` and `endet`). For ldproxy-canonical input (JSON-tx,
      // config, CQL2 queryables) the path is just properties; no object-type segment is
      // expected.
      if (inputHasObjectTypeSteps && matched.getObjectType().isPresent() && i < inputPath.size()) {
        String expected = matched.getObjectType().get();
        String actual = inputPath.get(i);
        if (!expected.equals(actual)) {
          throw new IllegalArgumentException(
              "Property path expects object-type element '"
                  + expected
                  + "' after property '"
                  + matched.getName()
                  + "', but got '"
                  + actual
                  + "'.");
        }
        i++;
      }
      parent = matched;
    }
    return resolved;
  }

  static List<String> toOutputPath(List<FeatureSchema> resolved, boolean outputUseAlias) {
    List<String> out = new ArrayList<>(resolved.size());
    for (FeatureSchema s : resolved) {
      out.add(outputUseAlias ? s.getAlias().orElse(s.getName()) : s.getName());
    }
    return out;
  }

  private static FeatureSchema findProperty(
      FeatureSchema parent, String segment, boolean useAlias) {
    for (FeatureSchema child : parent.getProperties()) {
      String expected = useAlias ? child.getAlias().orElse(child.getName()) : child.getName();
      if (expected.equals(segment)) {
        return child;
      }
    }
    return null;
  }

  /**
   * A property addressed through its {@code xmlPaths} chain, and the input index after the chain.
   */
  private static final class ChainMatch {
    final FeatureSchema property;
    final int nextIndex;

    ChainMatch(FeatureSchema property, int nextIndex) {
      this.property = property;
      this.nextIndex = nextIndex;
    }
  }

  /**
   * Matches the input path from {@code start} against the {@code xmlPaths} chain of any child of
   * {@code parent}. All of the chain's elements must be present in order; injected empty elements
   * (configured with a trailing {@code /}) are siblings of a chain element rather than ancestors
   * and are therefore not part of an input path. Returns {@code null} when no child's chain starts
   * with the input segment; throws when a chain starts but the path does not address its innermost
   * element — the only element that carries the property's value.
   */
  private static ChainMatch matchXmlPathChain(
      FeatureSchema parent,
      List<FeatureSchema> resolvedSoFar,
      List<String> inputPath,
      int start,
      Map<String, List<String>> xmlPaths) {
    List<String> longestStart = null;
    FeatureSchema longestStartProperty = null;
    for (FeatureSchema child : parent.getProperties()) {
      List<String> chain = chainFor(child, resolvedSoFar, xmlPaths);
      if (chain == null || chain.isEmpty()) {
        continue;
      }
      List<String> elements =
          chain.stream()
              .filter(segment -> !isEmptyElementSegment(segment))
              .map(UpdatePathResolver::localName)
              .collect(Collectors.toList());
      if (!elements.get(0).equals(inputPath.get(start))) {
        continue;
      }
      if (longestStart == null) {
        longestStart = elements;
        longestStartProperty = child;
      }
      if (start + elements.size() > inputPath.size()) {
        continue;
      }
      if (elements.equals(inputPath.subList(start, start + elements.size()))) {
        return new ChainMatch(child, start + elements.size());
      }
    }
    if (longestStart != null) {
      throw new IllegalArgumentException(
          "Property path '"
              + String.join("/", inputPath.subList(start, inputPath.size()))
              + "' addresses the element structure of property '"
              + longestStartProperty.getName()
              + "' but not its value element; use the full element path '"
              + String.join("/", longestStart)
              + "'.");
    }
    return null;
  }

  /**
   * The configured chain of {@code child}, looked up by its property path in id form and — since
   * the option may be keyed either way — in alias form.
   */
  private static List<String> chainFor(
      FeatureSchema child, List<FeatureSchema> resolvedSoFar, Map<String, List<String>> xmlPaths) {
    StringBuilder idPath = new StringBuilder();
    StringBuilder aliasPath = new StringBuilder();
    for (FeatureSchema ancestor : resolvedSoFar) {
      idPath.append(ancestor.getName()).append('.');
      aliasPath.append(ancestor.getAlias().orElse(ancestor.getName())).append('.');
    }
    idPath.append(child.getName());
    aliasPath.append(child.getAlias().orElse(child.getName()));
    List<String> chain = xmlPaths.get(idPath.toString());
    return chain != null ? chain : xmlPaths.get(aliasPath.toString());
  }

  private static boolean isEmptyElementSegment(String segment) {
    return segment.trim().endsWith("/");
  }

  /** The local name of a chain segment: its {@code prefix:} and attribute predicates removed. */
  private static String localName(String segment) {
    String name = segment.trim();
    int bracket = name.indexOf('[');
    if (bracket >= 0) {
      name = name.substring(0, bracket).trim();
    }
    int colon = name.indexOf(':');
    return colon >= 0 ? name.substring(colon + 1) : name;
  }
}
