/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.domain;

import static de.ii.xtraplatform.features.gml.domain.GmlVersion.GML32;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ProfilesConfiguration;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformations;
import de.ii.xtraplatform.features.gml.domain.GmlVersion;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @buildingBlock GML
 * @langEn By default, every GML property element will receive the property name from the feature
 *     schema. That is, the element will be in the default namespace. A different name can be set
 *     using the `rename` transformation, which can be used to change the name, but also supports to
 *     add a namespace prefix.
 * @langDe Standardmäßig erhält jedes GML-Eigenschaftselement den Eigenschaftsnamen aus dem
 *     Feature-Schema. Das heißt, das Element wird im Standard-Namensraum liegen. Ein anderer Name
 *     kann mit der Transformation `rename` festgelegt werden, die zum Ändern des Namens verwendet
 *     werden kann, aber auch das Hinzufügen eines Namensraumpräfixes unterstützt.
 * @examplesAll <code>
 * ```yaml
 * - buildingBlock: GML
 *   enabled: true
 *   applicationNamespaces:
 *     ns1: http://www.example.com/ns/ns1/1.0
 *     ns2: http://www.example.com/ns/ns2/1.0
 *   defaultNamespace: ns1
 *   schemaLocations:
 *     ns1: '{{serviceUrl}}/resources/ns1.xsd'
 *     ns2: '{{serviceUrl}}/resources/ns2.xsd'
 *   gmlIdPrefix: '_'
 * collections:
 *   some_type:
 *     ...
 *     api:
 *     - buildingBlock: GML
 *       xmlAttributes:
 *         - someAtt
 *       transformations:
 *         someOtherAtt:
 *           rename: 'ns2:someOtherAtt'
 * ```
 *     </code>
 * @langEn ## Example: AdV NAS (AAA) profile
 *     <p>The following example combines all options that are typically required to publish data
 *     according to the AdV-GeoInfoDok / AFIS-ALKIS-ATKIS NAS application schema. Each option is
 *     individually opt-in and reusable for other GML application schemas.
 * @langDe ## Beispiel: AdV-NAS-(AAA-)Profil
 *     <p>Das folgende Beispiel kombiniert alle Optionen, die üblicherweise benötigt werden, um
 *     Daten gemäß dem AdV-GeoInfoDok-/AFIS-ALKIS-ATKIS-NAS-Anwendungsschema bereitzustellen. Jede
 *     Option ist einzeln aktivierbar und auch für andere GML-Anwendungsschemata nutzbar.
 * @examplesAll <code>
 * ```yaml
 * - buildingBlock: GML
 *   enabled: true
 *   useSurfaceAndCurve: true
 *   applicationNamespaces:
 *     aaa: 'http://www.adv-online.de/namespaces/adv/gid/7.1'
 *   defaultNamespace: aaa
 *   schemaLocations:
 *     aaa: 'https://repository.gdi-de.org/schemas/adv/nas/7.1/aaa.xsd'
 *   featureRefTemplate: 'urn:adv:oid:{{value}}'
 *   gmlIdentifier:
 *     codeSpace: 'http://www.adv-online.de/'
 *     valueTemplate: 'urn:adv:oid:{{value}}'
 *   appendTemporalSuffixToGmlId: true
 *   srsNameStyle: TEMPLATE
 *   srsNameMappings:
 *     - crs:
 *         code: 25832
 *         forceAxisOrder: NONE
 *       value: 'urn:adv:crs:ETRS89_UTM32'
 *     - crs:
 *         code: 25833
 *         forceAxisOrder: NONE
 *       value: 'urn:adv:crs:ETRS89_UTM33'
 *   uomStyle: TEMPLATE
 *   uomMappings:
 *     - uom: 'm'
 *       value: 'urn:adv:uom:m'
 *     - uom: 'grad'
 *       value: 'urn:adv:uom:grad'
 *   codelistUriTemplate: 'https://registry.gdi-de.org/codelist/de.adv.alkis/{{codelistId}}/{{value}}'
 *   codelistProperties:
 *     bauwerk.funktion: AX_Funktion_Bauwerk
 * collections:
 *   ax_flurstueck:
 *     api:
 *     - buildingBlock: GML
 *       codelistProperties:
 *         art_der_flurstuecksgrenze: AX_ArtDerFlurstuecksgrenze
 * ```
 *     </code>
 */
@Value.Immutable
@Value.Style(builder = "new", deepImmutablesDetection = true, attributeBuilderDetection = true)
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "GML")
@JsonDeserialize(builder = ImmutableGmlConfiguration.Builder.class)
public interface GmlConfiguration
    extends ExtensionConfiguration, PropertyTransformations, ProfilesConfiguration {

  enum Conformance {
    NONE,
    GMLSF0,
    GMLSF2
  }

  enum SrsNameStyle {
    OGC,
    TEMPLATE
  }

  enum UomStyle {
    RAW,
    TEMPLATE
  }

  /**
   * @langEn Selects the GML version to use: `GML32` for GML 3.2, `GML31` for GML 3.1 and `GML21`
   *     for GML 2.1.
   * @langDe Bestimmt die zu verwendende GML-Version: `GML32` für GML 3.2, `GML31` für GML 3.1 und
   *     `GML21` für GML 2.1.
   * @default GML32
   * @examplesAll <code>
   * ```yaml
   * - buildingBlock: GML
   *   enabled: true
   *   gmlVersion: GML31
   * ```
   * </code>
   * @since v3.3
   */
  @Nullable
  GmlVersion getGmlVersion();

  /**
   * @langEn The default `null` declares that the GML support does not meet all requirements of the
   *     *Geography Markup Language (GML), Simple Features Profile, Level 0* or the *Geography
   *     Markup Language (GML), Simple Features Profile, Level 2* conformance classes from [OGC API
   *     - Features - Part 1: Core 1.0](https://docs.ogc.org/is/17-069r4/17-069r4.html#rc_gmlsf0).
   *     <p>If the value is set to `0`, `1` or `2`, the conformance will be declared in the
   *     *Conformance Declaration* resource.
   *     <p>If for a collection from a SQL feature provider a root element different to
   *     `sf:FeatureCollection` is configured in `featureCollectionElementName`, the value will be
   *     ignored and no conformance to a GML conformance class will be declared.
   * @langDe Der Standardwert `null` erklärt, dass die GML-Unterstützung nicht alle Anforderungen
   *     der Konformitätsklassen *Geography Markup Language (GML), Simple Features Profile, Level 0*
   *     oder der *Geography Markup Language (GML), Simple Features Profile, Level 2* aus [OGC API -
   *     Features - Part 1: Core 1.0](https://docs.ogc.org/is/17-069r4/17-069r4.html#rc_gmlsf0)
   *     erfüllt.
   *     <p>Wenn der Wert auf `0`, `1` oder `2` gesetzt wird, wird die Konformität in der
   *     *Conformance Declaration* Ressource angegeben.
   *     <p>Wenn für eine Sammlung von einem SQL-Feature-Provider ein anderes Root-Element als
   *     `sf:FeatureCollection` in `featureCollectionElementName` konfiguriert ist, wird der Wert
   *     ignoriert und es wird keine Konformität zu einer GML-Konformitätsklasse erklärt.
   * @default null
   * @examplesAll <code>
   * ```yaml
   * - buildingBlock: GML
   *   enabled: true
   *   gmlSfLevel: 0
   * ```
   * </code>
   * @since v3.3
   */
  @Nullable
  Integer getGmlSfLevel();

  @Value.Derived
  default Conformance getConformance() {
    if (!GML32.equals(Objects.requireNonNullElse(getGmlVersion(), GML32))) {
      return Conformance.NONE;
    }

    switch (Objects.requireNonNullElse(getGmlSfLevel(), -1)) {
      case 0:
        return Conformance.GMLSF0;
      case 1:
      case 2:
        return Conformance.GMLSF2;
      default:
        return Conformance.NONE;
    }
  }

  /**
   * @langEn Every XML element will have and XML attribute can have an XML namespace. To improve
   *     readability of the XML documents, a namespace prefix is declared for every namespace.
   *     <p>Common namespaces and prefixes are pre-defined, these are: `gml` (GML 3.2), `xlink`
   *     (XLink), `xml` (XML), `sf` (OGC API Features Core 1.0, Core-SF), `wfs` (WFS 2.0), and `xsi`
   *     (XML Schema Information).
   *     <p>Additional namespaces that are used in the data (declared in GML application schemas and
   *     imported schemas), the namespaces are configured with their prefixes. Since feature data
   *     will always use elements in application-schema-specific namespaces, this confirguration
   *     parameter will always need to be specified.
   * @langDe Jedes XML-Element hat einen XML-Namensraum und jedes XML-Attribut kann einen
   *     XML-Namensraum haben. Um die Lesbarkeit der XML-Dokumente zu verbessern, wird für jeden
   *     Namespace ein Namespace-Präfix deklariert.
   *     <p>Gängige Namespaces und Präfixe sind vordefiniert, diese sind: `gml` (GML 3.2), `xlink`
   *     (XLink), `xml` (XML), `sf` (OGC API Features Core 1.0, Core-SF), `wfs` (WFS 2.0), und `xsi`
   *     (XML Schema Information).
   *     <p>Weitere Namespaces, die in den Daten verwendet werden (deklariert in
   *     GML-Anwendungsschemata und importierten Schemata), werden mit ihren Präfixen konfiguriert.
   *     Da Feature-Daten immer Elemente in anwendungsschemaspezifischen Namespaces verwenden, muss
   *     dieser Konfigurationsparameter immer angegeben werden.
   * @default {}
   * @examplesAll <code>
   * ```yaml
   * - buildingBlock: GML
   *   enabled: true
   *   applicationNamespaces:
   *     ns1: http://www.example.com/foo/0.1
   *     ns2: http://www.example.com/bar/0.1
   * ```
   * </code>
   * @since v3.3
   */
  Map<String, String> getApplicationNamespaces();

  /**
   * @langEn A default namespace that is used for XML elements, if no other namespace is specified,
   *     can be specified with this configuration parameter. The value will be the namespace prefix.
   *     It must be either a pre-defined prefix or a prefix declared in `applicationNamespaces`.
   *     This namespace will be declared as the default namespace of the XML document.
   * @langDe Mit diesem Konfigurationsparameter kann ein Standard-Namespace angegeben werden, der
   *     für XML-Elemente verwendet wird, wenn kein anderer Namespace angegeben ist. Der Wert ist
   *     der Namespace-Präfix. Es muss entweder ein vordefiniertes Präfix oder ein in
   *     `applicationNamespaces` deklariertes Präfix sein. Dieser Namespace wird als
   *     Standard-Namespace des XML-Dokuments deklariert.
   * @default null
   * @examplesAll <code>
   * ```yaml
   * - buildingBlock: GML
   *   enabled: true
   *   applicationNamespaces:
   *     ns1: http://www.example.com/foo/0.1
   *     ns2: http://www.example.com/bar/0.1
   *   defaultNamespace: ns1
   * ```
   * </code>
   * @since v3.3
   */
  @Nullable
  String getDefaultNamespace();

  /**
   * @langEn If any application namespace should be included in the `xsi:schemaLocation` attribute
   *     of the root element, the document URIs have to be provided.
   *     <p>In addition, the schema location of the namespace of the root element will be added, if
   *     known. For the pre-defined namespaces (`gml`, `sf` and `wfs`), the canonical schema
   *     location in the OGC schema repository will be used unless another schema location for the
   *     namespace is configured.
   *     <p>Note that to meet XML Schema validation requirements, the namespace of the root element
   *     must be declared in the `xsi:schemaLocation` attribute, even if the namespace is imported
   *     by another schema.
   * @langDe Wenn ein Anwendungsnamensraum in das Attribut `xsi:schemaLocation` des Root-Elements
   *     aufgenommen werden soll, müssen die Dokument-URIs angegeben werden.
   *     <p>Außerdem wird die Schema-URL des Namespaces des Root-Elements hinzugefügt, falls
   *     bekannt. Für die vordefinierten Namespaces (`gml`, `sf` und `wfs`) wird die kanonische
   *     Schema-URL im OGC-Schema-Repository verwendet, sofern keine andere Schema-URL für den
   *     Namespace konfiguriert ist.
   *     <p>Beachten Sie, dass der Namespace des Root-Elements im Attribut `xsi:schemaLocation`
   *     deklariert werden muss, um den XML-Schema-Validierungsanforderungen zu entsprechen, auch
   *     wenn der Namespace von einem anderen Schema importiert wird.
   * @default null
   * @examplesAll <code>
   * ```yaml
   * - buildingBlock: GML
   *   enabled: true
   *   applicationNamespaces:
   *     ns1: http://www.example.com/foo/0.1
   *     ns2: http://www.example.com/bar/0.1
   *   schemaLocations:
   *     ns1: http://www.example.com/foo/0.1/foo.xsd
   *     ns2: http://www.example.com/bar/0.1/bar.xsd
   * ```
   * </code>
   * @since v3.3
   */
  Map<String, String> getSchemaLocations();

  /**
   * @langEn All object/data type instances are represented through a GML object element.
   *     <p>In the provider schema, a name must be provided for each OBJECT in the `objectType`
   *     property, including for the feature type itself. By default, this name will be used for the
   *     unqualified name of the GML object element.
   *     <p>If the GML object element is not in the default namespace, this configuration parameter
   *     assigns a namespace prefix to an object type.
   * @langDe Alle Objekt/Datentyp-Instanzen werden durch ein GML-Objektelement dargestellt.
   *     <p>Im Provider-Schema muss für jedes OBJEKT in der Eigenschaft `objectType` ein Name
   *     angegeben werden, auch für den Feature-Typ selbst. Standardmäßig wird dieser Name für den
   *     unqualifizierten Namen des GML-Objektelements verwendet.
   *     <p>Wenn das GML-Objektelement nicht im Standard-Namensraum liegt, spezifiziert dieser
   *     Konfigurationsparameter den Namensraumpräfix zu einem Objekttyp.
   * @default {}
   * @examplesAll <code>
   * ```yaml
   * - buildingBlock: GML
   *   enabled: true
   *   objectTypeNamespaces:
   *     FeatureTypeA: ns2
   *     FeatureTypeB: ns2
   *     DataTypeA: ns2
   *     DataTypeB: ns2
   * ```
   * </code>
   * @since v3.3
   */
  Map<String, String> getObjectTypeNamespaces();

  /**
   * @langEn There may also be cases, in particular when inheritance is used in the underlying
   *     application schema, where multiple object types are represented in the same table with an
   *     attribute that specifies the name of the feature/object type. This configuration parameter
   *     provides the capability to identify these properties and map the values to qualified names
   *     for the GML object element. In the example, `_type` is the feature property with three
   *     different values mapped to the qualified element name.
   * @langDe Es kann auch Fälle geben, insbesondere wenn im zugrunde liegenden Anwendungsschema
   *     Vererbung verwendet wird, in denen mehrere Objekttypen in derselben Tabelle geführt werden,
   *     mit einem Attribut, das den Namen des Merkmals/Objekttyps angibt. Dieser
   *     Konfigurationsparameter bietet die Möglichkeit, diese Eigenschaften zu identifizieren und
   *     die Werte auf qualifizierte Namen für das GML-Objektelement abzubilden. Im Beispiel ist
   *     `_type` die Feature-Eigenschaft mit drei verschiedenen Werten, die auf den qualifizierten
   *     Elementnamen abgebildet werden.
   * @default {}
   * @examplesAll <code>
   * ```yaml
   * - buildingBlock: GML
   *   enabled: true
   *   variableObjectElementNames:
   *     FeatureTypeA:
   *       property: _type
   *       mapping:
   *         typeA1: 'ns1:TypeA1'
   *         typeA2: 'ns2:TypeA2'
   *         typeA3: 'ns2:TypeA3'
   * ```
   * </code>
   * @since v3.3
   */
  Map<String, VariableName> getVariableObjectElementNames();

  /**
   * @langEn Various feature collection elements are in use and sometimes additional ones are
   *     specified in GML application schemas. The default is `sf:FeatureCollection` as specified by
   *     OGC API Features. This configuration parameter provides a capability to use a different
   *     feature collection element.
   * @langDe Es werden verschiedene Feature-Collection-Elemente verwendet und manchmal werden
   *     zusätzliche Elemente in GML-Anwendungsschemata definiert. Der Standard ist
   *     `sf:FeatureCollection`, wie von OGC API Features spezifiziert. Dieser
   *     Konfigurationsparameter bietet die Möglichkeit, dass ein anderes Feature-Collection-Element
   *     in der Rückgabe verwendet wird.
   * @default sf:FeatureCollection
   * @examplesAll <code>
   * ```yaml
   * - buildingBlock: GML
   *   enabled: true
   *   featureCollectionElementName: 'plan:XPlanAuszug'
   * ```
   * </code>
   * @since v3.3
   */
  @Nullable
  String getFeatureCollectionElementName();

  /**
   * @langEn The feature collection element referenced in `featureCollectionElementName` has a child
   *     property element that contains each feature. The default is `sf:featureMember` as specified
   *     by OGC API Features. This configuration parameter provides a capability to declare the
   *     element name for the feature collection element.
   * @langDe Das in `featureCollectionElementName`referenzierte Feature-Collection-Element hat ein
   *     untergeordnetes Eigenschaftselement, das wiederum jedes Feature enthält. Der Standardwert
   *     ist `sf:featureMember`, wie von OGC API Features definiert. Dieser Konfigurationsparameter
   *     bietet die Möglichkeit, den Elementnamen für das konfigurierte Feature-Collection-Element
   *     zu spezifizieren.
   * @default sf:featureMember
   * @examplesAll <code>
   * ```yaml
   * - buildingBlock: GML
   *   enabled: true
   *   featureMemberElementName: 'gml:featureMember'
   * ```
   * </code>
   * @since v3.3
   */
  @Nullable
  String getFeatureMemberElementName();

  /**
   * @langEn The feature collection element referenced in `featureCollectionElementName` may support
   *     the WFS 2.0 standard response parameters (`timeStamp`, `numberMatched`, `numberReturned`).
   *     This configuration parameter controls whether the attributes are included in the feature
   *     collection element as XML attributes.
   * @langDe Das Feature-Collection-Element, auf das in `featureCollectionElementName` verwiesen
   *     wird, kann die WFS-2.0-Standardantwortparameter (`timeStamp`, `numberMatched`,
   *     `numberReturned`) unterstützen. Dieser Konfigurationsparameter steuert, ob die Attribute
   *     als XML-Attribute in das Feature-Collection-Element aufgenommen werden.
   * @default false
   * @examplesAll <code>
   * ```yaml
   * - buildingBlock: GML
   *   enabled: true
   *   supportsStandardResponseParameters: false
   * ```
   * </code>
   * @since v3.3
   */
  @Nullable
  Boolean getSupportsStandardResponseParameters();

  /**
   * @langEn Properties are by default represented as the XML child element (GML property element)
   *     of the XML element representing the object (GML object element). Alternatively, the
   *     property can be represented as an XML attribute of the parent GML object element. This is
   *     only possible for properties of type STRING, FLOAT, INTEGER, or BOOLEAN.
   * @langDe Eigenschaften werden standardmäßig als XML-Kindelement (GML-Eigenschaftselement) des
   *     XML-Elements dargestellt, das das Objekt repräsentiert (GML-Objektelement). Alternativ kann
   *     die Eigenschaft auch als XML-Attribut des übergeordneten GML-Objektelements dargestellt
   *     werden. Dies ist nur für Eigenschaften vom Typ STRING, FLOAT, INTEGER oder BOOLEAN möglich.
   * @default []
   * @examplesAll <code>
   * ```yaml
   * - buildingBlock: GML
   *   enabled: true
   *   xmlAttributes:
   *     - myAttProperty
   *     - someProperty.myOtherAttProperty
   * ```
   * </code>
   * @since v3.3
   */
  List<String> getXmlAttributes();

  /**
   * @langEn The feature property with role `ID` in the provider schema is mapped to the `gml:id`
   *     attribute of the feature. These properties must be a direct property of the feature type.
   *     <p>If the values violate the rule for XML IDs, e.g., if they can start with a digit, this
   *     configuration parameter can be used to add a consistent prefix to map all values to valid
   *     XML IDs.
   * @langDe Die Feature-Eigenschaft mit der Rolle `ID` im Provider-Schema wird auf das Attribut
   *     `gml:id` des Merkmals abgebildet. Diese Eigenschaften müssen eine direkte Eigenschaft des
   *     Feature-Typs sein.
   *     <p>Wenn die Werte die Regel für XML-IDs verletzen, z. B. wenn sie mit einer Ziffer beginnen
   *     können, kann dieser Konfigurationsparameter verwendet werden, um ein konsistentes Präfix
   *     hinzuzufügen, um alle Werte auf gültige XML-IDs abzubilden.
   * @default null
   * @examplesAll <code>
   * ```yaml
   * - buildingBlock: GML
   *   enabled: true
   *   gmlIdPrefix: '_'
   * ```
   * </code>
   * @since v3.3
   */
  @Nullable
  String getGmlIdPrefix();

  /**
   * @langEn This option adds a `gml:id` attribute on all GML geometry elements. The id attribute
   *     was required by GML 3.2 until 2016, older copies of the GML schema may still require the
   *     attribute. This option allows to create instances that are valid for such schemas.
   * @langDe Diese Option fügt ein `gml:id`-Attribut zu allen GML-Geometrieelementen hinzu. Das
   *     id-Attribut war in GML 3.2 bis 2016 verpflichtend, ältere Kopien des GML-Schemas können das
   *     Attribut noch benötigen. Diese Option ermöglicht es, Instanzen zu erstellen, die für solche
   *     Schemas gültig sind.
   * @default false
   * @examplesAll <code>
   * ```yaml
   * - buildingBlock: GML
   *   enabled: true
   *   gmlIdOnGeometries: true
   * ```
   * </code>
   * @since v4.0
   */
  @Nullable
  Boolean getGmlIdOnGeometries();

  /**
   * @langEn This option adds a `srsDimension` attribute on all GML `posList` and `pos` elements.
   *     Some GML readers expect this information, especially for 3D coordinates.
   * @langDe Diese Option fügt ein `srsDimension`-Attribut bei allen `posList` und `pos`-Elementen
   *     hinzu. Einige GML-Reader erwarten diese Information, insbesondere für 3D-Koordinaten.
   * @default false
   * @examplesAll <code>
   * ```yaml
   * - buildingBlock: GML
   *   enabled: true
   *   srsDimension: true
   * ```
   * </code>
   * @since v4.6
   */
  @Nullable
  Boolean getSrsDimension();

  /**
   * @langEn If enabled, simple polygons are encoded as {@code gml:Surface} with a single {@code
   *     gml:PolygonPatch} instead of {@code gml:Polygon}, and simple line strings are encoded as
   *     {@code gml:Curve} with a single {@code gml:LineStringSegment} instead of {@code
   *     gml:LineString}. All rings are encoded as {@code gml:Ring} with a single {@code
   *     gml:LineStringSegment} curve member instead of {@code gml:LinearRing}. This option is
   *     useful when the target GML application schema requires these geometry types.
   * @langDe Wenn aktiviert, werden einfache Polygone statt als {@code gml:Polygon} als {@code
   *     gml:Surface} mit genau einem {@code gml:PolygonPatch} kodiert, und einfache Linienzüge
   *     statt als {@code gml:LineString} als {@code gml:Curve} mit genau einem {@code
   *     gml:LineStringSegment}. Alle Ringe werden als {@code gml:Ring} mit genau einem {@code
   *     gml:LineStringSegment} als Curve-Member statt als {@code gml:LinearRing} kodiert. Diese
   *     Option ist nützlich, wenn das Ziel-GML-Anwendungsschema diese Geometrietypen erfordert.
   * @default false
   * @examplesAll <code>
   * ```yaml
   * - buildingBlock: GML
   *   enabled: true
   *   useSurfaceAndCurve: true
   * ```
   * </code>
   * @since v4.8
   */
  @Nullable
  Boolean getUseSurfaceAndCurve();

  /**
   * @langEn Change the default value of the [profile parameter](features.md#query-parameters) for
   *     this feature format. The value is an object where the key is the id of a profile set and
   *     the value is the default profile for the profile set. These defaults override the defaults
   *     specified in the [Features](features.md) building block. For GML, the following default
   *     profiles are set: "rel-as-link" for feature relationships and "val-as-code" for properties
   *     with coded values. Changing these profiles will likely result in a GML representation that
   *     is not valid against the GML application schema which typically do not support variations
   *     of the representation.
   * @langDe Spezifiziert den Standardwert des [Profile-Parameters](features.md#query-parameter) für
   *     Features. Der Wert ist ein Objekt, bei dem der Schlüssel die ID eines Profilsatzes ist und
   *     der Wert das Standardprofil für den Profilsatz. Diese Vorgaben haben Vorrang vor den im
   *     [Features](features.md)-Baustein angegebenen Standardprofilen. Für GML sind die folgenden
   *     Standardprofile gesetzt: "rel-as-link" für Feature-Beziehungen und "val-as-code" für
   *     Eigenschaften mit codierten Werten. Ein Ändern dieser Profile führt wahrscheinlich zu einer
   *     GML-Kodierung, die nicht gegen das GML-Anwendungsschema valide ist, das typischerweise
   *     keine Variationen der Kodierung unterstützt.
   * @since v4.2
   * @default {"rel": "rel-as-link", "val": "val-as-code"}
   */
  @Override
  Map<String, String> getDefaultProfiles();

  /**
   * @langEn Controls how the {@code srsName} attribute on geometries is rendered. {@code OGC} (the
   *     default) emits the OGC URI form (e.g. {@code http://www.opengis.net/def/crs/EPSG/0/25832}).
   *     {@code TEMPLATE} looks up the CRS in {@code srsNameMappings} and uses the mapped value; CRS
   *     without a mapping fall back to the {@code OGC} form.
   * @langDe Steuert, wie das {@code srsName}-Attribut von Geometrien gerendert wird. {@code OGC}
   *     (Standard) erzeugt die OGC-URI-Form (z.B. {@code
   *     http://www.opengis.net/def/crs/EPSG/0/25832}). {@code TEMPLATE} sucht das CRS in {@code
   *     srsNameMappings} und verwendet den zugeordneten Wert; CRS ohne Mapping fallen auf die
   *     {@code OGC}-Form zurück.
   * @default OGC
   * @since v4.9
   */
  @Nullable
  SrsNameStyle getSrsNameStyle();

  /**
   * @langEn Mapping list used when {@code srsNameStyle} is {@code TEMPLATE}. Each entry binds a CRS
   *     to a fixed {@code srsName} value. Useful for application schemas that require non-OGC URIs
   *     (e.g. AdV: {@code urn:adv:crs:ETRS89_UTM32}).
   * @langDe Mapping-Liste für {@code srsNameStyle: TEMPLATE}. Jeder Eintrag bindet ein CRS an einen
   *     festen {@code srsName}-Wert. Nützlich für Anwendungsschemata, die Nicht-OGC-URIs verlangen
   *     (z.B. AdV: {@code urn:adv:crs:ETRS89_UTM32}).
   * @default []
   * @examplesAll <code>
   * ```yaml
   * - buildingBlock: GML
   *   enabled: true
   *   srsNameStyle: TEMPLATE
   *   srsNameMappings:
   *     - crs:
   *         code: 25832
   *         forceAxisOrder: NONE
   *       value: 'urn:adv:crs:ETRS89_UTM32'
   * ```
   * </code>
   * @since v4.9
   */
  List<SrsNameMapping> getSrsNameMappings();

  /**
   * @langEn Controls how the {@code uom} attribute on measure-typed properties is rendered. {@code
   *     RAW} (the default) writes the unit string from the provider schema verbatim. {@code
   *     TEMPLATE} looks up the unit in {@code uomMappings} and uses the mapped value; units without
   *     a mapping fall back to the raw value.
   * @langDe Steuert, wie das {@code uom}-Attribut bei Measure-Eigenschaften gerendert wird. {@code
   *     RAW} (Standard) schreibt den Einheitenwert aus dem Provider-Schema unverändert. {@code
   *     TEMPLATE} sucht die Einheit in {@code uomMappings} und verwendet den zugeordneten Wert;
   *     Einheiten ohne Mapping fallen auf den Rohwert zurück.
   * @default RAW
   * @since v4.9
   */
  @Nullable
  UomStyle getUomStyle();

  /**
   * @langEn Mapping list used when {@code uomStyle} is {@code TEMPLATE}. Each entry binds a unit
   *     string to a fixed {@code uom} value. Useful for application schemas that require non-UCUM
   *     identifiers (e.g. AdV: {@code urn:adv:uom:m}).
   * @langDe Mapping-Liste für {@code uomStyle: TEMPLATE}. Jeder Eintrag bindet einen
   *     Einheiten-String an einen festen {@code uom}-Wert. Nützlich für Anwendungsschemata, die
   *     Nicht-UCUM-Bezeichner verlangen (z.B. AdV: {@code urn:adv:uom:m}).
   * @default []
   * @examplesAll <code>
   * ```yaml
   * - buildingBlock: GML
   *   enabled: true
   *   uomStyle: TEMPLATE
   *   uomMappings:
   *     - uom: 'm'
   *       value: 'urn:adv:uom:m'
   * ```
   * </code>
   * @since v4.9
   */
  List<UomMapping> getUomMappings();

  /**
   * @langEn URI/URN template applied to {@code xlink:href} of feature reference properties (i.e.
   *     properties with {@code refType}). The placeholder {@code {{value}}} is replaced with the
   *     referenced feature id (the segment after {@code /items/} in the original href). Default
   *     {@code null} keeps the original href. Useful for application schemas that require URN-style
   *     references (e.g. AdV: {@code urn:adv:oid:{{value}}}). Does not affect links inside generic
   *     {@code Link} objects.
   * @langDe URI/URN-Template für {@code xlink:href} von Feature-Referenz-Eigenschaften (d.h.
   *     Properties mit {@code refType}). Der Platzhalter {@code {{value}}} wird durch die
   *     referenzierte Feature-ID (das Segment nach {@code /items/} in der Original-URL) ersetzt.
   *     Standardwert {@code null} belässt die Original-URL. Nützlich für Anwendungsschemata, die
   *     URN-Referenzen verlangen (z.B. AdV: {@code urn:adv:oid:{{value}}}). Wirkt nicht auf Links
   *     in generischen {@code Link}-Objekten.
   * @default null
   * @examplesAll <code>
   * ```yaml
   * - buildingBlock: GML
   *   enabled: true
   *   featureRefTemplate: 'urn:adv:oid:{{value}}'
   * ```
   * </code>
   * @since v4.9
   */
  @Nullable
  String getFeatureRefTemplate();

  /**
   * @langEn If set, a {@code gml:identifier} child element is emitted as the first child of every
   *     feature with the configured {@code codeSpace} attribute. The element's text value is the
   *     raw feature id from the provider, optionally substituted into {@code valueTemplate} (where
   *     {@code {{value}}} is replaced with the raw id). Useful for application schemas that mandate
   *     {@code gml:identifier} (e.g. AdV NAS).
   * @langDe Wenn gesetzt, wird ein {@code gml:identifier}-Kindelement als erstes Kind jedes
   *     Features mit dem konfigurierten {@code codeSpace}-Attribut ausgegeben. Der Textinhalt des
   *     Elements ist die rohe Feature-ID aus dem Provider, optional eingesetzt in {@code
   *     valueTemplate} (wobei {@code {{value}}} durch die rohe ID ersetzt wird). Nützlich für
   *     Anwendungsschemata, die {@code gml:identifier} verlangen (z.B. AdV NAS).
   * @default null
   * @examplesAll <code>
   * ```yaml
   * - buildingBlock: GML
   *   enabled: true
   *   gmlIdentifier:
   *     codeSpace: 'http://www.adv-online.de/'
   *     valueTemplate: 'urn:adv:oid:{{value}}'
   * ```
   * </code>
   * @since v4.9
   */
  @Nullable
  GmlIdentifier getGmlIdentifier();

  /**
   * @langEn If {@code true} and the request's {@code datetime} parameter is an interval (contains
   *     {@code /}), the {@code gml:id} of every feature is suffixed with the feature's primary
   *     temporal property value formatted as {@code yyyyMMddTHHmmssX}. The source property is the
   *     one with role {@code PRIMARY_INSTANT}, falling back to {@code PRIMARY_INTERVAL_START}.
   *     Useful for application schemas that require {@code gml:id} to be unique per feature version
   *     (e.g. AdV NAS time-series queries). Does not affect {@code gml:identifier}.
   * @langDe Wenn {@code true} und der {@code datetime}-Parameter der Anfrage ein Intervall ist
   *     (enthält {@code /}), wird die {@code gml:id} jedes Features um den Wert der primären
   *     zeitlichen Eigenschaft formatiert als {@code yyyyMMddTHHmmssX} ergänzt. Quelle ist die
   *     Eigenschaft mit Rolle {@code PRIMARY_INSTANT}, ersatzweise {@code PRIMARY_INTERVAL_START}.
   *     Nützlich für Anwendungsschemata, die je Feature-Version eine eindeutige {@code gml:id}
   *     verlangen (z.B. AdV NAS Zeitreihen-Anfragen). Wirkt nicht auf {@code gml:identifier}.
   * @default false
   * @since v4.9
   */
  @Nullable
  Boolean getAppendTemporalSuffixToGmlId();

  /**
   * @langEn URI template used to construct the {@code xlink:href} for properties that carry a
   *     codelist value. The template may contain the placeholders {@code {{codelistId}}} (replaced
   *     with the codelist id from {@code codelistProperties}) and {@code {{value}}} (replaced with
   *     the raw property value). When set together with {@code codelistProperties}, the matching
   *     property is encoded as an empty element {@code <prop xlink:href="..." xlink:title="..."/>}
   *     where {@code xlink:title} is the codelist label looked up from the resolved codelist
   *     (falling back to the raw value if no label is found).
   * @langDe URI-Template zum Aufbau des {@code xlink:href} für Eigenschaften mit Codelist-Werten.
   *     Das Template darf die Platzhalter {@code {{codelistId}}} (ersetzt durch die Codelist-ID aus
   *     {@code codelistProperties}) und {@code {{value}}} (ersetzt durch den rohen
   *     Eigenschaftswert) enthalten. Wenn zusammen mit {@code codelistProperties} gesetzt, wird die
   *     betroffene Eigenschaft als leeres Element {@code <prop xlink:href="..."
   *     xlink:title="..."/>} kodiert, wobei {@code xlink:title} das Codelist-Label aus der
   *     aufgelösten Codelist ist (Fallback ist der rohe Wert, wenn kein Label gefunden wird).
   * @default null
   * @examplesAll <code>
   * ```yaml
   * - buildingBlock: GML
   *   enabled: true
   *   codelistUriTemplate: 'https://registry.gdi-de.org/codelist/de.adv.alkis/{{codelistId}}/{{value}}'
   * ```
   * </code>
   * @since v4.9
   */
  @Nullable
  String getCodelistUriTemplate();

  /**
   * @langEn Maps property paths (matching {@code FeatureSchema#getFullPathAsString()}) to a
   *     codelist id. Properties listed here are encoded as xlink elements using {@code
   *     codelistUriTemplate} instead of the raw value being written as element text. Entries
   *     defined at the building-block (API) level apply to all collections; per-collection entries
   *     are merged on top.
   * @langDe Bildet Eigenschaftspfade (entsprechend {@code FeatureSchema#getFullPathAsString()}) auf
   *     eine Codelist-ID ab. Hier aufgeführte Eigenschaften werden als xlink-Elemente mittels
   *     {@code codelistUriTemplate} kodiert anstatt den rohen Wert als Elementtext zu schreiben.
   *     Auf Building-Block-Ebene (API) definierte Einträge gelten für alle Collections; Einträge
   *     auf Collection-Ebene werden ergänzt.
   * @default {}
   * @examplesAll <code>
   * ```yaml
   * - buildingBlock: GML
   *   enabled: true
   *   codelistProperties:
   *     bauwerk.funktion: AX_Funktion_Bauwerk
   * ```
   * </code>
   * @since v4.9
   */
  Map<String, String> getCodelistProperties();

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutableGmlConfiguration.Builder();
  }

  @Override
  default ExtensionConfiguration mergeInto(ExtensionConfiguration source) {
    return new ImmutableGmlConfiguration.Builder()
        .from(source)
        .from(this)
        .transformations(
            PropertyTransformations.super
                .mergeInto((PropertyTransformations) source)
                .getTransformations())
        .defaultProfiles(
            ProfilesConfiguration.super
                .mergeInto((ProfilesConfiguration) source)
                .getDefaultProfiles())
        .build();
  }
}
