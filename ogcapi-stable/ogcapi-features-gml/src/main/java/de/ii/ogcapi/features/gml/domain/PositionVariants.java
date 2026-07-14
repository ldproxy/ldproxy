/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * Configuration of the CRS-specific position variants of one geometry property, see {@code
 * GmlConfiguration#getPositionVariants()}. All referenced properties are siblings of the geometry
 * property in the provider schema.
 */
@Value.Immutable
@Value.Style(builder = "new", deepImmutablesDetection = true)
@JsonDeserialize(builder = ImmutablePositionVariants.Builder.class)
public interface PositionVariants {

  /**
   * @langEn Maps a wire `srsName` to the geometry property that stores 2D/3D positions in that CRS.
   *     The property must declare the storage CRS via its `crs` option in the provider schema; the
   *     corresponding `srsNameMappings` entry resolves the srsName for decoding.
   * @langDe Bildet einen `srsName` auf die Geometrieeigenschaft ab, die 2D/3D-Positionen in diesem
   *     CRS speichert. Die Eigenschaft muss das Speicher-CRS über ihre `crs`-Option im
   *     Provider-Schema deklarieren; der zugehörige `srsNameMappings`-Eintrag löst den srsName beim
   *     Einlesen auf.
   */
  Map<String, String> getVariantProperties();

  /**
   * @langEn The FLOAT property that stores 1D positions (e.g. heights in a vertical reference
   *     system); required when `verticalSrsNames` is non-empty.
   * @langDe Die FLOAT-Eigenschaft, die 1D-Positionen speichert (z.B. Höhen in einem
   *     Höhenreferenzsystem); erforderlich, wenn `verticalSrsNames` nicht leer ist.
   */
  Optional<String> getVerticalProperty();

  /**
   * @langEn The wire `srsName` values of 1D (vertical) reference systems; positions with one of
   *     these are stored in `verticalProperty`.
   * @langDe Die `srsName`-Werte von 1D-Referenzsystemen (Höhenreferenzsystemen); Positionen mit
   *     einem dieser Werte werden in `verticalProperty` gespeichert.
   */
  List<String> getVerticalSrsNames();

  /**
   * @langEn The STRING property that stores the verbatim wire `srsName` for positions in a
   *     non-native CRS; the encoder reproduces it on output.
   * @langDe Die STRING-Eigenschaft, die den `srsName` für Positionen in einem nicht-nativen CRS
   *     unverändert speichert; bei der Ausgabe wird er daraus reproduziert.
   */
  Optional<String> getSrsNameProperty();
}
