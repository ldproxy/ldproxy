/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import java.text.DateFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;
import org.immutables.value.Value;
import org.threeten.extra.Interval;

@Value.Immutable
@JsonDeserialize(builder = ImmutableTemporalExtent.Builder.class)
public interface TemporalExtent {

  static TemporalExtent of(String start, String end) {
    return new ImmutableTemporalExtent.Builder().start(start).end(end).build();
  }

  static TemporalExtent of(Instant start, Instant end) {
    return of(formatInstant(start), formatInstant(end));
  }

  static TemporalExtent of(Interval interval) {
    ImmutableTemporalExtent.Builder builder = new ImmutableTemporalExtent.Builder();
    if (!interval.isUnboundedStart()) {
      builder.start(DateTimeFormatter.ISO_INSTANT.format(interval.getStart()));
    }
    if (!interval.isUnboundedEnd()) {
      builder.end(DateTimeFormatter.ISO_INSTANT.format(interval.getEnd()));
    }
    return builder.build();
  }

  @Nullable
  String getStart();

  @Nullable
  String getEnd();

  @Value.Derived
  @JsonIgnore
  @Nullable
  default Instant getStartInstant() {
    return getStart() == null
        ? null
        : Instant.from(DateTimeFormatter.ISO_INSTANT.parse(getStart()));
  }

  @Value.Derived
  @JsonIgnore
  @Nullable
  default Instant getEndInstant() {
    return getEnd() == null ? null : Instant.from(DateTimeFormatter.ISO_INSTANT.parse(getEnd()));
  }

  @Value.Check
  default void validateIsoInstant() {
    if (getStart() != null) {
      validateIsoInstant(getStart(), "start");
    }
    if (getEnd() != null) {
      validateIsoInstant(getEnd(), "end");
    }
  }

  private static void validateIsoInstant(String value, String field) {
    Preconditions.checkState(
        !value.matches("^-?\\d+$"),
        "TemporalExtent: '%s' must be a UTC ISO-8601 instant string, not a numeric timestamp.",
        field);
    try {
      DateTimeFormatter.ISO_INSTANT.parse(value);
    } catch (DateTimeParseException e) {
      Preconditions.checkState(
          false,
          "TemporalExtent: '%s' is not a valid UTC ISO-8601 instant (yyyy-MM-ddTHH:mm:ss.SSSZ): %s",
          field,
          value);
    }
  }

  private static String formatInstant(Instant value) {
    return value == null ? null : DateTimeFormatter.ISO_INSTANT.format(value);
  }

  default String humanReadable(Locale locale) {
    DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);

    return String.format(
        "%s - %s",
        Optional.ofNullable(getStartInstant())
            .map(start -> df.format(Date.from(start)))
            .orElse(".."),
        Optional.ofNullable(getEndInstant()).map(end -> df.format(Date.from(end))).orElse(".."));
  }
}
