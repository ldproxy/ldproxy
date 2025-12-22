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
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

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
 * - Geometry properties will be mapped to the corresponding GML 3.2 geometry (`gml:Point` and
 *   `gml:MultiPoint` with `gml:pos`; `gml:LineString`, `gml:MultiCurve`, `gml:Polygon`, and
 *   `gml:MultiSurface` with `gml:posList`). No `gml:id` attribute is added to geometry
 *   elements. The `srsName` attribute is set in each geometry.
 * - Properties that are `OBJECT`s with object type `Link` will be mapped to a `gml:Reference`
 *   value with `xlink:href` and `xmlnk:title` attributes, if set.
 * - Properties that are `OBJECT`s with object type `Measure` will be mapped to a
 *   `gml:MeasureType` value. The object must have the properties `value` and `uom`, which
 *   both must be present in the data.
 * - Properties that are `FLOAT` or `INTEGER` values with a `unit` property in the provider
 *   schema are mapped to a `gml:MeasureType` value, too. The value of `unit` is mapped to the
 *   `uom` attribute.
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
 *   sein.
 * - Geometrieeigenschaften werden auf die entsprechende GML 3.2 Geometrie abgebildet (`gml:Point`
 *   und `gml:MultiPoint` mit `gml:pos`; `gml:LineString`, `gml:MultiCurve`, `gml:Polygon` und
 *   `gml:MultiSurface` mit `gml:posList`). Das Attribut `gml:id` wird den Geometrieelementen
 *   nicht hinzugefügt. Das Attribut "srsName" wird in jeder Geometrie gesetzt.
 * - Eigenschaften, die `OBJECT`s mit dem Objekttyp `Link` sind, werden auf einen `gml:Reference`-
 *   Wert mit den Attributen `xlink:href` und `xmlnk:title` abgebildet, falls gesetzt.
 * - Eigenschaften, die `OBJECT`s mit dem Objekttyp `Measure` sind, werden auf einen
 *   `gml:MeasureType`-Wert abgebildet. Das Objekt muss die Eigenschaften `value` und `uom`
 *   haben, die beide in den Daten vorhanden sein müssen.
 * - Eigenschaften, die `FLOAT`- oder `INTEGER`-Werte mit einer `unit`-Eigenschaft im
 *   Provider-Schema sind, werden ebenfalls auf einen `gml:MeasureType`-Wert abgebildet.
 *   Der Wert von `unit` wird auf das Attribut `uom` abgebildet.
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
        .defaultProfiles(Map.of("rel", "rel-as-link", "val", "val-as-code"))
        .build();
  }
}
