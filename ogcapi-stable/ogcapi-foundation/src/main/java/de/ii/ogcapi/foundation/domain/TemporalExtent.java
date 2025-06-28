/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;
import org.immutables.value.Value;
import org.threeten.extra.Interval;

@Value.Immutable
@JsonDeserialize(builder = ImmutableTemporalExtent.Builder.class)
public interface TemporalExtent {

  static TemporalExtent of(Long start, Long end) {
    return new ImmutableTemporalExtent.Builder().start(start).end(end).build();
  }

  static TemporalExtent of(Interval interval) {
    ImmutableTemporalExtent.Builder builder = new ImmutableTemporalExtent.Builder();
    if (!interval.isUnboundedStart()) {
      builder.start(interval.getStart().toEpochMilli());
    }
    if (!interval.isUnboundedEnd()) {
      builder.end(interval.getEnd().toEpochMilli());
    }
    return builder.build();
  }

  @Value.Default
  @Nullable
  default Long getStart() {
    return null;
  }

  @Value.Default
  @Nullable
  default Long getEnd() {
    return null;
  }

  default String humanReadable(Locale locale) {
    DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);

    return String.format(
        "%s - %s",
        Optional.ofNullable(getStart()).map(start -> df.format(new Date(start))).orElse(".."),
        Optional.ofNullable(getEnd()).map(end -> df.format(new Date(end))).orElse(".."));
  }
}
