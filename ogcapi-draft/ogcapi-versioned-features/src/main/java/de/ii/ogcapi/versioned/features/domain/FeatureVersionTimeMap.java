/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.domain;

import com.google.common.base.Splitter;
import de.ii.xtraplatform.features.domain.FeatureBase;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.immutables.value.Value;

/**
 * Per-version POJO populated by {@link FeatureEncoderTimeMap} from the underlying feature stream.
 * The {@code FeatureQuery} is restricted to the primary temporal interval plus, for formats that
 * render the feature title, the properties referenced by the collection's feature title template.
 * The start and end values may sit under a parent OBJECT (e.g. {@code
 * lebenszeitintervall.beginnt}), so the accessors walk the full property subtree.
 */
@Value.Modifiable
@Value.Style(set = "*")
public interface FeatureVersionTimeMap extends FeatureBase<PropertyTimeMap, FeatureSchema> {

  /** The raw start value as emitted by the provider — a date stays a date. */
  @Value.Lazy
  default Optional<String> getStartValue() {
    return findProperty(SchemaBase::isPrimaryIntervalStart).map(PropertyTimeMap::getValue);
  }

  @Value.Lazy
  default Optional<Instant> getStart() {
    return getStartValue().map(FeatureVersionTimeMap::parse);
  }

  /** {@code true} when the start property is of type {@code DATE}. */
  @Value.Lazy
  default boolean isStartDate() {
    return isDate(findProperty(SchemaBase::isPrimaryIntervalStart));
  }

  /** The raw end value as emitted by the provider — a date stays a date. */
  @Value.Lazy
  default Optional<String> getEndValue() {
    return findProperty(SchemaBase::isPrimaryIntervalEnd).map(PropertyTimeMap::getValue);
  }

  @Value.Lazy
  default Optional<Instant> getEnd() {
    return getEndValue().map(FeatureVersionTimeMap::parse);
  }

  /** {@code true} when the end property is of type {@code DATE}. */
  @Value.Lazy
  default boolean isEndDate() {
    return isDate(findProperty(SchemaBase::isPrimaryIntervalEnd));
  }

  /**
   * The first value of the property at the given dot-separated path, used to resolve the feature
   * title template against this version's properties.
   */
  default Optional<String> findValueByPath(String pathString) {
    List<String> path = Splitter.on('.').omitEmptyStrings().splitToList(pathString);
    return getProperties().stream()
        .flatMap(FeatureVersionTimeMap::walk)
        .filter(p -> Objects.equals(p.getPropertyPath(), path))
        .findFirst()
        .flatMap(p -> walk(p).map(PropertyTimeMap::getValue).filter(Objects::nonNull).findFirst());
  }

  private Optional<PropertyTimeMap> findProperty(
      java.util.function.Predicate<FeatureSchema> match) {
    return getProperties().stream()
        .flatMap(FeatureVersionTimeMap::walk)
        .filter(p -> p.getSchema().filter(match).isPresent())
        .filter(p -> Objects.nonNull(p.getValue()))
        .filter(p -> Objects.nonNull(parse(p.getValue())))
        .findFirst();
  }

  private static boolean isDate(Optional<PropertyTimeMap> property) {
    return property
        .flatMap(PropertyTimeMap::getSchema)
        .filter(schema -> schema.getType() == SchemaBase.Type.DATE)
        .isPresent();
  }

  private static Stream<PropertyTimeMap> walk(PropertyTimeMap p) {
    return Stream.concat(
        Stream.of(p), p.getNestedProperties().stream().flatMap(FeatureVersionTimeMap::walk));
  }

  // The start/end properties may be DATETIME or DATE; a date is interpreted as start of day UTC,
  // matching the temporal-extent and link-role handling in the provider pipeline.
  private static Instant parse(String value) {
    try {
      TemporalAccessor ta =
          DateTimeFormatter.ofPattern("yyyy-MM-dd[['T'][' ']HH:mm:ss[.SSS]][X]")
              .parseBest(value, OffsetDateTime::from, LocalDateTime::from, LocalDate::from);
      if (ta instanceof OffsetDateTime) {
        return ((OffsetDateTime) ta).toInstant();
      } else if (ta instanceof LocalDateTime) {
        return ((LocalDateTime) ta).toInstant(ZoneOffset.UTC);
      }
      return ((LocalDate) ta).atStartOfDay(ZoneOffset.UTC).toInstant();
    } catch (Throwable ignore) {
      return null;
    }
  }
}
