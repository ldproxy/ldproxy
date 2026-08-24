/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.google.common.collect.ImmutableSet;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaVisitorTopDown;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The properties of a feature type whose value a request body must not set, as paths of property
 * names relative to the feature, separated by {@code .}.
 *
 * <p>A property is read-only in the published schema if it is returnable but not receivable — the
 * same condition that {@code SchemaDeriver} uses to derive the {@code readOnly} member, so what is
 * published and what is rejected cannot drift apart.
 *
 * <p>The property with the role {@code ID} is not included, although it is published as {@code
 * readOnly}: an identifier in the request body of a POST or PUT has to be ignored, and a JSON Merge
 * Patch that changes it is rejected separately.
 */
public final class ReadOnlyProperties implements SchemaVisitorTopDown<FeatureSchema, Set<String>> {

  private static final ReadOnlyProperties VISITOR = new ReadOnlyProperties();

  private ReadOnlyProperties() {}

  /** The read-only properties of the feature type. */
  public static Set<String> of(FeatureSchema featureType) {
    return featureType.accept(VISITOR);
  }

  /** Whether the path is a read-only property or a property of one. */
  public static boolean contains(Set<String> readOnly, String path) {
    return readOnly.stream()
        .anyMatch(readOnlyPath -> path.equals(readOnlyPath) || path.startsWith(readOnlyPath + "."));
  }

  @Override
  public Set<String> visit(
      FeatureSchema schema, List<FeatureSchema> parents, List<Set<String>> visitedProperties) {
    // the feature type itself is not a property that a request body could set
    if (parents.isEmpty()) {
      return union(visitedProperties);
    }

    if (schema.isId()) {
      return ImmutableSet.of();
    }

    if (schema.receivable() || !schema.returnable()) {
      return union(visitedProperties);
    }

    // a read-only object covers the properties below it, which are not reported separately
    return ImmutableSet.of(path(schema, parents));
  }

  private static String path(FeatureSchema schema, List<FeatureSchema> parents) {
    return Stream.concat(
            // the first parent is the feature type, which contributes no path segment
            parents.stream().skip(1).map(FeatureSchema::getName), Stream.of(schema.getName()))
        .collect(Collectors.joining("."));
  }

  private static Set<String> union(List<Set<String>> visitedProperties) {
    return visitedProperties.stream().flatMap(Set::stream).collect(ImmutableSet.toImmutableSet());
  }
}
