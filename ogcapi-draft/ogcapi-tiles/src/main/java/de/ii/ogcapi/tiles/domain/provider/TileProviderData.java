/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.domain.provider;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * # Tile-Provider-Objects
 *
 * @langEn There are currently three types of Tile providers supported:
 *     <p>- `FEATURES`: The tiles are derived from a feature provider. - `MBTILES`: The tiles of a
 *     tileset in the "WebMercatorQuad" tiling scheme are available in an MBTiles archive. -
 *     `TILESERVER`: The tiles are retrieved from a TileServer GL instance.
 * @langDe Es werden aktuell drei Arten von Tile-Providern unterstützt:
 *     <p>- `FEATURES`: Die Kacheln werden aus einem Feature-Provider abgeleitet. - `MBTILES`: Die
 *     Kacheln eines Tileset im Kachelschema "WebMercatorQuad" liegen in einem MBTiles-Archiv vor. -
 *     `TILESERVER`: Die Kacheln werden von einer TileServer-GL-Instanz abgerufen.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = TileProviderFeaturesData.class, name = "FEATURES"),
  @JsonSubTypes.Type(value = TileProviderMbtilesData.class, name = "MBTILES"),
  @JsonSubTypes.Type(value = TileProviderTileServerData.class, name = "TILESERVER")
})
public interface TileProviderData {

  TileProviderData mergeInto(TileProviderData tileProvider);
}