/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.tiles.domain.TileSet.DataType;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(builder = "new")
@JsonDeserialize(builder = ImmutableTileSetsLinkScope.Builder.class)
public interface TileSetsLinkScope {

  enum Level {
    DATASET,
    COLLECTIONS
  }

  enum TileType {
    VECTOR,
    MAP,
    STYLED_MAP;

    public static Optional<TileType> of(DataType dataType, Optional<String> styleId) {
      return switch (dataType) {
        case vector -> Optional.of(VECTOR);
        case map -> Optional.of(styleId.isPresent() ? STYLED_MAP : MAP);
        case coverage -> Optional.empty();
      };
    }
  }

  /**
   * @langEn The tileset lists that receive the link: `DATASET` for the tilesets of the dataset
   *     (`/tiles`), `COLLECTIONS` for the tilesets of the collections (`/collections/{collectionId}
   *     /tiles`). An empty list stands for both.
   * @langDe Die Listen von Kachelsätzen, die den Link erhalten: `DATASET` für die Kachelsätze des
   *     Datensatzes (`/tiles`), `COLLECTIONS` für die Kachelsätze der Collections
   *     (`/collections/{collectionId}/tiles`). Eine leere Liste steht für beide.
   * @default []
   * @since v4.9
   */
  List<Level> getLevels();

  /**
   * @langEn The kinds of tilesets that receive the link: `VECTOR` for vector tiles (`.../tiles`),
   *     `MAP` for map tiles (`.../map/tiles`), `STYLED_MAP` for map tiles of a style
   *     (`.../styles/{styleId}/map/tiles`). An empty list stands for all kinds.
   * @langDe Die Arten von Kachelsätzen, die den Link erhalten: `VECTOR` für Vektorkacheln
   *     (`.../tiles`), `MAP` für Kartenkacheln (`.../map/tiles`), `STYLED_MAP` für Kartenkacheln
   *     eines Styles (`.../styles/{styleId}/map/tiles`). Eine leere Liste steht für alle Arten.
   * @default []
   * @since v4.9
   */
  List<TileType> getTileTypes();

  /**
   * @langEn Restricts the link to the map tiles of these styles (`.../styles/{styleId}/map/tiles`),
   *     for example to link the metadata record of each style. A non-empty list implies `tileTypes:
   *     [STYLED_MAP]`.
   * @langDe Beschränkt den Link auf die Kartenkacheln dieser Styles
   *     (`.../styles/{styleId}/map/tiles`), zum Beispiel um den Metadatensatz jedes Styles zu
   *     verlinken. Eine nicht leere Liste schließt `tileTypes: [STYLED_MAP]` ein.
   * @default []
   * @since v4.9
   */
  List<String> getStyles();

  default boolean appliesTo(Level level, Optional<TileType> tileType, Optional<String> styleId) {
    if (!getLevels().isEmpty() && !getLevels().contains(level)) {
      return false;
    }
    if (!getTileTypes().isEmpty()
        && (tileType.isEmpty() || !getTileTypes().contains(tileType.get()))) {
      return false;
    }
    return getStyles().isEmpty()
        || tileType.filter(TileType.STYLED_MAP::equals).isPresent()
            && styleId.filter(getStyles()::contains).isPresent();
  }
}
