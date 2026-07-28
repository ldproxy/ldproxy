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
 * @langEn Profiles for the reference system of the positions in the response.
 * @langDe Profile für das Referenzsystem der Positionen in der Antwort.
 * @scopeEn One of two profiles can be used to select the reference system of the positions in the
 *     response.
 *     <p>With "crs-original", positions are represented as recorded. A position of a geometry
 *     property with a `crsVariants` declaration in the provider schema is returned in its original
 *     reference system, identified by the stored verbatim CRS identifier. This includes positions
 *     in reference systems that cannot be expressed as the CRS of the response, such as
 *     realizations that map to the same coordinate reference system or 1D vertical reference
 *     systems. Every other position is returned in the CRS in which the feature provider stores it,
 *     that is, in the storage CRS of the collection.
 *     <p>With "crs-requested" (the default), the position of the primary geometry property is
 *     returned in the requested CRS in all feature encodings; a feature whose position cannot be
 *     represented in the requested CRS (for example, a position in a 1D vertical reference system)
 *     is returned without a geometry.
 *     <p>The CRS that "crs-original" selects is a fallback for the default CRS of the API: if the
 *     request includes a `crs` parameter, the positions are returned in that CRS, except for the
 *     positions of a geometry property with a `crsVariants` declaration, which are always returned
 *     as recorded. In the HTML representation the profile is ignored, as is the `crs` parameter.
 *     <p>The original reference system of a position of a geometry property with a `crsVariants`
 *     declaration is represented in GML (the position element carries the original identifier as
 *     `srsName`, 1D positions are encoded with `srsDimension="1"`) and in GeoJSON with the JSON-FG
 *     extensions (`place` carries the original position with the identifier in `coordRefSys`; for a
 *     1D position, the vertical coordinate and the identifier appear in `properties`). Feature
 *     encodings that cannot represent positions in other reference systems (for example, plain
 *     GeoJSON) return such positions in the CRS of the response.
 * @scopeDe Es kann eines von zwei Profilen verwendet werden, um das Referenzsystem der Positionen
 *     in der Antwort auszuwählen.
 *     <p>Mit "crs-original" werden die Positionen wie erfasst dargestellt. Eine Position einer
 *     Geometrieeigenschaft mit einer `crsVariants`-Deklaration im Provider-Schema wird in ihrem
 *     ursprünglichen Referenzsystem zurückgegeben, identifiziert durch die unverändert gespeicherte
 *     CRS-Kennung. Dies schließt Positionen in Referenzsystemen ein, die nicht als CRS der Antwort
 *     ausgedrückt werden können, etwa Realisierungen, die auf dasselbe Koordinatenreferenzsystem
 *     abgebildet werden, oder eindimensionale Höhenreferenzsysteme. Alle anderen Positionen werden
 *     in dem CRS zurückgegeben, in dem der Feature-Provider sie speichert, also im Speicher-CRS der
 *     Collection.
 *     <p>Mit "crs-requested" (dem Default) wird die Position der Haupt-Geometrieeigenschaft in
 *     allen Feature-Kodierungen im angeforderten CRS zurückgegeben; ein Feature, dessen Position
 *     nicht im angeforderten CRS dargestellt werden kann (zum Beispiel eine Position in einem
 *     eindimensionalen Höhenreferenzsystem), wird ohne Geometrie zurückgegeben.
 *     <p>Das CRS, das "crs-original" auswählt, ist ein Fallback für das Standard-CRS der API: Wenn
 *     die Anfrage einen `crs`-Parameter enthält, werden die Positionen in diesem CRS zurückgegeben,
 *     mit Ausnahme der Positionen einer Geometrieeigenschaft mit einer `crsVariants`-Deklaration,
 *     die immer wie erfasst zurückgegeben werden. In der HTML-Ausgabe wird das Profil ignoriert,
 *     ebenso wie der `crs`-Parameter.
 *     <p>Das ursprüngliche Referenzsystem einer Position einer Geometrieeigenschaft mit einer
 *     `crsVariants`-Deklaration wird in GML (das Positionselement führt die ursprüngliche Kennung
 *     als `srsName`, 1D-Positionen werden mit `srsDimension="1"` kodiert) sowie in GeoJSON mit den
 *     JSON-FG-Erweiterungen (`place` enthält die ursprüngliche Position mit der Kennung in
 *     `coordRefSys`; bei einer 1D-Position erscheinen die Höhenkoordinate und die Kennung in
 *     `properties`) dargestellt. Feature-Kodierungen, die Positionen in anderen Referenzsystemen
 *     nicht darstellen können (zum Beispiel reines GeoJSON), geben diese Positionen im CRS der
 *     Antwort zurück.
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
