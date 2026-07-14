/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(builder = "new", deepImmutablesDetection = true)
@JsonDeserialize(builder = ImmutableSrsNameMapping.Builder.class)
public interface SrsNameMapping {
  EpsgCrs getCrs();

  String getValue();

  /**
   * @langEn The difference between the false easting of the CRS and the false easting used by
   *     coordinates carrying this `srsName` on the wire. When non-zero, the difference is added to
   *     the easting (the first ordinate) on input and subtracted on output, so the stored
   *     coordinates conform to the CRS. Example: German Gauss-Krüger coordinates written without
   *     the zone prefix use a false easting of 500000, while EPSG:5677 (zone 3, E-N) defines
   *     3500000 — the difference is 3000000.
   * @langDe Die Differenz zwischen dem False Easting des CRS und dem False Easting der Koordinaten,
   *     die diesen `srsName` verwenden. Bei einem Wert ungleich 0 wird die Differenz beim Einlesen
   *     zum Rechtswert (der ersten Ordinate) addiert und bei der Ausgabe subtrahiert, sodass die
   *     gespeicherten Koordinaten dem CRS entsprechen. Beispiel: Gauß-Krüger-Koordinaten ohne
   *     Zonenkennzahl verwenden ein False Easting von 500000, EPSG:5677 (Zone 3, E-N) definiert
   *     3500000 — die Differenz beträgt 3000000.
   * @default 0
   */
  @Value.Default
  default double getFalseEastingDifference() {
    return 0;
  }
}
