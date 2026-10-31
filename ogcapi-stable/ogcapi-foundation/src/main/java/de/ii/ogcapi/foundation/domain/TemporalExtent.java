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
import java.text.DateFormat;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;
import org.immutables.value.Value;
import org.threeten.extra.Interval;

/**
 * Start and end accept the same formats as the extent in the feature provider: a date
 * (`yyyy-MM-dd`, UTC, the end is inclusive) or a date-time with offset. Epoch milliseconds are
 * still accepted, since configurations written for earlier versions use them.
 */
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
    return of(
        interval.isUnboundedStart() ? null : interval.getStart(),
        interval.isUnboundedEnd() ? null : interval.getEnd());
  }

  @Nullable
  String getStart();

  @Nullable
  String getEnd();

  @Value.Derived
  @JsonIgnore
  @Nullable
  default Instant getStartInstant() {
    return getStart() == null ? null : parse(getStart(), LocalTime.MIN);
  }

  @Value.Derived
  @JsonIgnore
  @Nullable
  default Instant getEndInstant() {
    return getEnd() == null ? null : parse(getEnd(), LocalTime.of(23, 59, 59));
  }

  private static Instant parse(String value, LocalTime timeOfDayForDates) {
    try {
      if (value.matches("-?\\d+")) {
        return Instant.ofEpochMilli(Long.parseLong(value));
      }
      if (!value.contains("T")) {
        return LocalDate.parse(value).atTime(timeOfDayForDates).toInstant(ZoneOffset.UTC);
      }
      return OffsetDateTime.parse(value).toInstant();
    } catch (DateTimeException | NumberFormatException e) {
      throw new IllegalStateException(
          String.format(
              "TemporalExtent: '%s' is neither a date (yyyy-MM-dd) nor a date-time with offset.",
              value),
          e);
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
