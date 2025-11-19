/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.html.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.html.domain.FeaturesHtmlConfiguration.POSITION;
import de.ii.ogcapi.features.html.domain.ImmutableFeaturesHtmlConfiguration.Builder;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Features - HTML
 * @langEn Encode features as HTML.
 * @langDe Kodierung von Features als HTML.
 * @cfgFilesEn If the `style` configuration option is set to a custom style, the stylesheet file
 *     must be placed in the value store as `maplibre-styles/{apiId}/{styleId}.json`, if
 *     `mapClientType` is `MAP_LIBRE`. The stylesheet must be a MapLibre style. If `mapClientType`
 *     is `CESIUM`, the stylesheet file must be placed in the value store as
 *     `3dtiles-styles/{apiId}/building/{styleId}.json`, and must be a 3DTiles style. `{styleId}`
 *     must be the value of the `style` configuration option, `{apiId}` must be the `id` of the
 *     service.
 * @cfgFilesDe Wenn die Konfigurationsoption `style` auf einen spezifischen Style gesetzt wird, muss
 *     die Stylesheet-Datei im Value Store unter `maplibre-styles/{apiId}/{styleId}.json` abgelegt
 *     werden, wenn `mapClientType` auf `MAP_LIBRE` gesetzt ist. Das Stylesheet muss ein
 *     MapLibre-Style sein. Wenn `mapClientType` auf `CESIUM` gesetzt ist, muss die Stylesheet-Datei
 *     im Value Store unter `3dtiles-styles/{apiId}/building/{styleId}.json` abgelegt werden und ein
 *     3DTiles-Style sein. `{styleId}` muss dem Wert der Konfigurationsoption `style` entsprechen,
 *     `{apiId}` muss die `id` des Dienstes sein.
 * @conformanceEn *Features HTML* implements all requirements of conformance class *HTML* of [OGC
 *     API - Features - Part 1: Core 1.0](https://docs.ogc.org/is/17-069r4/17-069r4.html#rc_html)
 *     for the two mentioned resources.
 * @conformanceDe Der Baustein implementiert für die Ressourcen Features und Feature alle Vorgaben
 *     der Konformitätsklasse "HTML" von [OGC API - Features - Part 1: Core
 *     1.0](https://docs.ogc.org/is/17-069r4/17-069r4.html#rc_html).
 * @ref:cfg {@link de.ii.ogcapi.features.html.domain.FeaturesHtmlConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.features.html.domain.ImmutableFeaturesHtmlConfiguration}
 */
@Singleton
@AutoBind
public class FeaturesHtmlBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.STABLE_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/is/17-069r4/17-069r4.html",
              "OGC API - Features - Part 1: Core"));

  @Inject
  public FeaturesHtmlBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new Builder()
        .enabled(true)
        .mapPosition(POSITION.AUTO)
        .style("DEFAULT")
        .propertyTooltips(true)
        .propertyTooltipsOnItems(false)
        .defaultProfiles(Map.of("rel", "rel-as-link", "val", "val-as-title"))
        .build();
  }
}
