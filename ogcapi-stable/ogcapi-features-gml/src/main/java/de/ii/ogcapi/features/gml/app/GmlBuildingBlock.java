/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.gml.domain.GmlConfiguration;
import de.ii.ogcapi.features.gml.domain.ImmutableGmlConfiguration;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 *   valid XML IDs. On versioned collections, GML defaults to the profile
 *   `versions-as-features-unique-ids`, so a response that can contain multiple versions of a
 *   feature carries unique `gml:id` values (the composite of the canonical id and the
 *   version's interval start).
 * - If `gmlIdentifier` is configured, a `gml:identifier` element is emitted as the first
 *   child of every feature, with the configured `codeSpace` attribute and the feature id
 *   (optionally substituted into `valueTemplate`) as text.
 * - Geometry properties are mapped to GML 3.2 elements depending on the geometry type:
 *   `Point` is mapped to `gml:Point` (with `gml:pos`); `MultiPoint` to `gml:MultiPoint`;
 *   `LineString` to `gml:LineString` (with `gml:posList`); `CircularString` to `gml:Curve`
 *   with a `gml:Arc` segment (three control points) or `gml:ArcString` segment (more than
 *   three control points); `CompoundCurve` to `gml:Curve` with multiple segments;
 *   `MultiLineString` and `MultiCurve` to `gml:MultiCurve`; `Polygon` and `CurvePolygon` to
 *   `gml:Polygon` with `gml:LinearRing` rings; `MultiPolygon` and `MultiSurface` to
 *   `gml:MultiSurface`; `PolyhedralSurface` to `gml:Solid` (when closed) or
 *   `gml:PolyhedralSurface` (when open); and `GeometryCollection` to `gml:MultiGeometry`.
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
 * - Properties listed in `xmlPaths` are encoded as the complete chain of XML elements
 *   declared in the configuration (outer to inner), with the value appearing inside the
 *   innermost element; consecutive properties whose chains share leading elements are encoded
 *   inside one instance of the shared wrapper elements. This is useful for application
 *   schemas that nest atomic values inside wrapper types, and to encode flat properties as
 *   nested structures.
 * - Properties listed in `xmlComments` are encoded as an XML comment
 *   `<!-- name: value -->` in the position of the property element instead of as an element,
 *   so that values without a place in the application schema can be conveyed without making
 *   the response invalid against it. The same form is used for the property links of a
 *   feature, which GML elements cannot carry as RFC 8288 web links.
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
 *   um gültige XML-IDs zu gewährleisten. Bei versionierten Collections verwendet GML
 *   standardmäßig das Profil `versions-as-features-unique-ids`, sodass eine Antwort, die
 *   mehrere Versionen eines Features enthalten kann, eindeutige `gml:id`-Werte trägt (das
 *   Kompositum aus kanonischer Id und Intervallbeginn der Version).
 * - Wenn `gmlIdentifier` konfiguriert ist, wird ein `gml:identifier`-Element als erstes
 *   Kindelement jedes Features ausgegeben, mit dem konfigurierten `codeSpace`-Attribut und
 *   der Feature-ID (optional eingesetzt in `valueTemplate`) als Textinhalt.
 * - Geometrieeigenschaften werden je nach Geometrietyp auf GML-3.2-Elemente abgebildet:
 *   `Point` wird auf `gml:Point` (mit `gml:pos`) abgebildet; `MultiPoint` auf
 *   `gml:MultiPoint`; `LineString` auf `gml:LineString` (mit `gml:posList`); `CircularString`
 *   auf `gml:Curve` mit einem `gml:Arc`-Segment (drei Kontrollpunkte) oder
 *   `gml:ArcString`-Segment (mehr als drei Kontrollpunkte); `CompoundCurve` auf `gml:Curve`
 *   mit mehreren Segmenten; `MultiLineString` und `MultiCurve` auf `gml:MultiCurve`;
 *   `Polygon` und `CurvePolygon` auf `gml:Polygon` mit `gml:LinearRing`-Ringen;
 *   `MultiPolygon` und `MultiSurface` auf `gml:MultiSurface`; `PolyhedralSurface` auf
 *   `gml:Solid` (wenn geschlossen) oder `gml:PolyhedralSurface` (wenn offen); und
 *   `GeometryCollection` auf `gml:MultiGeometry`.
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
 * - Eigenschaften, die in `xmlPaths` aufgeführt sind, werden als die vollständige, in der
 *   Konfiguration deklarierte Kette von XML-Elementen kodiert (von außen nach innen); der
 *   Wert steht innerhalb des innersten Elements. Aufeinanderfolgende Eigenschaften, deren
 *   Ketten mit denselben Elementen beginnen, werden innerhalb einer Instanz der geteilten
 *   Wrapper-Elemente kodiert. Dies ist nützlich für Anwendungsschemata, die atomare Werte in
 *   Wrappertypen einbetten, sowie um flache Eigenschaften als geschachtelte Strukturen zu
 *   kodieren.
 * - Eigenschaften, die in `xmlComments` aufgeführt sind, werden anstelle eines Elements als
 *   XML-Kommentar `<!-- name: wert -->` an der Position des Eigenschaftselements kodiert,
 *   damit Werte ohne Platz im Anwendungsschema übermittelt werden können, ohne die Antwort
 *   gegenüber dem Schema ungültig zu machen. Dieselbe Form wird für die Property-Links eines
 *   Features verwendet, die GML-Elemente nicht als Web-Links nach RFC 8288 tragen können.
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

  private final FeaturesCoreProviders providers;

  @Inject
  public GmlBuildingBlock(FeaturesCoreProviders providers) {
    this.providers = providers;
  }

  /**
   * A property encoded as an annotation comment is written on output only — a comment is not part
   * of the XML information a GML request body is decoded from, so the value could not be sent back.
   * Restricting {@code xmlComments} to properties that are excluded from the {@code RECEIVABLE}
   * scope keeps the encoding and the decoding of a collection symmetric by construction: a property
   * a client may send is never reduced to a comment.
   */
  @Override
  public ValidationResult onStartup(OgcApi api, ValidationResult.MODE apiValidation) {
    ImmutableValidationResult.Builder builder =
        ImmutableValidationResult.builder().mode(apiValidation);

    for (Map.Entry<String, FeatureTypeConfigurationOgcApi> entry :
        api.getData().getCollections().entrySet()) {
      FeatureTypeConfigurationOgcApi collectionData = entry.getValue();
      List<String> xmlComments =
          collectionData
              .getExtension(GmlConfiguration.class)
              .filter(GmlConfiguration::isEnabled)
              .map(GmlConfiguration::getXmlComments)
              .orElse(List.of());
      if (xmlComments.isEmpty()) {
        continue;
      }
      Optional<FeatureSchema> schema = providers.getFeatureSchema(api.getData(), collectionData);
      if (schema.isEmpty()) {
        builder.addErrors(
            MessageFormat.format(
                "The GML option ''xmlComments'' is configured for collection ''{0}'', but no provider has been configured.",
                entry.getKey()));
        continue;
      }
      for (String property : xmlComments) {
        checkPropertyIsNotReceivable(builder, entry.getKey(), schema.get(), property);
      }
    }

    return builder.build();
  }

  private static void checkPropertyIsNotReceivable(
      ImmutableValidationResult.Builder builder,
      String collectionId,
      FeatureSchema schema,
      String property) {
    FeatureSchema propertySchema =
        schema.getAllNestedProperties().stream()
            .filter(p -> Objects.equals(p.getFullPathAsString(), property))
            .findFirst()
            .orElse(null);
    if (Objects.isNull(propertySchema)) {
      builder.addErrors(
          MessageFormat.format(
              "The GML option ''xmlComments'' of collection ''{0}'' lists ''{1}'', but the feature type has no such property.",
              collectionId, property));
      return;
    }
    if (propertySchema.receivable()) {
      builder.addErrors(
          MessageFormat.format(
              "The GML option ''xmlComments'' of collection ''{0}'' lists ''{1}'', but that property is receivable. A property encoded as an XML comment cannot be decoded from a GML request body, so it must be excluded from the ''RECEIVABLE'' scope in the provider schema.",
              collectionId, property));
    }
  }

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableGmlConfiguration.Builder()
        .enabled(false)
        .featureCollectionElementName("sf:FeatureCollection")
        .featureMemberElementName("sf:featureMember")
        .supportsStandardResponseParameters(false)
        .useSurfaceAndCurve(false)
        .defaultProfiles(
            Map.of(
                "rel",
                "rel-as-link",
                "val",
                "val-as-code",
                "versions",
                "versions-as-features-unique-ids"))
        .build();
  }
}
