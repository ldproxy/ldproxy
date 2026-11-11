/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.features.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** The inputs of {@code feature-aggregate:execute} that define the groups and the aggregates. */
record Aggregation(List<GroupKey> groupBy, List<Aggregate> aggregates, Optional<Integer> limit) {

  static final String AREA = "$area";
  static final String LENGTH = "$length";

  static final String INPUT_GROUP_BY = "groupBy";
  static final String INPUT_AGGREGATES = "aggregates";
  static final String INPUT_LIMIT = "limit";

  enum Function {
    COUNT,
    SUM,
    MIN,
    MAX,
    AVG,
    MEDIAN;

    String id() {
      return name().toLowerCase(Locale.ROOT);
    }

    boolean isNumeric() {
      return this == SUM || this == AVG || this == MEDIAN;
    }
  }

  enum Bucket {
    YEAR,
    MONTH,
    WIDTH
  }

  /** A group key: a property, optionally with a bucket for temporal or numeric values. */
  record GroupKey(String property, Optional<Bucket> bucket, double width) {}

  /** An aggregate: a function, optionally of a property. */
  record Aggregate(Function function, Optional<String> property) {
    String name() {
      return property.map(p -> String.format("%s(%s)", function.id(), p)).orElse(function.id());
    }
  }

  /** The properties of the features that the groups and aggregates need. */
  List<String> properties() {
    List<String> properties = new ArrayList<>();
    groupBy.forEach(key -> properties.add(key.property()));
    aggregates.forEach(aggregate -> aggregate.property().ifPresent(properties::add));
    return properties.stream().distinct().toList();
  }

  boolean needsGeometry() {
    return properties().stream().anyMatch(Aggregation::isVirtual);
  }

  static boolean isVirtual(String property) {
    return AREA.equals(property) || LENGTH.equals(property);
  }

  /**
   * @throws IllegalArgumentException if an input is invalid
   */
  static Aggregation of(Map<String, Object> inputs) {
    List<GroupKey> groupBy = new ArrayList<>();
    for (Object key : list(inputs.get(INPUT_GROUP_BY), INPUT_GROUP_BY)) {
      groupBy.add(groupKey(key));
    }

    List<Aggregate> aggregates = new ArrayList<>();
    for (Object aggregate : list(inputs.get(INPUT_AGGREGATES), INPUT_AGGREGATES)) {
      aggregates.add(aggregate(aggregate));
    }
    if (aggregates.isEmpty()) {
      aggregates.add(new Aggregate(Function.COUNT, Optional.empty()));
    }
    if (aggregates.stream().map(Aggregate::name).distinct().count() < aggregates.size()) {
      throw new IllegalArgumentException("The input 'aggregates' contains an aggregate twice.");
    }

    Optional<Integer> limit = Optional.empty();
    Object limitValue = inputs.get(INPUT_LIMIT);
    if (Objects.nonNull(limitValue)) {
      if (!(limitValue instanceof Number number) || number.intValue() < 1) {
        throw new IllegalArgumentException("The input 'limit' must be a positive integer.");
      }
      limit = Optional.of(number.intValue());
    }

    return new Aggregation(List.copyOf(groupBy), List.copyOf(aggregates), limit);
  }

  private static List<?> list(Object value, String input) {
    if (Objects.isNull(value)) {
      return List.of();
    }
    if (value instanceof List<?> list) {
      return list;
    }
    throw new IllegalArgumentException(String.format("The input '%s' must be an array.", input));
  }

  private static GroupKey groupKey(Object value) {
    if (value instanceof String property) {
      return new GroupKey(property(property, INPUT_GROUP_BY), Optional.empty(), 0);
    }
    if (value instanceof Map<?, ?> map && map.get("property") instanceof String property) {
      Object bucket = map.get("bucket");
      if (Objects.isNull(bucket)) {
        return new GroupKey(property(property, INPUT_GROUP_BY), Optional.empty(), 0);
      }
      if (bucket instanceof Number width && width.doubleValue() > 0) {
        return new GroupKey(
            property(property, INPUT_GROUP_BY), Optional.of(Bucket.WIDTH), width.doubleValue());
      }
      if ("year".equals(bucket)) {
        return new GroupKey(property(property, INPUT_GROUP_BY), Optional.of(Bucket.YEAR), 0);
      }
      if ("month".equals(bucket)) {
        return new GroupKey(property(property, INPUT_GROUP_BY), Optional.of(Bucket.MONTH), 0);
      }
      throw new IllegalArgumentException(
          String.format(
              "The bucket of the group key '%s' must be 'year', 'month' or a positive number.",
              property));
    }
    throw new IllegalArgumentException(
        "A group key must be the name of a property or an object with the member 'property'.");
  }

  private static Aggregate aggregate(Object value) {
    if (value instanceof String function) {
      return new Aggregate(function(function), Optional.empty());
    }
    if (value instanceof Map<?, ?> map && map.get("function") instanceof String function) {
      Function f = function(function);
      Object property = map.get("property");
      if (Objects.isNull(property)) {
        if (f != Function.COUNT) {
          throw new IllegalArgumentException(
              String.format("The aggregate '%s' needs a property.", function));
        }
        return new Aggregate(f, Optional.empty());
      }
      if (property instanceof String p) {
        return new Aggregate(f, Optional.of(property(p, INPUT_AGGREGATES)));
      }
    }
    throw new IllegalArgumentException(
        "An aggregate must be 'count' or an object with the members 'function' and 'property'.");
  }

  private static Function function(String function) {
    for (Function f : Function.values()) {
      if (f.id().equals(function)) {
        return f;
      }
    }
    throw new IllegalArgumentException(
        String.format(
            "Unknown aggregate function '%s', the functions are count, sum, min, max, avg and median.",
            function));
  }

  private static String property(String property, String input) {
    if (property.isBlank()) {
      throw new IllegalArgumentException(
          String.format("The input '%s' contains an empty property name.", input));
    }
    return property;
  }
}
