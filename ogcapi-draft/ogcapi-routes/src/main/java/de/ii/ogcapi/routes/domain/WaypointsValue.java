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
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true, deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableWaypointsValue.Builder.class)
public abstract class WaypointsValue {
  public final String getType() {
    return "MultiPoint";
  }

  public abstract List<List<Float>> getCoordinates();

  @Value.Default
  public String getCoordRefSys() {
    return OgcCrs.CRS84_URI;
  }

  @Value.Check
  void check() {
    Preconditions.checkState(
        "MultiPoint".equals(getType()),
        "WaypointsValue is not a MultiPoint geometry. Found: {}.",
        getType());
    Preconditions.checkState(
        getCoordinates().size() >= 2,
        "At least two waypoints are required. Found: {}.",
        getCoordinates().size());
    getCoordinates()
        .forEach(
            waypoint -> {
              Preconditions.checkState(
                  waypoint.size() >= 2,
                  "At least two coordinates are required per waypoint. Found: {}.",
                  waypoint.size());
              Preconditions.checkState(
                  waypoint.size() <= 3,
                  "At most three coordinates are required per waypoint. Found: {}.",
                  waypoint.size());
            });
  }

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<WaypointsValue> FUNNEL =
      (from, into) -> {
        into.putString(from.getType(), StandardCharsets.UTF_8);
        from.getCoordinates().stream().flatMap(Collection::stream).forEach(into::putFloat);
        into.putString(from.getCoordRefSys(), StandardCharsets.UTF_8);
      };
}
