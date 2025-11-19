/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.json.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.json.domain.ImmutableJsonConfiguration.Builder;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title JSON
 * @langEn JSON encoding for every supported resource.
 * @langDe JSON-Kodierung für alle unterstützten Ressourcen.
 * @cfgFilesEn The building block does not require or support any additional configuration files.
 * @cfgFilesDe Der Baustein benötigt bzw. unterstützt keine zusätzlichen Konfigurationsdateien.
 * @conformanceEn *JSON* implements all requirements of conformance class *GeoJSON* from [OGC API -
 *     Features - Part 1: Core 1.0](https://docs.ogc.org/is/17-069r4/17-069r4.html#rc_geojson) for
 *     the resources *Landing Page*, *Conformance Declaration*, *Feature Collections*, and *Feature
 *     Collection*.
 * @conformanceDe Der Baustein implementiert für die Ressourcen *Landing Page*, *Conformance
 *     Declaration*, *Feature Collections* und *Feature Collection* alle Vorgaben der
 *     Konformitätsklasse "GeoJSON" von [OGC API - Features - Part 1: Core
 *     1.0](https://docs.ogc.org/is/17-069r4/17-069r4.html.0#rc_geojson).
 * @ref:cfg {@link de.ii.ogcapi.json.domain.JsonConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.json.domain.ImmutableJsonConfiguration}
 */
@Singleton
@AutoBind
public class JsonBuildingBlock implements ApiBuildingBlock {

  @Inject
  public JsonBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new Builder().enabled(true).build();
  }
}
