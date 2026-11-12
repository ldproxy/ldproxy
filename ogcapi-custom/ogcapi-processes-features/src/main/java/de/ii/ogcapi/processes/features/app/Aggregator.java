/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.features.app;

import de.ii.ogcapi.processes.features.app.Aggregation.Aggregate;
import de.ii.ogcapi.processes.features.app.Aggregation.Function;
import de.ii.ogcapi.processes.features.app.Aggregation.GroupKey;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Computes the groups and aggregates of {@code feature-aggregate:execute} from a stream of
 * features. Each feature counts once in each group that it belongs to: a property with several
 * values puts the feature into one group per value, a missing value forms the group {@code null}.
 * Only {@code median} keeps the values, all other aggregates keep a fixed state per group.
 */
class Aggregator {

  static final String OBJECTTYPE = "objecttype";

  static final String OUTPUT_GROUPS = "groups";
  static final String OUTPUT_TOTAL = "total";
  static final String OUTPUT_FEATURES_SCANNED = "featuresScanned";
  static final String OUTPUT_GROUPS_TRUNCATED = "groupsTruncated";

  private static final int DECIMALS = 3;

  private final Aggregation aggregation;
  private final boolean byCollection;
  private final List<String> keyNames;
  private final Map<List<Object>, List<Accumulator>> groups;
  private final List<Accumulator> total;
  private long featuresScanned;

  Aggregator(Aggregation aggregation, boolean byCollection) {
    this.aggregation = aggregation;
    this.byCollection = byCollection;
    this.keyNames = new ArrayList<>();
    if (byCollection) {
      keyNames.add(OBJECTTYPE);
    }
    aggregation.groupBy().forEach(key -> keyNames.add(key.property()));
    this.groups = new LinkedHashMap<>();
    this.total = accumulators();
  }

  long getFeaturesScanned() {
    return featuresScanned;
  }

  /**
   * @param record the feature
   * @param virtualValues the values of the virtual properties, e.g. the area of the geometry or the
   *     center of its grid cell
   */
  void add(FeatureRecord record, Map<String, Object> virtualValues) {
    featuresScanned++;

    List<List<Object>> dimensions = new ArrayList<>();
    if (byCollection) {
      dimensions.add(singleton(record.collectionId()));
    }
    for (GroupKey key : aggregation.groupBy()) {
      List<Object> values = values(record, virtualValues, key.property());
      List<Object> bucketed = new ArrayList<>();
      for (Object value : values) {
        // the grid cell is already the bucket
        bucketed.add(Aggregation.GEOMETRY.equals(key.property()) ? value : bucket(key, value));
      }
      dimensions.add(bucketed.isEmpty() ? singleton(null) : bucketed);
    }

    List<List<Object>> aggregateValues = new ArrayList<>();
    for (Aggregate aggregate : aggregation.aggregates()) {
      aggregateValues.add(
          aggregate.property().map(p -> values(record, virtualValues, p)).orElse(List.of()));
    }

    accumulate(total, aggregateValues);
    for (List<Object> key : keys(dimensions)) {
      accumulate(groups.computeIfAbsent(key, k -> accumulators()), aggregateValues);
    }
  }

  /** The outputs: the groups, the total, the number of features and whether groups were cut. */
  Map<String, Object> result() {
    List<Map.Entry<List<Object>, List<Accumulator>>> entries = new ArrayList<>(groups.entrySet());
    // the first aggregate decides the order, the largest first
    entries.sort(
        Comparator.comparing(
                (Map.Entry<List<Object>, List<Accumulator>> entry) ->
                    entry.getValue().get(0).value(),
                Comparator.nullsLast(Aggregator::compareDescending))
            .thenComparing(entry -> String.valueOf(entry.getKey())));

    int limit = aggregation.limit().orElse(Integer.MAX_VALUE);
    List<Map<String, Object>> groupList = new ArrayList<>();
    for (Map.Entry<List<Object>, List<Accumulator>> entry : entries) {
      if (groupList.size() >= limit) {
        break;
      }
      Map<String, Object> group = new LinkedHashMap<>();
      Map<String, Object> key = new LinkedHashMap<>();
      for (int i = 0; i < keyNames.size(); i++) {
        key.put(keyNames.get(i), entry.getKey().get(i));
      }
      group.put("key", key);
      group.putAll(values(entry.getValue()));
      groupList.add(group);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put(OUTPUT_GROUPS, groupList);
    result.put(OUTPUT_TOTAL, values(total));
    result.put(OUTPUT_FEATURES_SCANNED, featuresScanned);
    result.put(OUTPUT_GROUPS_TRUNCATED, groupList.size() < entries.size());
    return result;
  }

  private Map<String, Object> values(List<Accumulator> accumulators) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (int i = 0; i < accumulators.size(); i++) {
      values.put(aggregation.aggregates().get(i).name(), accumulators.get(i).value());
    }
    return values;
  }

  private List<Accumulator> accumulators() {
    List<Accumulator> accumulators = new ArrayList<>();
    aggregation
        .aggregates()
        .forEach(
            aggregate ->
                accumulators.add(
                    new Accumulator(aggregate.function(), aggregate.property().isEmpty())));
    return accumulators;
  }

  private static void accumulate(List<Accumulator> accumulators, List<List<Object>> values) {
    for (int i = 0; i < accumulators.size(); i++) {
      accumulators.get(i).add(values.get(i));
    }
  }

  private static List<Object> values(
      FeatureRecord record, Map<String, Object> virtualValues, String property) {
    if (Aggregation.isVirtual(property)) {
      Object value = virtualValues.get(property);
      return Objects.isNull(value) ? List.of() : List.of(value);
    }
    return record.values(property);
  }

  private static List<Object> singleton(Object value) {
    List<Object> list = new ArrayList<>(1);
    list.add(value);
    return list;
  }

  // the combinations of the values of all dimensions, each combination once
  private static Set<List<Object>> keys(List<List<Object>> dimensions) {
    Set<List<Object>> keys = new LinkedHashSet<>();
    keys.add(List.of());
    for (List<Object> dimension : dimensions) {
      Set<List<Object>> next = new LinkedHashSet<>();
      for (List<Object> key : keys) {
        for (Object value : dimension) {
          List<Object> extended = new ArrayList<>(key);
          extended.add(value);
          next.add(extended);
        }
      }
      keys = next;
    }
    return keys;
  }

  static Object bucket(GroupKey key, Object value) {
    if (key.bucket().isEmpty() || Objects.isNull(value)) {
      return value;
    }
    return switch (key.bucket().get()) {
      case YEAR -> value instanceof String s && s.length() >= 4 ? s.substring(0, 4) : value;
      case MONTH -> value instanceof String s && s.length() >= 7 ? s.substring(0, 7) : value;
      case WIDTH -> {
        Double number = number(value);
        if (Objects.isNull(number)) {
          yield value;
        }
        double lower = Math.floor(number / key.width()) * key.width();
        yield lower == Math.rint(lower) && key.width() == Math.rint(key.width())
            ? (Object) (long) lower
            : (Object) lower;
      }
    };
  }

  static Double number(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value instanceof String string) {
      try {
        return Double.parseDouble(string);
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static int compareDescending(Object a, Object b) {
    return compare(b, a);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  static int compare(Object a, Object b) {
    if (a instanceof Number x && b instanceof Number y) {
      return Double.compare(x.doubleValue(), y.doubleValue());
    }
    return String.valueOf(a).compareTo(String.valueOf(b));
  }

  private static Object round(double value) {
    return BigDecimal.valueOf(value).setScale(DECIMALS, RoundingMode.HALF_UP).doubleValue();
  }

  /** The state of one aggregate in one group. */
  private static final class Accumulator {
    private final Function function;
    private final boolean perFeature;
    private long count;
    private double sum;
    private boolean integral = true;
    private Object min;
    private Object max;
    private List<Double> values;

    Accumulator(Function function, boolean perFeature) {
      this.function = function;
      this.perFeature = perFeature;
      if (function == Function.MEDIAN) {
        this.values = new ArrayList<>();
      }
    }

    // the values of the property of one feature; count without a property counts the feature
    void add(List<Object> featureValues) {
      switch (function) {
        case COUNT -> {
          if (perFeature || !featureValues.isEmpty()) {
            count++;
          }
        }
        case MIN, MAX -> featureValues.forEach(this::addComparable);
        default -> featureValues.forEach(this::addNumber);
      }
    }

    private void addComparable(Object value) {
      if (Objects.isNull(min) || compare(value, min) < 0) {
        min = value;
      }
      if (Objects.isNull(max) || compare(value, max) > 0) {
        max = value;
      }
    }

    // values that are not numbers are left out
    private void addNumber(Object value) {
      Double number = number(value);
      if (Objects.isNull(number)) {
        return;
      }
      count++;
      sum += number;
      integral = integral && value instanceof Long;
      if (Objects.nonNull(values)) {
        values.add(number);
      }
    }

    Object value() {
      return switch (function) {
        case COUNT -> count;
        case MIN -> min;
        case MAX -> max;
        case SUM -> count == 0 ? null : integral ? (Object) (long) sum : round(sum);
        case AVG -> count == 0 ? null : round(sum / count);
        case MEDIAN -> median();
      };
    }

    private Object median() {
      if (values.isEmpty()) {
        return null;
      }
      values.sort(Double::compare);
      int middle = values.size() / 2;
      double median =
          values.size() % 2 == 1
              ? values.get(middle)
              : (values.get(middle - 1) + values.get(middle)) / 2;
      return integral && median == Math.rint(median) ? (Object) (long) median : round(median);
    }
  }
}
