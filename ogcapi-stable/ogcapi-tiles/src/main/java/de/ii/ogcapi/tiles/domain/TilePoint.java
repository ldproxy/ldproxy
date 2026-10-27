/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(builder = "new")
@JsonDeserialize(builder = ImmutableTilePoint.Builder.class)
public interface TilePoint {

  @SuppressWarnings("UnstableApiUsage")
  Funnel<TilePoint> FUNNEL =
      (from, into) -> {
        from.getCoordinates().forEach(into::putDouble);
        from.getTileMatrix().ifPresent(val -> into.putString(val, StandardCharsets.UTF_8));
        into.putString(from.getCrs(), StandardCharsets.UTF_8);
      };

  List<Double> getCoordinates();

  Optional<String> getTileMatrix();

  @Value.Default
  default String getCrs() {
    return OgcCrs.CRS84.toUriString();
  }
}
