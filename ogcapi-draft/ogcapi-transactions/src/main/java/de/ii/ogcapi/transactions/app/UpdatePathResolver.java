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
    if (inputPath == null || inputPath.isEmpty()) {
      throw new IllegalArgumentException("Property path must not be empty");
    }
    List<FeatureSchema> resolved = new ArrayList<>(inputPath.size());
    FeatureSchema parent = root;
    int i = 0;
    while (i < inputPath.size()) {
      String segment = inputPath.get(i);
      FeatureSchema matched = findProperty(parent, segment, inputUseAlias);
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
}
