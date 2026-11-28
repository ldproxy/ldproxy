/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonDeserialize(builder = ImmutableOgcApiExtentSpatial.Builder.class)
@ApiInfo(schemaId = "SpatialExtent")
public interface OgcApiExtentSpatial {

  @SuppressWarnings("UnstableApiUsage")
  Funnel<OgcApiExtentSpatial> FUNNEL =
      (from, into) -> {
        into.putString(from.getCrs(), StandardCharsets.UTF_8);
        Arrays.stream(from.getBbox())
            .forEachOrdered(arr -> Arrays.stream(arr).forEachOrdered(into::putDouble));
        from.getStorageCrsBbox()
            .ifPresent(
                bboxes ->
                    Arrays.stream(bboxes)
                        .forEachOrdered(arr -> Arrays.stream(arr).forEachOrdered(into::putDouble)));
      };

  double[][] getBbox();

  /**
   * The spatial extent in the storage CRS of the collection (`storageCrs`), in the axis order of
   * that CRS, as required by OGC API - Common - Part 2 if the storage CRS is not CRS84.
   */
  Optional<double[][]> getStorageCrsBbox();

  String getCrs();

  static OgcApiExtentSpatial of(BoundingBox bbox) {
    return of(bbox, Optional.empty());
  }

  static OgcApiExtentSpatial of(BoundingBox bbox, Optional<double[]> storageCrsBbox) {
    return ImmutableOgcApiExtentSpatial.builder()
        .bbox(new double[][] {bbox.toArray()})
        .storageCrsBbox(storageCrsBbox.map(storageBbox -> new double[][] {storageBbox}))
        .crs(bbox.is3d() ? OgcCrs.CRS84h_URI : OgcCrs.CRS84_URI)
        .build();
  }
}
