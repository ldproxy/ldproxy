/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.tiles3d.domain.ImmutableTiles3dConfiguration;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title 3D Tiles
 * @langEn Publish geographic data as 3D Tiles.
 * @langDe Veröffentlichen von Geodaten als 3D Tiles.
 * @scopeEn The building block *3D Tiles* adds support for 3D Tiles 1.1 for feature collections that
 *     can be encoded by ldproxy using the building block [Features - glTF](features_-_gltf.md).
 *     <p>This building block supports glTF as the tile format and implicit quadtree tiling.
 *     Subtrees are encoded using the binary format for compactness.
 *     <p>The only [refinement strategy](https://docs.ogc.org/cs/22-025r4/22-025r4.html#toc19) that
 *     is supported is `ADD`. Use the `contentFilters` configuration option to specify at which
 *     level of the tile hierarchy a building will be represented. Each building should be included
 *     on exactly one level.
 *     <p>The 3D Tiles can be inspected in a web browser using an integrated Cesium client.
 * @scopeDe Der Baustein *3D Tiles* fügt Unterstützung für 3D Tiles 1.1 für Feature Collections
 *     hinzu, die von ldproxy unter Verwendung des Bausteins [Features - glTF](features_-_gltf.md)
 *     kodiert werden können.
 *     <p>Dieser Baustein unterstützt glTF als Kachelformat und implizite Quadtree-Kachelung.
 *     Subtrees werden aus Gründen der Kompaktheit im Binärformat kodiert.
 *     <p>Die 3D-Kacheln können in einem Webbrowser mit Hilfe eines integrierten Cesium-Clients
 *     inspiziert werden.
 * @cfgFilesEn This building block does not require or support any additional configuration files.
 *     <p>If seeding is enabled, the files of the tileset are stored in the resource store in the
 *     directory `tiles3d/{apiId}/building/`.
 * @cfgFilesDe Dieses Modul benötigt bzw. unterstützt keine zusätzlichen Konfigurationsdateien.
 *     <p>Wenn das Seeding aktiviert ist, werden die Dateien des Tilesets im Ressourcen-Store im
 *     Verzeichnis `tiles3d/{apiId}/building/` abgelegt.
 * @conformanceEn *3D Tiles* implements support for the OGC Community Standard [3D Tiles
 *     1.1](https://docs.ogc.org/cs/22-025r4/22-025r4.html). glTF is the only supported tile format.
 *     All tilesets use implicit quadtree tiling.
 * @conformanceDe *3D Tiles* implementiert Unterstützung für den OGC Community Standard [3D Tiles
 *     1.1](https://docs.ogc.org/cs/22-025r4/22-025r4.html). glTF ist das einzige unterstützte
 *     Kachelformat. Alle Kachelsätze verwenden implizite Quadtree-Kachelung.
 * @limitationsEn See [Features - glTF](features_-_gltf.md#limitations).
 *     <p>In addition, the following information in Subtrees is not supported: property tables, tile
 *     metadata, content metadata, and subtree metadata.
 * @limitationsDe Siehe [Features - glTF](features_-_gltf.md#limitierungen).
 *     <p>Darüber hinaus werden die folgenden Informationen in Subtrees nicht unterstützt:
 *     Eigenschaftstabellen (Property Tables), Kachel-Metadaten (Tile Metadata), Inhalts-Metadaten
 *     (Content Metadata) und Metadaten von Subtrees.
 * @ref:cfg {@link de.ii.ogcapi.tiles3d.domain.Tiles3dConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.tiles3d.domain.ImmutableTiles3dConfiguration}
 * @ref:endpoints {@link de.ii.ogcapi.tiles3d.infra.Endpoint3dTilesTileset}, {@link
 *     de.ii.ogcapi.tiles3d.infra.Endpoint3dTilesSubtree}, {@link
 *     de.ii.ogcapi.tiles3d.infra.Endpoint3dTilesContent}
 * @ref:queryParameters {@link de.ii.ogcapi.tiles3d.app.QueryParameterFTileset}
 * @ref:pathParameters {@link de.ii.ogcapi.tiles3d.app.PathParameterCollectionId3dTiles}, {@link
 *     de.ii.ogcapi.tiles3d.app.PathParameterLevel}, {@link
 *     de.ii.ogcapi.tiles3d.app.PathParameterX}, {@link de.ii.ogcapi.tiles3d.app.PathParameterY}
 */
@AutoBind
@Singleton
public class Tiles3dBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://opengeospatial.github.io/ogcna-auto-review/22-029.html",
              "OGC API - 3D GeoVolumes (DRAFT)"));
  public static final String STORE_RESOURCE_TYPE = "tiles3d";

  @Inject
  public Tiles3dBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableTiles3dConfiguration.Builder()
        .enabled(false)
        .firstLevelWithContent(0)
        .maxLevel(0)
        .subtreeLevels(3)
        .geometricErrorRoot(0.0f)
        .clampToEllipsoid(false)
        .build();
  }
}
