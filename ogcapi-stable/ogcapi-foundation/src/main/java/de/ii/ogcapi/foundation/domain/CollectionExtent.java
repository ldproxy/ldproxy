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
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.docs.DocIgnore;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * @langEn By default, spatial and temporal extents of the collections are derived from the data
 *     during startup. The extent of the dataset is always the union of the collection extents. For
 *     large datasets the automated derivation will slow down the startup of the API.
 *     <p>If the spatial extent should not be derived from the data source on startup, set
 *     `spatialComputed` to `false`.
 *     <p>If the temporal extent should not be derived from the data source on startup, set
 *     `temporalComputed` to `false`.
 *     <p>As an alternative, a default value for the spatial (`spatial`) and/or temporal
 *     (`temporal`) extent for each collection can be set.
 *     <p><code>
 * - Required keys for spatial extents (all values in `CRS84`): `xmin` (western longitude), `ymin` (southern latitude), `xmax` (eastern longitude), `ymax` (northern latitude).
 * - Required keys for temporal extents (all values in milliseconds since 1 January 1970): `start`, `end`.
 *     </code>
 * @langDe Es kann ein Standardwert für die räumliche (`spatial`) und/oder zeitliche (`temporal`)
 *     Ausdehnung der Daten angeben werden, die bei den Objektarten verwendet wird, wenn dort keine
 *     anderslautende Ausdehnung spezifiziert wird und die automatische Ableitung deaktiviert ist.
 *     <p>Für die räumliche Ausdehnung sind die folgenden Eigenschaften anzugeben (alle Angaben im
 *     Koordinatenreferenzsystem WGS 84): `xmin` (westlicher Längengrad), `ymin` (südlicher
 *     Breitengrad), `xmax` (östlicher Längengrad), `ymax` (nördlicher Breitengrad).
 *     <p>Für die zeitliche Ausdehnung sind die folgenden Eigenschaften anzugeben (alle Angaben in
 *     Millisekunden seit dem 1.1.1970): `start`, `end`.
 *     <p>Es handelt sich hierbei nicht um die Ausdehnung des Datensatzes insgesamt, dieser wird
 *     stets automatisch aus den Ausdehnungen der einzelnen Objektarten ermittelt.
 *     <p>Soll die räumliche Ausdehnung nicht automatisch aus den Daten der Objektarten beim Start
 *     von ldproxy ermittelt werden, kann `spatialComputed` mit dem Wert `false` angegeben werden.
 *     <p>Soll die zeitliche Ausdehnung nicht automatisch aus den Daten einer Objektart beim Start
 *     von ldproxy ermittelt werden, kann `temporalComputed` mit dem Wert `false` angegeben werden.
 *     <p>Bei großen Datenmengen verzögert die automatische Bestimmung die Zeitdauer, bis die API
 *     verfügbar ist.
 * @default
 */
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
   * @langEn The spatial extent of each collection is automatically dervied from the data during the
   *     startup of the API. If the collection has no temporal properties, the collection will not
   *     have a temporal extent.
   * @langDe Die räumliche Ausdehnung jeder Collection wird beim Starten der API automatisch aus den
   *     Daten abgeleitet. Wenn die Collection keine räumlichen Eigenschaften hat, hat die
   *     Collection keine räumliche Ausdehnung.
   * @default true
   */
  Optional<Boolean> getSpatialComputed();

  /**
   * @langEn `start` and `end` of the temporal extent of the data, specified as Unix timestamp in
   *     milliseconds. Unspecified values indicate an unbounded interval end.
   * @langDe Beginn (`start`) und Ende (`end`) der zeitlichen Ausdehnung der Daten. Die Angabe
   *     erfolgt als Unix Timestamp in Millisekunden. Fehlende Angaben stehen für ein unbegrenztes
   *     Intervall.
   * @default
   */
  Optional<TemporalExtent> getTemporal();

  /**
   * @langEn The temporal extent of each collection is automatically dervied from the data during
   *     the startup of the API. If the collection has no temporal properties, the collection will
   *     not have a temporal extent.
   * @langDe Die zeitliche Ausdehnung jeder Collection wird beim Starten der API automatisch aus den
   *     Daten abgeleitet. Wenn die Collection keine zeitlichen Eigenschaften hat, hat die
   *     Collection keine zeitliche Ausdehnung.
   * @default true
   */
  Optional<Boolean> getTemporalComputed();

  @DocIgnore
  @JsonIgnore
  @Value.Lazy
  default boolean isSpatialComputed() {
    return getSpatialComputed().orElse(false);
  }

  @DocIgnore
  @JsonIgnore
  @Value.Lazy
  default boolean isTemporalComputed() {
    return getTemporalComputed().orElse(false);
  }
}
