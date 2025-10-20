/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.jsonfg.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.jsonfg.domain.ImmutableJsonFgConfiguration.Builder;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Features - JSON-FG
 * @langEn Encode features as JSON-FG.
 * @langDe Kodierung von Features als JSON-FG.
 * @scopeEn GeoJSON is a popular encoding for feature data. It is the default encoding for features
 *     in ldproxy. However, GeoJSON has intentional restrictions that prevent or limit its use in
 *     certain contexts. For example, GeoJSON is restricted to WGS 84 coordinates, does not support
 *     volumetric geometries and has no concept of classifying features according to their type.
 *     <p>OGC Features and Geometries JSON (JSON-FG) is an OGC candidate standard for GeoJSON
 *     extensions that provide standard ways to support such requirements.
 *     <p>The role `PRIMARY_GEOMETRY` is used to select the primary spatial property for the "place"
 *     or "geometry" member.
 *     <p>For the JSON-FG+ profile, that is, with geometries in both "place" and "geometry", a
 *     separate spatial property has to be specified with the role `SECONDARY_GEOMETRY` which will
 *     be represented in OGC:CRS84/OGC:CRS84h in the "geometry" member. That property is not
 *     represented, if the profile is not JSON-FG+.
 *     <p>The roles `PRIMARY_INSTANT`, `PRIMARY_INTERVAL_START`, and `PRIMARY_INTERVAL_END` are used
 *     to select the temporal properties for the "time" member.
 *     <p>The role `TYPE` can be used to annotate a property that contains the type of the feature.
 *     The property value will be represented in the "type" member of the feature.
 *     <p>The three GeoJSON profiles specified in JSON-FG are supported. The GeoJSON profiles only
 *     apply when the response media type is "application/geo+json".
 *     <p><code>
 * - The default profile "rfc7946" returns GeoJSON without any JSON-FG extensions.
 * - The profile "jsonfg" returns GeoJSON with all applicable JSON-FG extensions.
 * - The profile "jsonfg-plus" returns GeoJSON with JSON-FG extensions with the additional
 *     constraint that the "geometry" member is not `null` to support GeoJSON clients unaware of
 *     JSON-FG. The profile is only enabled, if the schema contains a property with the role `SECONDARY_GEOMETRY`.
 * </code>
 * @scopeDe GeoJSON ist eine beliebte Kodierung für Features. Es ist die Standardkodierung für
 *     Features in ldproxy. GeoJSON hat jedoch bewusste Einschränkungen, die seine Verwendung unter
 *     Umständen verhindern oder einschränken. So ist GeoJSON beispielsweise auf WGS 84-Koordinaten
 *     beschränkt, unterstützt keine volumetrischen Geometrien und hat kein Konzept zur
 *     Klassifizierung von Features nach ihrem Typ.
 *     <p>OGC Features and Geometries JSON (JSON-FG) ist ein Entwurf für einen OGC-Standard für
 *     GeoJSON-Erweiterungen, die Standardwege zur Unterstützung solcher Anforderungen bieten.
 *     <p>Die Rolle `PRIMARY_GEOMETRY` dient zur Auswahl der primären räumlichen Eigenschaft für das
 *     Mitglied "place" oder "geometry".
 *     <p>Für das Profil JSON-FG+, d. h. mit Geometrien sowohl in "place" als auch in "geometry",
 *     muss eine separate räumliche Eigenschaft mit der Rolle `SECONDARY_GEOMETRY` angegeben werden,
 *     die in OGC:CRS84 oder OGC:CRS84h im Element "geometry" dargestellt wird. Diese Eigenschaft
 *     wird nicht dargestellt, wenn das Profil nicht JSON-FG+ ist.
 *     <p>Die Rollen `PRIMARY_INSTANT`, `PRIMARY_INTERVAL_START` und `PRIMARY_INTERVAL_END` werden
 *     zur Auswahl der zeitlichen Eigenschaften für das Mitglied "time" verwendet.
 *     <p>Die Rolle `TYPE` kann verwendet werden, um eine Eigenschaft, die den Typ des Features
 *     enthält, mit Anmerkungen zu versehen. Der Wert der Eigenschaft wird im Member "type" des
 *     Features dargestellt.
 *     <p>Die drei GeoJSON-Profile gemäß der JSON-FG-Spezifikation werden unterstützt. Die
 *     GeoJSON-Profile gelten nur, wenn der Media Type der Antwort "application/geo+json" ist.
 *     <p><code>
 * - Das Standardprofil "rfc7946" liefert GeoJSON ohne jegliche JSON-FG-Erweiterungen.
 * - Das Profil "jsonfg" liefert GeoJSON mit allen anwendbaren JSON-FG-Erweiterungen.
 * - Das Profil "jsonfg-plus" liefert GeoJSON mit JSON-FG-Erweiterungen mit der zusätzlichen
 *     Einschränkung, dass das Element "geometry" nicht `null` ist, um GeoJSON-Clients zu
 *     unterstützen, die JSON-FG nicht kennen. Das Profil ist nur aktiv, wenn eine Eigenschaft mit der Rolle `SECONDARY_GEOMETRY` angegeben ist.
 * </code>
 * @conformanceEn The building block implements the Requirements Classes "Core", "Polyhedra",
 *     "Circular Arcs", "Feature Types and Schemas", "GeoJSON Profiles", and "JSON-FG in Web APIs"
 *     of [JSON-FG 0.3.0
 *     (DRAFT)](https://github.com/opengeospatial/ogc-feat-geo-json/releases/tag/v0.3.0). The
 *     implementation may change as the draft can evolve during the standardization process.
 * @conformanceDe Der Baustein implementiert die Requirements Classes "Core", "Polyhedra", "Circular
 *     Arcs", "Feature Types and Schemas", "GeoJSON Profiles" und "JSON-FG in Web APIs" aus [JSON-FG
 *     0.3.0 (DRAFT)](https://github.com/opengeospatial/ogc-feat-geo-json/releases/tag/v0.3.0). Die
 *     Implementierung kann sich im Zuge der weiteren Standardisierung der Spezifikation noch
 *     ändern.
 * @ref:cfg {@link de.ii.ogcapi.features.jsonfg.domain.JsonFgConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.features.jsonfg.domain.ImmutableJsonFgConfiguration}
 * @since v3.1
 */
@Singleton
@AutoBind
public class JsonFgBuildingBlock implements ApiBuildingBlock, ConformanceClass {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://github.com/opengeospatial/ogc-feat-geo-json/releases/tag/v0.3.0",
              "OGC Features and Geometries JSON - Part 1: Core (DRAFT)"));

  @Inject
  public JsonFgBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new Builder().enabled(false).supportPlusProfile(true).build();
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return List.of("http://www.opengis.net/spec/json-fg-1/0.3/conf/api");
  }
}
