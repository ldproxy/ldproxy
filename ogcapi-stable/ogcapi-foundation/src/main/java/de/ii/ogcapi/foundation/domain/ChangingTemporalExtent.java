/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import de.ii.xtraplatform.entities.domain.ChangingValue;
import java.util.Objects;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface ChangingTemporalExtent extends ChangingValue<TemporalExtent> {

  static ChangingTemporalExtent of(TemporalExtent interval) {
    return new ImmutableChangingTemporalExtent.Builder()
        .value(Objects.requireNonNullElse(interval, TemporalExtent.of(null, null)))
        .build();
  }

  @Override
  default Optional<ChangingValue<TemporalExtent>> updateWith(ChangingValue<TemporalExtent> delta) {
    TemporalExtent deltaExtent = delta.getValue();
    Long currentStart = getValue().getStart();
    Long currentEnd = getValue().getEnd();
    Long deltaStart = deltaExtent.getStart();
    Long deltaEnd = deltaExtent.getEnd();

    if (Objects.requireNonNullElse(currentStart, Long.MIN_VALUE)
            <= Objects.requireNonNullElse(deltaStart, Long.MIN_VALUE)
        && Objects.requireNonNullElse(currentEnd, Long.MAX_VALUE)
            >= Objects.requireNonNullElse(deltaEnd, Long.MAX_VALUE)) {
      return Optional.empty();
    }

    return Optional.of(
        ChangingTemporalExtent.of(
            TemporalExtent.of(
                currentStart == null || deltaStart == null
                    ? null
                    : Math.min(currentStart, deltaStart),
                currentEnd == null || deltaEnd == null ? null : Math.max(currentEnd, deltaEnd))));
  }
}
