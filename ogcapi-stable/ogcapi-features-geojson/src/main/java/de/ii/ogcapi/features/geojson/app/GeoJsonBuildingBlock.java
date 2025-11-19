/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.geojson.domain.ImmutableGeoJsonConfiguration.Builder;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author zahnen
 */

/**
 * @title Features - GeoJSON
 * @langEn Encode features as GeoJSON.
 * @langDe Kodierung von Features als GeoJSON.
 * @cfgFilesEn The building block does not require or support any additional configuration files.
 * @cfgFilesDe Der Baustein benötigt bzw. unterstützt keine zusätzlichen Konfigurationsdateien.
 * @conformanceEn *Features GeoJSON* implements all requirements of conformance class *GeoJSON* from
 *     [OGC API - Features - Part 1: Core
 *     1.0](https://docs.ogc.org/is/17-069r4/17-069r4.html#rc_geojson) for the two mentioned
 *     resources.
 * @conformanceDe Der Baustein implementiert für die Ressourcen Features und Feature alle Vorgaben
 *     der Konformitätsklasse "GeoJSON" von [OGC API - Features - Part 1: Core
 *     1.0](https://docs.ogc.org/is/17-069r4/17-069r4.html#rc_geojson).
 * @ref:cfg {@link de.ii.ogcapi.features.geojson.domain.GeoJsonConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.features.geojson.domain.ImmutableGeoJsonConfiguration}
 */
@Singleton
@AutoBind
public class GeoJsonBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.STABLE_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/is/17-069r4/17-069r4.html",
              "OGC API - Features - Part 1: Core"));

  @Inject
  public GeoJsonBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new Builder()
        .enabled(true)
        .defaultProfiles(Map.of("rel", "rel-as-link", "geojson", "rfc7946"))
        .build();
  }
}
