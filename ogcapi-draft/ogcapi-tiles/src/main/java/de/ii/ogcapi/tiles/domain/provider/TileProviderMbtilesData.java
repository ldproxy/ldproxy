/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.domain.provider;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Objects;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * # Tile-Provider MBTILES
 *
 * @langEn With this tile provider, the tiles are provided via an [MBTiles
 *     file](https://github.com/mapbox/mbtiles-spec). The tile format and all other properties of
 *     the tileset resource are derived from the contents of the MBTiles file. Only the
 *     "WebMercatorQuad" tiling scheme is supported.
 * @langDe Bei diesem Tile-Provider werden die Kacheln über eine
 *     [MBTiles-Datei](https://github.com/mapbox/mbtiles-spec) bereitgestellt. Das Kachelformat und
 *     alle anderen Eigenschaften der Tileset-Ressource ergeben sich aus dem Inhalt der
 *     MBTiles-Datei. Unterstützt wird nur das Kachelschema "WebMercatorQuad".
 */
@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonDeserialize(builder = ImmutableTileProviderMbtilesData.Builder.class)
public interface TileProviderMbtilesData extends TileProviderData {

  /**
   * @langEn Fixed value, identifies the tile provider type.
   * @langDe Fester Wert, identifiziert die Tile-Provider-Art.
   * @default `MBTILES`
   */
  default String getType() {
    return "MBTILES";
  }

  /**
   * @langEn Filename of the MBTiles file in the `api-resources/tiles/{apiId}` directory.
   * @langDe Dateiname der MBTiles-Datei im Verzeichnis `api-resources/tiles/{apiId}`.
   * @default `null`
   */
  @Nullable
  public abstract String getFilename();

  @Override
  default TileProviderData mergeInto(TileProviderData source) {
    if (Objects.isNull(source) || !(source instanceof TileProviderMbtilesData)) return this;

    TileProviderMbtilesData src = (TileProviderMbtilesData) source;

    ImmutableTileProviderMbtilesData.Builder builder =
        ImmutableTileProviderMbtilesData.builder().from(src).from(this);

    // if (!getCenter().isEmpty()) builder.center(getCenter());
    // else if (!src.getCenter().isEmpty()) builder.center(src.getCenter());

    return builder.build();
  }
}