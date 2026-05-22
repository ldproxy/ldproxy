/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import de.ii.ogcapi.foundation.domain.TemporalExtent;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonDeserialize(builder = ImmutableOgcApiExtentTemporal.Builder.class)
@ApiInfo(schemaId = "TemporalExtent")
public interface OgcApiExtentTemporal {

  @SuppressWarnings("UnstableApiUsage")
  Funnel<OgcApiExtentTemporal> FUNNEL =
      (from, into) -> {
        into.putString(from.getTrs(), StandardCharsets.UTF_8);
        Arrays.stream(from.getInterval())
            .forEachOrdered(
                arr ->
                    Arrays.stream(arr)
                        .forEachOrdered(
                            val ->
                                into.putString(
                                    Objects.requireNonNullElse(val, "..").toString(),
                                    StandardCharsets.UTF_8)));
      };

  Instant[][] getInterval();

  String getTrs();

  static OgcApiExtentTemporal of(TemporalExtent interval) {
    return ImmutableOgcApiExtentTemporal.builder()
        .interval(
            new Instant[][] {
              {
                (interval.getStart() != null)
                    ? Instant.from(DateTimeFormatter.ISO_INSTANT.parse(interval.getStart()))
                        .truncatedTo(ChronoUnit.SECONDS)
                    : null,
                (interval.getEnd() != null)
                    ? Instant.from(DateTimeFormatter.ISO_INSTANT.parse(interval.getEnd()))
                        .truncatedTo(ChronoUnit.SECONDS)
                    : null
              }
            })
        .trs("http://www.opengis.net/def/uom/ISO-8601/0/Gregorian")
        .build();
  }

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default String getFirstIntervalIso8601() {
    return String.format(
        "%s/%s",
        Objects.requireNonNullElse(getInterval()[0][0], ".."),
        Objects.requireNonNullElse(getInterval()[0][1], ".."));
  }
}
