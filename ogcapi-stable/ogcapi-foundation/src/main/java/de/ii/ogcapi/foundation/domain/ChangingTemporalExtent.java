/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import de.ii.xtraplatform.entities.domain.ChangingValue;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface ChangingTemporalExtent extends ChangingValue<TemporalExtent> {

  static ChangingTemporalExtent of(TemporalExtent interval) {
    return new ImmutableChangingTemporalExtent.Builder()
        .value(
            Objects.requireNonNullElse(interval, TemporalExtent.of((String) null, (String) null)))
        .build();
  }

  @Override
  default Optional<ChangingValue<TemporalExtent>> updateWith(ChangingValue<TemporalExtent> delta) {
    TemporalExtent deltaExtent = delta.getValue();
    Instant currentStart = getValue().getStartInstant();
    Instant currentEnd = getValue().getEndInstant();
    Instant deltaStart = deltaExtent.getStartInstant();
    Instant deltaEnd = deltaExtent.getEndInstant();

    if (!Objects.requireNonNullElse(currentStart, Instant.MIN)
            .isAfter(Objects.requireNonNullElse(deltaStart, Instant.MIN))
        && !Objects.requireNonNullElse(currentEnd, Instant.MAX)
            .isBefore(Objects.requireNonNullElse(deltaEnd, Instant.MAX))) {
      return Optional.empty();
    }

    return Optional.of(
        ChangingTemporalExtent.of(
            TemporalExtent.of(
                currentStart == null || deltaStart == null
                    ? null
                    : (currentStart.isBefore(deltaStart) ? currentStart : deltaStart),
                currentEnd == null || deltaEnd == null
                    ? null
                    : (currentEnd.isAfter(deltaEnd) ? currentEnd : deltaEnd))));
  }
}
