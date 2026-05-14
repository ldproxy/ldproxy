/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.gml.domain.ImmutableGmlConfiguration;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.Optional;

/**
 * @title Features - GML
 * @langEn Encode features as GML.
 * @langDe Kodierung von Features als GML.
 * @scopeEn For a WFS feature provider, the features are accessed as GML from the WFS and rewritten
 *     to the response. In case of *Features* the root element is `sf:FeatureCollection`.
 *     <p>For a SQL feature provider, the features are mapped to GML object and property elements
 *     based on the provider schema. A number of configuration options exist to control how the
 *     features are mapped to XML.
 *     <p>All configuration options of this building block except `gmlSfLevel` are only applicable
 *     for collections with a SQL feature provider. For collections with a WFS feature provider, all
 *     other configuration options are ignored.
 *     <p>The following descriptions all apply only to collections with a SQL feature provider:
 *     <p><code>
 * - The feature property with the role `ID` in the provider schema is mapped to the `gml:id`
 *   attribute of the feature. These properties must be a direct property of the feature type.
 *   If `gmlIdPrefix` is set, the prefix is prepended to every `gml:id` value to keep them
 *   valid XML IDs. If `appendTemporalSuffixToGmlId` is `true` and the request's `datetime`
 *   parameter is an interval, the value of the primary temporal property is appended to the
 *   `gml:id` formatted as `yyyyMMddTHHmmssX`.
 * - If `gmlIdentifier` is configured, a `gml:identifier` element is emitted as the first
 *   child of every feature, with the configured `codeSpace` attribute and the feature id
 *   (optionally substituted into `valueTemplate`) as text.
 * - Geometry properties are mapped to GML 3.2 elements depending on the geometry type:
 *   - `Point` → `gml:Point` (with `gml:pos`)
 *   - `MultiPoint` → `gml:MultiPoint`
 *   - `LineString` → `gml:LineString` (with `gml:posList`)
 *   - `CircularString` → `gml:Curve` with a `gml:Arc` segment (three control points) or
 *     `gml:ArcString` segment (more than three control points)
 *   - `CompoundCurve` → `gml:Curve` with multiple segments
 *   - `MultiLineString` / `MultiCurve` → `gml:MultiCurve`
 *   - `Polygon` / `CurvePolygon` → `gml:Polygon` with `gml:LinearRing` rings
 *   - `MultiPolygon` / `MultiSurface` → `gml:MultiSurface`
 *   - `PolyhedralSurface` → `gml:Solid` (when closed) or `gml:PolyhedralSurface` (when open)
 *   - `GeometryCollection` → `gml:MultiGeometry`
 *   With `useSurfaceAndCurve: true`, the following alternative encodings are used: simple
 *   line strings as `gml:Curve` with one `gml:LineStringSegment`; simple polygons (and
 *   `CurvePolygon`) as `gml:Surface` with one `gml:PolygonPatch`; compound curves as
 *   `gml:CompositeCurve`; and all rings inside polygons as `gml:Ring` with one or more
 *   `gml:LineStringSegment` curve members instead of `gml:LinearRing`. No `gml:id` is added
 *   to geometry elements unless `gmlIdOnGeometries: true`. The `srsName` attribute is set on
 *   every geometry; with `srsNameStyle: TEMPLATE`, the value is taken from `srsNameMappings`
 *   instead of the OGC URI form. With `srsDimension: true`, a `srsDimension` attribute is
 *   also added to `pos` and `posList`.
 * - Properties that are `OBJECT`s with object type `Link` will be mapped to a `gml:Reference`
 *   value with `xlink:href` and `xlink:title` attributes, if set. For feature-reference
 *   properties (declared with `refType`), `featureRefTemplate` can rewrite `xlink:href` into
 *   a URN-style identifier (e.g. `urn:adv:oid:{{value}}`).
 * - Properties that are `OBJECT`s with object type `Measure` will be mapped to a
 *   `gml:MeasureType` value. The object must have the properties `value` and `uom`, which
 *   both must be present in the data.
 * - Properties that are `FLOAT` or `INTEGER` values with a `unit` property in the provider
 *   schema are also mapped to a `gml:MeasureType` value. The value of `unit` is mapped to
 *   the `uom` attribute. With `uomStyle: TEMPLATE`, the unit string is resolved via
 *   `uomMappings`.
 * - Properties listed in `codelistProperties` are encoded as empty XML elements with
 *   `xlink:href` (constructed from `codelistUriTemplate`) and `xlink:title` (the codelist
 *   label, falling back to the raw value if no label is found), instead of writing the raw
 *   value as element text.
 * - Each property element is named after the property in the feature schema; with
 *   `useAlias: true`, properties that declare an `alias` are encoded under that alias
 *   instead. The element is placed in the namespace of its parent object type as declared by
 *   `objectTypeNamespaces`. An explicit `prefix:name` in the schema or a `rename`
 *   transformation takes precedence over both the inherited namespace and the alias.
 * - Properties listed in `valueWrap` have their scalar value wrapped in one or more nested
 *   XML elements declared in the configuration (outer to inner), with the value appearing
 *   inside the innermost wrapper — useful for application schemas that nest atomic values
 *   inside wrapper types.
 *     </code>
 * @scopeDe Bei einem WFS-Feature-Provider werden die Features als GML vom WFS abgerufen und in die
 *     Antwort umgeschrieben. Im Falle von *Features* ist das Wurzelelement `sf:FeatureCollection`.
 *     <p>Bei einem SQL-Feature-Provider werden die Features auf der Grundlage des Provider-Schemas
 *     auf GML-Objekt- und Eigenschaftselemente abgebildet. Es gibt eine Reihe von
 *     Konfigurationsoptionen, um zu steuern, wie die Merkmale auf XML abgebildet werden.
 *     <p>Alle Konfigurationsoptionen dieses Bausteins mit Ausnahme von "gmlSfLevel" sind nur für
 *     Collections mit einem SQL-Feature-Provider anwendbar. Für Collections mit einem
 *     WFS-Feature-Provider werden alle anderen Konfigurationsoptionen ignoriert.
 *     <p>Die folgenden Beschreibungen gelten alle nur für Collections mit einem
 *     SQL-Feature-Provider:
 *     <p><code>
 * - Die Feature-Eigenschaft mit der Rolle `ID` im Provider-Schema wird auf das Attribut `gml:id`
 *   des Features abgebildet. Diese Eigenschaften müssen eine direkte Eigenschaft des Featuretyps
 *   sein. Wenn `gmlIdPrefix` gesetzt ist, wird das Präfix jedem `gml:id`-Wert vorangestellt,
 *   um gültige XML-IDs zu gewährleisten. Wenn `appendTemporalSuffixToGmlId: true` gesetzt ist
 *   und der `datetime`-Parameter der Anfrage ein Intervall ist, wird der Wert der primären
 *   zeitlichen Eigenschaft im Format `yyyyMMddTHHmmssX` an die `gml:id` angehängt.
 * - Wenn `gmlIdentifier` konfiguriert ist, wird ein `gml:identifier`-Element als erstes
 *   Kindelement jedes Features ausgegeben, mit dem konfigurierten `codeSpace`-Attribut und
 *   der Feature-ID (optional eingesetzt in `valueTemplate`) als Textinhalt.
 * - Geometrieeigenschaften werden je nach Geometrietyp auf folgende GML-3.2-Elemente
 *   abgebildet:
 *   - `Point` → `gml:Point` (mit `gml:pos`)
 *   - `MultiPoint` → `gml:MultiPoint`
 *   - `LineString` → `gml:LineString` (mit `gml:posList`)
 *   - `CircularString` → `gml:Curve` mit einem `gml:Arc`-Segment (drei Kontrollpunkte) oder
 *     `gml:ArcString`-Segment (mehr als drei Kontrollpunkte)
 *   - `CompoundCurve` → `gml:Curve` mit mehreren Segmenten
 *   - `MultiLineString` / `MultiCurve` → `gml:MultiCurve`
 *   - `Polygon` / `CurvePolygon` → `gml:Polygon` mit `gml:LinearRing`-Ringen
 *   - `MultiPolygon` / `MultiSurface` → `gml:MultiSurface`
 *   - `PolyhedralSurface` → `gml:Solid` (wenn geschlossen) oder `gml:PolyhedralSurface`
 *     (wenn offen)
 *   - `GeometryCollection` → `gml:MultiGeometry`
 *   Mit `useSurfaceAndCurve: true` werden folgende alternative Kodierungen verwendet:
 *   einfache Linienzüge als `gml:Curve` mit einem `gml:LineStringSegment`; einfache Polygone
 *   (und `CurvePolygon`) als `gml:Surface` mit einem `gml:PolygonPatch`; CompoundCurves als
 *   `gml:CompositeCurve`; und alle Ringe innerhalb von Polygonen als `gml:Ring` mit einem
 *   oder mehreren `gml:LineStringSegment`-Curve-Membern statt `gml:LinearRing`. Das Attribut
 *   `gml:id` wird den Geometrieelementen nicht hinzugefügt, sofern nicht
 *   `gmlIdOnGeometries: true` gesetzt ist. Das Attribut `srsName` wird in jeder Geometrie
 *   gesetzt; mit `srsNameStyle: TEMPLATE` wird der Wert aus `srsNameMappings` übernommen
 *   statt der OGC-URI-Form. Mit `srsDimension: true` wird zusätzlich ein
 *   `srsDimension`-Attribut bei `pos` und `posList` hinzugefügt.
 * - Eigenschaften, die `OBJECT`s mit dem Objekttyp `Link` sind, werden auf einen
 *   `gml:Reference`-Wert mit den Attributen `xlink:href` und `xlink:title` abgebildet, falls
 *   gesetzt. Für Feature-Referenz-Eigenschaften (mit `refType` deklariert) kann
 *   `featureRefTemplate` `xlink:href` zu einem URN-Bezeichner umschreiben (z.B.
 *   `urn:adv:oid:{{value}}`).
 * - Eigenschaften, die `OBJECT`s mit dem Objekttyp `Measure` sind, werden auf einen
 *   `gml:MeasureType`-Wert abgebildet. Das Objekt muss die Eigenschaften `value` und `uom`
 *   haben, die beide in den Daten vorhanden sein müssen.
 * - Eigenschaften, die `FLOAT`- oder `INTEGER`-Werte mit einer `unit`-Eigenschaft im
 *   Provider-Schema sind, werden ebenfalls auf einen `gml:MeasureType`-Wert abgebildet.
 *   Der Wert von `unit` wird auf das Attribut `uom` abgebildet. Mit `uomStyle: TEMPLATE`
 *   wird die Einheit über `uomMappings` aufgelöst.
 * - Eigenschaften, die in `codelistProperties` aufgeführt sind, werden als leere
 *   XML-Elemente mit `xlink:href` (aus `codelistUriTemplate` aufgebaut) und `xlink:title`
 *   (das Codelist-Label, ersatzweise der Rohwert) kodiert, statt den rohen Wert als
 *   Elementtext zu schreiben.
 * - Jedes Eigenschaftselement erhält den Namen der Eigenschaft aus dem Feature-Schema; mit
 *   `useAlias: true` werden Eigenschaften, die einen `alias` deklarieren, unter diesem Alias
 *   kodiert. Das Element wird im Namensraum seines übergeordneten Objekttyps platziert, wie
 *   in `objectTypeNamespaces` deklariert. Ein explizit angegebenes `prefix:name` im Schema
 *   oder eine `rename`-Transformation hat Vorrang vor dem geerbten Namensraum und dem Alias.
 * - Eigenschaften, die in `valueWrap` aufgeführt sind, haben ihren skalaren Wert in ein oder
 *   mehrere geschachtelte XML-Elemente eingebettet, die in der Konfiguration deklariert sind
 *   (von außen nach innen); der Wert steht innerhalb des innersten Wrappers — nützlich für
 *   Anwendungsschemata, die atomare Werte in Wrappertypen einbetten.
 *     </code>
 * @conformanceEn In general, *Features GML* implements all requirements of conformance class
 *     *Geography Markup Language (GML), Simple Features Profile, Level 0* and *Geography Markup
 *     Language (GML), Simple Features Profile, Level 2* from [OGC API - Features - Part 1: Core
 *     1.0](https://docs.ogc.org/is/17-069r4/17-069r4.html#rc_gmlsf0). However, conformance depends
 *     on the conformance of the GML application schema with the GML Simple Features standard. Since
 *     the GML application schema is not controlled by ldproxy, the conformance level needs to be
 *     declared as part of the configuration.
 *     <p>For SQL feature providers a different root element than `sf:FeatureCollection` can be
 *     configured for the *Features* resource. In that case, the API cannot conform to any of the
 *     GML conformance classes of OGC API Features.
 * @conformanceDe Im Allgemeinen implementiert *Features GML* alle Anforderungen der
 *     Konformitätsklassen *Geography Markup Language (GML), Simple Features Profile, Level 0* und
 *     *Geography Markup Language (GML), Simple Features Profile, Level 2* aus [OGC API - Features -
 *     Part 1: Core 1.0](https://docs.ogc.org/is/17-069r4/17-069r4.html#rc_gmlsf0). Die Konformität
 *     hängt jedoch von der Konformität des GML-Anwendungsschemas mit dem GML Simple Features
 *     Standard ab. Da das GML-Anwendungsschema nicht von ldproxy kontrolliert wird, muss die
 *     Einstufung der Konformität als Teil der Konfiguration deklariert werden.
 *     <p>Für SQL-Feature-Provider kann außerdem ein anderes Root-Element als `sf:FeatureCollection`
 *     für die *Features*-Ressource konfiguriert werden. In diesem Fall kann die API nicht konform
 *     zu einer der GML-Konformitätsklassen von OGC API Features sein.
 * @ref:cfg {@link de.ii.ogcapi.features.gml.domain.GmlConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.features.gml.domain.ImmutableGmlConfiguration}
 */
@Singleton
@AutoBind
public class GmlBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.STABLE_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/is/17-069r4/17-069r4.html",
              "OGC API - Features - Part 1: Core"));

  @Inject
  public GmlBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableGmlConfiguration.Builder()
        .enabled(false)
        .featureCollectionElementName("sf:FeatureCollection")
        .featureMemberElementName("sf:featureMember")
        .supportsStandardResponseParameters(false)
        .useSurfaceAndCurve(false)
        .defaultProfiles(Map.of("rel", "rel-as-link", "val", "val-as-code"))
        .build();
  }
}
