/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tilematrixsets.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.tilematrixsets.domain.ImmutableTileMatrixSetsConfiguration;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetsConfiguration;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;

/**
 * @title Tile Matrix Sets
 * @langEn Provides definitions of the Tile Matrix Sets used in the API.
 * @langDe Stellt die in der API verwendeten Kachelschemas bereit.
 * @scopeEn This building block provides information about the tiling schemes supported by the API.
 *     <p>The following preconfigured tiling schemes are available:
 *     <p><code>
 * - [WebMercatorQuad](http://www.opengis.net/def/tilematrixset/OGC/1.0/WebMercatorQuad)
 * - [WorldCRS84Quad](http://www.opengis.net/def/tilematrixset/OGC/1.0/WorldCRS84Quad)
 * - [WorldMercatorWGS84Quad](http://www.opengis.net/def/tilematrixset/OGC/1.0/WorldMercatorWGS84Quad)
 * - [AdV_25832](https://demo.ldproxy.net/strassen/tileMatrixSets/AdV_25832) (AdV tiling scheme using ETRS89/UTM32N covering Germany)
 * - [AdV_25833](https://github.com/interactive-instruments/xtraplatform-spatial/blob/master/xtraplatform-tiles/src/main/resources/tilematrixsets/AdV_25833.json) (AdV tiling scheme using ETRS89/UTM33N covering Germany)
 * - [EU_25832](https://demo.ldproxy.net/strassen/tileMatrixSets/EU_25832) (Tiling scheme using ETRS89/UTM32N covering Europe)
 * - [gdi_de_25832](https://demo.ldproxy.net/strassen/tileMatrixSets/gdi_de_25832) (GDI-DE tiling scheme using ETRS89/UTM32N covering Germany)
 *     </code>
 * @scopeDe Dieser Baustein stellt Informationen über die von der API unterstützten
 *     Kachelungsschemas bereit.
 *     <p>Als vorkonfigurierte Kachelschemas stehen zur Verfügung:
 *     <p><code>
 * - [WebMercatorQuad](http://www.opengis.net/def/tilematrixset/OGC/1.0/WebMercatorQuad)
 * - [WorldCRS84Quad](http://www.opengis.net/def/tilematrixset/OGC/1.0/WorldCRS84Quad)
 * - [WorldMercatorWGS84Quad](http://www.opengis.net/def/tilematrixset/OGC/1.0/WorldMercatorWGS84Quad)
 * - [AdV_25832](https://demo.ldproxy.net/strassen/tileMatrixSets/AdV_25832) (Kachelschema der AdV für Deutschland in ETRS89/UTM32N)
 * - [AdV_25833](https://github.com/interactive-instruments/xtraplatform-spatial/blob/master/xtraplatform-tiles/src/main/resources/tilematrixsets/AdV_25833.json) (Kachelschema der AdV für Deutschland in ETRS89/UTM33N)
 * - [EU_25832](https://demo.ldproxy.net/strassen/tileMatrixSets/EU_25832) (Kachelschema des BKG, basierend auf AdV_25832, erweitert auf Europa)
 * - [gdi_de_25832](https://demo.ldproxy.net/strassen/tileMatrixSets/gdi_de_25832) (von der GDI-DE empfohlenes Kachelschema in ETRS89/UTM32N)
 *     </code>
 * @conformanceEn This building block implements the conformance classes "TileMatrixSet", and
 *     "JSONTileMatrixSet" of the [OGC Two Dimensional Tile Matrix Set and Tile Set Metadata 2.0
 *     Standard](https://docs.ogc.org/is/17-083r4/17-083r4.html).
 * @conformanceDe Der Baustein implementiert die Konformitätsklassen "TileMatrixSet" und
 *     "JSONTileMatrixSet" des Standards [OGC Two Dimensional Tile Matrix Set and Tile Set Metadata
 *     2.0](https://docs.ogc.org/is/17-083r4/17-083r4.html).
 * @ref:cfg {@link de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetsConfiguration}
 * @ref:cfgProperties {@link
 *     de.ii.ogcapi.tilematrixsets.domain.ImmutableTileMatrixSetsConfiguration}
 * @ref:endpoints {@link de.ii.ogcapi.tilematrixsets.infra.EndpointTileMatrixSets}, {@link
 *     de.ii.ogcapi.tilematrixsets.infra.EndpointTileMatrixSet}
 * @ref:pathParameters {@link de.ii.ogcapi.tilematrixsets.app.PathParameterTileMatrixSetId}
 * @ref:queryParameters {@link de.ii.ogcapi.tilematrixsets.app.QueryParameterFTileMatrixSets}
 */
@Singleton
@AutoBind
public class TileMatrixSetsBuildingBlock implements ApiBuildingBlock, ConformanceClass {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.STABLE_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/is/20-057/20-057.html", "OGC API - Tiles - Part 1: Core"));

  @Inject
  public TileMatrixSetsBuildingBlock() {}

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of(
        "http://www.opengis.net/spec/tms/2.0/conf/tilematrixset",
        "http://www.opengis.net/spec/tms/2.0/conf/json-tilematrixset");
  }

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {

    return new ImmutableTileMatrixSetsConfiguration.Builder().enabled(false).build();
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    // since building block / capability components are currently always enabled,
    // we need to test, if the TILE MATRIX SETS building block is enabled for the API and stop, if
    // not
    OgcApiDataV2 apiData = api.getData();
    if (!apiData
        .getExtension(TileMatrixSetsConfiguration.class)
        .map(ExtensionConfiguration::isEnabled)
        .orElse(false)) {
      return ValidationResult.of();
    }

    ImmutableValidationResult.Builder builder =
        ImmutableValidationResult.builder().mode(apiValidation);

    return builder.build();
  }
}
