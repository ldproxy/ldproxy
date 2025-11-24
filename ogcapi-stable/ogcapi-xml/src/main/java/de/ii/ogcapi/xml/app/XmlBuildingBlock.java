/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.xml.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.xml.domain.ImmutableXmlConfiguration;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title XML
 * @langEn XML encoding for every supported resource.
 * @langDe XML-Kodierung für alle unterstützten Ressourcen.
 * @limitationsEn Only the resources *Landing Page*, *Conformance Declaration*, *Feature
 *     Collections* and *Feature Collection* support XML.
 * @limitationsDe Nur die Ressourcen *Landing Page*, *Conformance Declaration*, *Feature
 *     Collections* and *Feature Collection* unterstützen XML.
 * @conformanceEn *JSON* implements all requirements of conformance classes *Geography Markup
 *     Language (GML), Simple Features Profile, Level 0* and *Geography Markup Language (GML),
 *     Simple Features Profile, Level 2* from [OGC API - Features - Part 1: Core
 *     1.0](https://docs.ogc.org/is/17-069r4/17-069r4.html#rc_geojson) for the resources *Landing
 *     Page*, *Conformance Declaration*, *Feature Collections*, and *Feature Collection*.
 * @conformanceDe Der Baustein implementiert für die Ressourcen *Landing Page*, *Conformance
 *     Declaration*, *Feature Collections* und *Feature Collection* alle Vorgaben der
 *     Konformitätsklassen "Geography Markup Language (GML), Simple Features Profile, Level 0" and
 *     "Geography Markup Language (GML), Simple Features Profile, Level 2" von [OGC API - Features -
 *     Part 1: Core 1.0](https://docs.ogc.org/is/17-069r4/17-069r4.html.0#rc_geojson).
 * @ref:cfg {@link de.ii.ogcapi.xml.domain.XmlConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.xml.domain.ImmutableXmlConfiguration}
 */
@Singleton
@AutoBind
public class XmlBuildingBlock implements ApiBuildingBlock {

  @Inject
  public XmlBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableXmlConfiguration.Builder().enabled(false).build();
  }
}
