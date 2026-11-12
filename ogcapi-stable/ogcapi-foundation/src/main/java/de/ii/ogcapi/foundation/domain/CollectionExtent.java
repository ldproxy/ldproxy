/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.features.domain.Extent;
import de.ii.xtraplatform.features.domain.ImmutableExtent;
import de.ii.xtraplatform.features.domain.ImmutableSpatialExtent;
import java.util.Objects;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * @langEn Deprecated, use `extent` in the building block `FEATURES_CORE` of the API or the
 *     collection instead. A value set here is used for that option unless it is set there, and
 *     `xtracfg upgrade` moves it there.
 *     <p>A fixed spatial (`spatial`) and/or temporal (`temporal`) extent; a part that is not set is
 *     computed from the data. `spatialComputed` and `temporalComputed` are ignored.
 * @langDe Veraltet, stattdessen `extent` im Baustein `FEATURES_CORE` der API oder der Collection
 *     verwenden. Ein hier gesetzter Wert wird für diese Option verwendet, wenn sie dort nicht
 *     gesetzt ist, und `xtracfg upgrade` verschiebt ihn dorthin.
 *     <p>Eine feste räumliche (`spatial`) und/oder zeitliche (`temporal`) Ausdehnung; ein nicht
 *     gesetzter Teil wird aus den Daten berechnet. `spatialComputed` und `temporalComputed` werden
 *     ignoriert.
 * @default
 */
@Deprecated(since = "4.9", forRemoval = true)
@Value.Immutable
@JsonDeserialize(builder = ImmutableCollectionExtent.Builder.class)
public interface CollectionExtent {

  /**
   * @langEn West- and east-bound longitude (`xmin`, `xmax`), south- and north-bound latitude
   *     (`ymin`, `ymax`) of the data. Optionally, minimum and maximum elevation (`zmin`, `zmax`)
   *     can be provided, too.
   * @langDe Längengrad am westlichen und östlichen Ende (`xmin`, `xmax`), Breite am südlichen und
   *     nördlichen Ende (`ymin`, `ymax`) der Daten. Optional können auch die minimale und maximale
   *     Höhe (`zmin`, `zmax`) angegeben werden.
   * @default
   * @example { "xmin": -180, "ymin": -90, "xmax": 180, "ymax": 90 }
   */
  Optional<BoundingBox> getSpatial();

  /**
   * @deprecated Will be removed. The extent is computed from the data, if no value is set.
   */
  @Deprecated
  Optional<Boolean> getSpatialComputed();

  /**
   * @langEn `start` and `end` of the temporal extent of the data, as date (`yyyy-MM-dd`), date-time
   *     with offset or Unix timestamp in milliseconds. A missing value is an unbounded start or
   *     end.
   * @langDe Beginn (`start`) und Ende (`end`) der zeitlichen Ausdehnung der Daten, als Datum
   *     (`yyyy-MM-dd`), Zeitstempel mit Offset oder Unix Timestamp in Millisekunden. Eine fehlende
   *     Angabe steht für einen unbegrenzten Beginn oder ein unbegrenztes Ende.
   * @default
   */
  Optional<TemporalExtent> getTemporal();

  /**
   * @deprecated Will be removed. The extent is computed from the data, if no value is set.
   */
  @Deprecated
  Optional<Boolean> getTemporalComputed();

  /**
   * The extent in the format of the option `extent` of the building blocks; the computed flags are
   * ignored, the spatial extent is taken as CRS84, as documented for this format.
   */
  default Extent toExtent() {
    ImmutableExtent.Builder builder = ImmutableExtent.builder();
    getSpatial()
        .ifPresent(
            bbox ->
                builder.spatial(
                    ImmutableSpatialExtent.builder()
                        .xmin(bbox.getXmin())
                        .ymin(bbox.getYmin())
                        .zmin(bbox.getZmin())
                        .xmax(bbox.getXmax())
                        .ymax(bbox.getYmax())
                        .zmax(bbox.getZmax())
                        .build()));
    getTemporal()
        .filter(
            temporal ->
                Objects.nonNull(temporal.getStartInstant())
                    || Objects.nonNull(temporal.getEndInstant()))
        .ifPresent(
            temporal ->
                builder.temporal(
                    de.ii.xtraplatform.features.domain.ImmutableTemporalExtent.builder()
                        .start(Objects.toString(temporal.getStartInstant(), null))
                        .end(Objects.toString(temporal.getEndInstant(), null))
                        .build()));
    return builder.build();
  }
}
