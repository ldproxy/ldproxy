/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.domain;

import de.ii.xtraplatform.features.domain.FeatureBase;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.immutables.value.Value;

/**
 * Per-version POJO populated by {@link FeatureEncoderTimeMap} from the underlying feature stream.
 * Only the primary temporal interval is exposed — the encoder restricts the {@code FeatureQuery} to
 * the start and end properties, so other properties never enter the pipeline. The start and end
 * values may sit under a parent OBJECT (e.g. {@code lebenszeitintervall.beginnt}), so the accessors
 * walk the full property subtree.
 */
@Value.Modifiable
@Value.Style(set = "*")
public interface FeatureVersionTimeMap extends FeatureBase<PropertyTimeMap, FeatureSchema> {

  @Value.Lazy
  default Optional<Instant> getStart() {
    return findValue(SchemaBase::isPrimaryIntervalStart);
  }

  @Value.Lazy
  default Optional<Instant> getEnd() {
    return findValue(SchemaBase::isPrimaryIntervalEnd);
  }

  private Optional<Instant> findValue(java.util.function.Predicate<FeatureSchema> match) {
    return getProperties().stream()
        .flatMap(FeatureVersionTimeMap::walk)
        .filter(p -> p.getSchema().filter(match).isPresent())
        .map(PropertyTimeMap::getValue)
        .filter(Objects::nonNull)
        .map(FeatureVersionTimeMap::parse)
        .filter(Objects::nonNull)
        .findFirst();
  }

  private static Stream<PropertyTimeMap> walk(PropertyTimeMap p) {
    return Stream.concat(
        Stream.of(p), p.getNestedProperties().stream().flatMap(FeatureVersionTimeMap::walk));
  }

  private static Instant parse(String value) {
    try {
      return Instant.parse(value);
    } catch (Throwable ignore) {
      return null;
    }
  }
}
