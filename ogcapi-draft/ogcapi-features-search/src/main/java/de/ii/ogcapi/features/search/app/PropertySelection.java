/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Resolves the effective property selection of a sub-query from the "properties" and
 * "excludeProperties" members of a query expression, which are both available globally (on the
 * query expression) and locally (on a single query).
 */
final class PropertySelection {

  static final String ALL = "*";

  private PropertySelection() {}

  /** The global and local exclusion lists, unioned, in order, without duplicates. */
  static List<String> exclusions(List<String> global, List<String> local) {
    return Stream.concat(global.stream(), local.stream()).distinct().toList();
  }

  /** The names that {@code known} does not contain, in order, without duplicates. */
  static List<String> unknown(List<String> names, Collection<String> known) {
    return names.stream().filter(name -> !known.contains(name)).distinct().toList();
  }

  /**
   * The effective field list: the global and local selection lists concatenated — or all properties
   * of the collection, if neither selects anything — minus the exclusions.
   *
   * <p>An exclusion that {@code all} does not contain is ignored, so that a global exclusion can be
   * applied to collections that do not have the property.
   *
   * <p>An empty result means that every property was excluded. The provider reads an empty field
   * list as "all properties", so the caller must reject that instead of passing it on.
   */
  static List<String> fields(
      Collection<String> all,
      List<String> global,
      List<String> local,
      Collection<String> exclusions) {
    List<String> selected = Stream.concat(global.stream(), local.stream()).distinct().toList();

    if (exclusions.isEmpty()) {
      return selected.isEmpty() ? ImmutableList.of(ALL) : selected;
    }

    return (selected.isEmpty() ? all : selected)
        .stream().filter(property -> !exclusions.contains(property)).distinct().toList();
  }
}
