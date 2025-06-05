/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.jsonfg.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.jsonfg.domain.ImmutableJsonFgConfiguration.Builder;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Features - JSON-FG
 * @langEn Encode features as JSON-FG.
 * @langDe Kodierung von Features als JSON-FG.
 * @scopeEn GeoJSON is a popular encoding for feature data. It is the default encoding for features
 *     in ldproxy. However, GeoJSON has intentional restrictions that prevent or limit its use in
 *     certain contexts. For example, GeoJSON is restricted to WGS 84 coordinates, does not support
 *     volumetric geometries and has no concept of classifying features according to their type.
 *     <p>OGC Features and Geometries JSON (JSON-FG) is an OGC proposal for GeoJSON extensions that
 *     provide standard ways to support such requirements.
 *     <p>By default, the `geometry` and `place` members will be based on the same geometry property
 *     in the feature schema. In some cases, it can be appropriate to use different properties. For
 *     example, if a building feature has a solid geometry and a footprint geometry, a useful
 *     approach can be to provide the solid in `place` and the footprint in `geometry` / GeoJSON. To
 *     use a different geometry property in `place`, specify the property name.*
 * @scopeDe GeoJSON ist eine beliebte Kodierung für Features. Es ist die Standardkodierung für
 *     Features in ldproxy. GeoJSON hat jedoch bewusste Einschränkungen, die seine Verwendung unter
 *     Umständen verhindern oder einschränken. So ist GeoJSON beispielsweise auf WGS 84-Koordinaten
 *     beschränkt, unterstützt keine volumetrischen Geometrien und hat kein Konzept zur
 *     Klassifizierung von Features nach ihrem Typ.
 *     <p>OGC Features and Geometries JSON (JSON-FG) ist ein Vorschlag von OGC für
 *     GeoJSON-Erweiterungen, die Standardwege zur Unterstützung solcher Anforderungen bieten.
 *     <p>Standardmäßig basieren die Elemente `geometry` und `place` auf derselben
 *     Geometrie-Eigenschaft im Feature-Schema. In einigen Fällen kann es sinnvoll sein,
 *     unterschiedliche Eigenschaften zu verwenden. Wenn zum Beispiel ein Gebäude-Feature eine
 *     Solid-Geometrie und eine Grundriss-Geometrie hat, kann es sinnvoll sein, den Solid in `place`
 *     und den Grundriss in `geometry` / GeoJSON bereitzustellen. Um eine andere
 *     Geometrieeigenschaft in `place` zu verwenden, geben Sie den Eigenschaftsnamen an.
 * @conformanceEn The building block is based on [draft 0.2.1 of
 *     JSON-FG](https://github.com/opengeospatial/ogc-feat-geo-json/releases/tag/v0.2.1). The
 *     implementation will change as the draft will evolve during the standardization process.
 * @conformanceDe Der Baustein basiert auf dem [Entwurf 0.2.1 von
 *     JSON-FG](https://github.com/opengeospatial/ogc-feat-geo-json/releases/tag/v0.2.1). Die
 *     Implementierung wird sich im Zuge der weiteren Standardisierung der Spezifikation noch
 *     ändern.
 * @ref:cfg {@link de.ii.ogcapi.features.jsonfg.domain.JsonFgConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.features.jsonfg.domain.ImmutableJsonFgConfiguration}
 * @since v3.1
 */
@Singleton
@AutoBind
public class JsonFgBuildingBlock implements ApiBuildingBlock {

  @Inject
  public JsonFgBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new Builder()
        .enabled(false)
        .describedby(false)
        .coordRefSys(true)
        .geojsonCompatibility(true)
        .build();
  }
}
