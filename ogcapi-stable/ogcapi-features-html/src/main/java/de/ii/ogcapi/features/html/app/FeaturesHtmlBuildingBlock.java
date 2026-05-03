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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Features - HTML
 * @langEn Encode features as HTML.
 * @langDe Kodierung von Features als HTML.
 * @scopeEn The building block *Features - HTML* provides support for encoding features in HTML.
 *     <p>If features are requested in a coordinate reference system that is not the default
 *     coordinate reference system (WGS84 longitude/latitude with or without height), that aspect of
 *     the request is ignored and the features are returned in the default coordinate reference
 *     system. The reason is that the HTML representation requires the use of the default coordinate
 *     reference system for any embedded schema.org annotations.
 *     <p>A map of the features is included in the HTML response, if the feature schema of the
 *     features includes a geometry.
 *     <p>Note that there may be reasons that the map is blank and does not show any features. For
 *     example, if the primary geometry property is not included in the response.
 * @scopeDe Der Baustein *Features – HTML* bietet Unterstützung für die Darstellung von Features in
 *     HTML.
 *     <p>Werden Features in einem Koordinatenreferenzsystem angefordert, das nicht dem
 *     Standard-Koordinatenreferenzsystem entspricht (WGS84-Längen-/Breitengrad mit oder ohne
 *     Höhenangabe), wird dieser Aspekt der Anfrage ignoriert und die Features werden im
 *     Standard-Koordinatenreferenzsystem zurückgegeben. Der Grund dafür ist, dass die
 *     HTML-Darstellung die Verwendung des Standard-Koordinatenreferenzsystems für etwaige
 *     eingebettete schema.org-Annotationen erfordert.
 *     <p>Eine Karte der Features ist in die HTML-Antwort eingebettet, sofern das Objektschema der
 *     Objekte eine Geometrie enthält.
 *     <p>Beachten Sie, dass es Gründe geben kann, warum die Karte leer ist und keine Features
 *     anzeigt. Dies ist beispielsweise der Fall, wenn die primäre Geometrieeigenschaft nicht in der
 *     Antwort enthalten ist.
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

  private static final Logger LOGGER = LoggerFactory.getLogger(FeaturesHtmlBuildingBlock.class);

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
        .crsSelector(false)
        .limitSelector(List.of())
        .defaultProfiles(Map.of("rel", "rel-as-link", "val", "val-as-title"))
        .build();
  }
}
