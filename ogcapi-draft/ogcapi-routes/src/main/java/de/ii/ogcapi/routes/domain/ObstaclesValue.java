/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.routes.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import com.google.common.hash.Funnel;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true, deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableObstaclesValue.Builder.class)
public abstract class ObstaclesValue {
  public final String getType() {
    return "MultiPolygon";
  }

  public abstract List<List<List<List<Float>>>> getCoordinates();

  @Value.Default
  public String getCoordRefSys() {
    return OgcCrs.CRS84_URI;
  }

  @Value.Check
  void check() {
    Preconditions.checkState(
        "MultiPolygon".equals(getType()),
        "WaypointsValue is not a MultiPolygon geometry. Found: {}.",
        getType());
    Preconditions.checkState(
        getCoordinates().size() >= 1,
        "At least one polygon is required. Found: {}.",
        getCoordinates().size());
    getCoordinates().stream()
        .flatMap(Collection::stream)
        .flatMap(Collection::stream)
        .forEach(
            position -> {
              Preconditions.checkState(
                  position.size() >= 2,
                  "At least two coordinates are required per position. Found: {}.",
                  position.size());
              Preconditions.checkState(
                  position.size() <= 3,
                  "At most three coordinates are required per position. Found: {}.",
                  position.size());
            });
    List<Integer> dimensions =
        getCoordinates().stream()
            .flatMap(Collection::stream)
            .flatMap(Collection::stream)
            .map(List::size)
            .distinct()
            .sorted()
            .collect(Collectors.toUnmodifiableList());
    Preconditions.checkState(
        dimensions.size() == 1,
        "All coordinates must have the same dimension. Found: {}.",
        dimensions);
    Preconditions.checkState(
        dimensions.stream().min(Comparator.naturalOrder()).orElse(2) >= 2,
        "At least two coordinates are required per position. Found: {}.",
        dimensions.stream().min(Comparator.naturalOrder()).get());
    Preconditions.checkState(
        dimensions.stream().max(Comparator.naturalOrder()).orElse(3) <= 3,
        "At most three coordinates are required per position. Found: {}.",
        dimensions.stream().max(Comparator.naturalOrder()).get());
  }

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<ObstaclesValue> FUNNEL =
      (from, into) -> {
        into.putString(from.getType(), StandardCharsets.UTF_8);
        from.getCoordinates().stream()
            .flatMap(Collection::stream)
            .flatMap(Collection::stream)
            .flatMap(Collection::stream)
            .forEach(into::putFloat);
        into.putString(from.getCoordRefSys(), StandardCharsets.UTF_8);
      };
}
