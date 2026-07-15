/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.profile.crs.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.profile.crs.domain.ImmutableProfileCrsConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;

/**
 * @title Profile - Position CRS
 * @langEn Profiles for positions stored in a reference system that differs from the storage CRS.
 * @langDe Profile für Positionen, die in einem anderen Referenzsystem als dem Speicher-CRS
 *     gespeichert sind.
 * @scopeEn If the feature schema includes at least one geometry property with a `variants`
 *     declaration, one of two profiles can be used to select the representation of these positions
 *     in the response. With "crs-original", positions are represented as recorded: in their
 *     original reference system, identified by the stored verbatim CRS identifier, unaffected by
 *     the `crs` query parameter. This includes positions in reference systems that cannot be
 *     expressed as the CRS of the response, such as realizations that map to the same coordinate
 *     reference system or 1D vertical reference systems.
 *     <p>With "crs-requested" (the default), the position of the primary geometry property is
 *     returned in the requested CRS in all feature encodings; a feature whose position cannot be
 *     represented in the requested CRS (for example, a position in a 1D vertical reference system)
 *     is returned without a geometry.
 *     <p>The profile is supported by GML (the position element carries the original identifier as
 *     `srsName`, 1D positions are encoded with `srsDimension="1"`) and by GeoJSON with the JSON-FG
 *     extensions (`place` carries the original position with the identifier in `coordRefSys`; for a
 *     1D position, the vertical coordinate and the identifier appear in `properties`). Feature
 *     encodings that cannot represent positions in other reference systems (for example, plain
 *     GeoJSON) ignore the profile.
 * @scopeDe Wenn das Feature-Schema mindestens eine Geometrieeigenschaft mit einer
 *     `variants`-Deklaration enthält, kann eines von zwei Profilen verwendet werden, um die
 *     Darstellung dieser Positionen in der Antwort auszuwählen. Mit "crs-original" werden die
 *     Positionen wie erfasst dargestellt: in ihrem ursprünglichen Referenzsystem, identifiziert
 *     durch die unverändert gespeicherte CRS-Kennung, unabhängig vom Query-Parameter `crs`. Dies
 *     schließt Positionen in Referenzsystemen ein, die nicht als CRS der Antwort ausgedrückt werden
 *     können, etwa Realisierungen, die auf dasselbe Koordinatenreferenzsystem abgebildet werden,
 *     oder eindimensionale Höhenreferenzsysteme.
 *     <p>Mit "crs-requested" (dem Default) wird die Position der Haupt-Geometrieeigenschaft in
 *     allen Feature-Kodierungen im angeforderten CRS zurückgegeben; ein Feature, dessen Position
 *     nicht im angeforderten CRS dargestellt werden kann (zum Beispiel eine Position in einem
 *     eindimensionalen Höhenreferenzsystem), wird ohne Geometrie zurückgegeben.
 *     <p>Das Profil wird von GML unterstützt (das Positionselement führt die ursprüngliche Kennung
 *     als `srsName`, 1D-Positionen werden mit `srsDimension="1"` kodiert) sowie von GeoJSON mit den
 *     JSON-FG-Erweiterungen (`place` enthält die ursprüngliche Position mit der Kennung in
 *     `coordRefSys`; bei einer 1D-Position erscheinen die Höhenkoordinate und die Kennung in
 *     `properties`). Feature-Kodierungen, die Positionen in anderen Referenzsystemen nicht
 *     darstellen können (zum Beispiel reines GeoJSON), ignorieren das Profil.
 * @ref:cfg {@link de.ii.ogcapi.profile.crs.domain.ProfileCrsConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.profile.crs.domain.ImmutableProfileCrsConfiguration}
 */
@Singleton
@AutoBind
public class ProfileCrsBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_LDPROXY);
  public static final Optional<ExternalDocumentation> SPEC = Optional.empty();

  @Inject
  public ProfileCrsBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableProfileCrsConfiguration.Builder().enabled(false).build();
  }
}
