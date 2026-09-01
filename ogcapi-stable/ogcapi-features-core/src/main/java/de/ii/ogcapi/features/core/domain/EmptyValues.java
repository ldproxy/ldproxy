/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.fasterxml.jackson.databind.JsonNode;
import de.ii.xtraplatform.features.domain.pipeline.FeatureEventHandlerEmptyValues;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Optional;

/**
 * Finds an empty value — a string with no characters or with only whitespace — in the value of a
 * single property, for the one case that does not go through a decoder: a partial update, which
 * carries the new values themselves instead of a feature payload.
 *
 * <p>A request body is checked while it is decoded, by {@link FeatureEventHandlerEmptyValues},
 * which also defines what counts as empty and how a rejection reads.
 */
public final class EmptyValues {

  private EmptyValues() {}

  /**
   * The path of the first empty value in {@code node}, or an empty {@code Optional} if it has none.
   * Object members are joined with {@code .}, array members carry their index; the empty path of a
   * scalar at the root is reported as {@code .}.
   */
  public static Optional<String> firstEmptyValue(JsonNode node) {
    return firstEmptyValue(node, "");
  }

  /**
   * Joins the path of the updated property with the path reported inside its value by {@link
   * #firstEmptyValue(JsonNode)}.
   */
  public static String join(String path, String subPath) {
    if (".".equals(subPath)) {
      return path;
    }
    return subPath.startsWith("[") ? path + subPath : path + "." + subPath;
  }

  private static Optional<String> firstEmptyValue(JsonNode node, String path) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return Optional.empty();
    }
    if (node.isTextual()) {
      return FeatureEventHandlerEmptyValues.isEmpty(node.textValue())
          ? Optional.of(path.isEmpty() ? "." : path)
          : Optional.empty();
    }
    if (node.isObject()) {
      Iterator<Entry<String, JsonNode>> members = node.fields();
      while (members.hasNext()) {
        Entry<String, JsonNode> member = members.next();
        Optional<String> found =
            firstEmptyValue(
                member.getValue(), path.isEmpty() ? member.getKey() : path + "." + member.getKey());
        if (found.isPresent()) {
          return found;
        }
      }
    } else if (node.isArray()) {
      for (int i = 0; i < node.size(); i++) {
        Optional<String> found = firstEmptyValue(node.get(i), path + "[" + i + "]");
        if (found.isPresent()) {
          return found;
        }
      }
    }
    return Optional.empty();
  }
}
