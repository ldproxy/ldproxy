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
    if (!Objects.requireNonNullElse(getGmlVersion(), GML32).equals(GML32)) {
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
   * @langEn Change the default value of the [profile parameter](features.md#query-parameters) for
   *     this feature format. The value is an object where the key is the id of a profile set, such
   *     as `rel`, and the value is the default profile for the profile set, e.g., `rel-as-key`.
   *     These defaults override the defaults specified in the [Features](features.md) building
   *     block.
   * @langDe Spezifiziert den Standardwert des [Profile-Parameters](features.md#query-parameter) für
   *     Features. Der Wert ist ein Objekt, bei dem der Schlüssel die ID eines Profilsatzes ist, z.
   *     B. `rel`, und der Wert das Standardprofil für den Profilsatz, z. B. `rel-as-key`. Diese
   *     Vorgaben haben Vorrang vor den im [Features](features.md)-Baustein angegebenen
   *     Standardprofilen.
   * @since v4.2
   * @default {"rel": "rel-as-link"}
   */
  @Override
  Map<String, String> getDefaultProfiles();

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
