/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.tiles3d.app.Tiles3dBuildingBlock;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import de.ii.xtraplatform.tiles.domain.SeedingOptions;
import de.ii.xtraplatform.tiles3d.domain.Tile3dAccess;
import de.ii.xtraplatform.tiles3d.domain.Tile3dProvider;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @buildingBlock TILES3D
 * @examplesAll
 *     <p>**API**
 *     <p><code>
 * ```yaml
 * - buildingBlock: TILES3D
 *   enabled: true
 *   tileProvider: example-3dtiles
 * ```
 *     </code>
 *     <p>**3D Tiles Provider**
 *     <p><code>
 * ```yaml
 * id: example-3dtiles
 * providerType: 3DTILE
 * providerSubType: FEATURES
 * seeding:
 *   runOnStartup: true
 *   purge: false
 * tilesets:
 *   building:
 *     id: building
 *     geometricErrorRoot: 8192
 *     clampToEllipsoid: true
 *     subtreeLevels: 3
 *     contentLevels:
 *       min: 5
 *       max: 9
 *     contentFilters:
 *     - diameter3d("bbox")>200
 *     - diameter3d("bbox")<=200 and diameter3d("bbox")>100
 *     - diameter3d("bbox")<=100 and diameter3d("bbox")>40
 *     - diameter3d("bbox")<=40 and diameter3d("bbox")>18
 *     - diameter3d("bbox")<=18
 *     tileFilters:
 *     - true
 *     - diameter3d("bbox")<=200
 *     - diameter3d("bbox")<=100
 *     - diameter3d("bbox")<=40
 *     - diameter3d("bbox")<=18
 * ```
 *     </code>
 */
@Value.Immutable
@Value.Style(builder = "new", deepImmutablesDetection = true, attributeBuilderDetection = true)
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "TILES3D")
@JsonDeserialize(builder = ImmutableTiles3dConfiguration.Builder.class)
public interface Tiles3dConfiguration extends ExtensionConfiguration {

  /**
   * @langEn Specifies the data source for the tiles, see [Tile
   *     Providers](../../providers/tile/README.md).
   * @langDe Spezifiziert die Datenquelle für die Kacheln, siehe
   *     [Tile-Provider](../../providers/tile/README.md).
   * @default null
   * @since v4.6
   */
  @Nullable
  String getTileProvider();

  /**
   * @langEn Specifies the tileset from the tile provider that should be used. The default is
   *     `__all__` for dataset tiles and `{collectionId}` for collection tiles.
   * @langDe Spezifiziert das Tileset vom Tile-Provider das verwendet werden soll. Der Default ist
   *     `__all__` für Dataset Tiles und `{collectionId}` für Collection Tiles.
   * @default __all__ \| {collectionId}
   * @since v4.6
   */
  @Nullable
  String getTileProviderTileset();

  /**
   * @langEn *Deprecated (from v5.0 on you have to use [3D Tiles
   *     Provider](../../providers/tile3d/README.md) entities)* The first level of the tileset which
   *     will contain buildings. The value will depend on the spatial extent of the dataset, i.e.,
   *     at what level of the implicit tiling scheme large buildings can be displayed.
   * @langDe *Deprecated (ab v5.0 müssen [3D-Tiles-Provider](../../providers/tile3d/README.md)
   *     Entities verwendet werden)* Die erste Ebene des Kachelsatzes, die Gebäude enthalten wird.
   *     Der Wert hängt von der räumlichen Ausdehnung des Datensatzes ab, d. h. davon, auf welcher
   *     Ebene des impliziten Kachelschemas große Gebäude dargestellt werden können.
   * @default 0
   * @since v3.4
   */
  @Deprecated(since = "4.6", forRemoval = true)
  @Nullable
  Integer getFirstLevelWithContent();

  /**
   * @langEn *Deprecated (from v5.0 on you have to use [3D Tiles
   *     Provider](../../providers/tile3d/README.md) entities)* The last level of the tileset which
   *     will contain buildings. The value will depend on the spatial extent of the dataset, i.e.,
   *     at what level of the implicit tiling scheme small buildings can be displayed in detail.
   * @langDe *Deprecated (ab v5.0 müssen [3D-Tiles-Provider](../../providers/tile3d/README.md)
   *     Entities verwendet werden)* Die erste Ebene des Kachelsatzes, die Gebäude enthalten wird.
   *     Der Wert hängt von der räumlichen Ausdehnung des Datensatzes ab, d. h. davon, auf welcher
   *     Ebene des impliziten Kachelschemas große Gebäude dargestellt werden können.
   * @default 0
   * @since v3.4
   */
  @Deprecated(since = "4.6", forRemoval = true)
  @Nullable
  Integer getMaxLevel();

  /**
   * @langEn *Deprecated (from v5.0 on you have to use [3D Tiles
   *     Provider](../../providers/tile3d/README.md) entities)* A CQL2 text filter expression for
   *     each level between the `firstLevelWithContent` and the `maxLevel` to select the buildings
   *     to include in the tile on that level. Since the [refinement
   *     strategy](https://docs.ogc.org/cs/22-025r4/22-025r4.html#toc19) is always `ADD`, specify
   *     disjoint filter expressions, so that each building will be included on exactly one level.
   * @langDe *Deprecated (ab v5.0 müssen [3D-Tiles-Provider](../../providers/tile3d/README.md)
   *     Entities verwendet werden)* Ein CQL2-Text-Filterausdruck für jede Ebene zwischen
   *     `firstLevelWithContent` und `maxLevel` zur Auswahl der Gebäude, die in die Kachel auf
   *     dieser Ebene aufgenommen werden sollen. Da die
   *     [Verfeinerungsstrategie](https://docs.ogc.org/cs/22-025r4/22-025r4.html#toc19) immer `ADD`
   *     ist, geben Sie disjunkte Filterausdrücke an, sodass jedes Gebäude auf genau einer Ebene
   *     einbezogen wird.
   * @default []
   * @since v3.4
   */
  @Deprecated(since = "4.6", forRemoval = true)
  List<String> getContentFilters();

  /**
   * @langEn *Deprecated (from v5.0 on you have to use [3D Tiles
   *     Provider](../../providers/tile3d/README.md) entities)* A CQL2 text filter expression for
   *     each level between the `firstLevelWithContent` and the `maxLevel` to select the buildings
   *     to include in the tile on that level or in any of the child tiles. This filter expression
   *     is the same as all the `contentFilters` on this or higher levels combined with an `OR`.
   *     This is also the default value. However, depending on the filter expressions, this may lead
   *     to inefficient tile filters and to improve performance the tile filters can also be
   *     specified explicitly.
   * @langDe *Deprecated (ab v5.0 müssen [3D-Tiles-Provider](../../providers/tile3d/README.md)
   *     Entities verwendet werden)* Ein CQL2-Text-Filterausdruck für jede Ebene zwischen
   *     `firstLevelWithContent` und `maxLevel` zur Auswahl der Gebäude, die in die Kachel auf
   *     dieser Ebene aufgenommen werden sollen oder in eine Kachel auf den tieferen Ebenen. Dieser
   *     Filterausdruck ist derselbe wie alle `contentFilters` auf dieser oder tieferen Ebenen,
   *     kombiniert mit einem `OR`. Dies ist auch der Standardwert. Je nach den Filterausdrücken
   *     kann dies jedoch zu ineffizienten Kachelfiltern führen, und zur Verbesserung der Leistung
   *     können die Kachelfilter auch explizit angegeben werden.
   * @default [ ... ]
   * @since v3.4
   */
  @Deprecated(since = "4.6", forRemoval = true)
  @Value.Default
  default List<String> getTileFilters() {
    return getTileFilters(getContentFilters());
  }

  static List<String> getTileFilters(List<String> contentFilters) {
    int levels = contentFilters.size();
    return IntStream.range(0, levels)
        .mapToObj(
            i ->
                String.format(
                    "(%s)",
                    IntStream.range(i, levels)
                        .mapToObj(contentFilters::get)
                        .collect(Collectors.joining(") OR ("))))
        .toList();
  }

  /**
   * @langEn *Deprecated (from v5.0 on you have to use [3D Tiles
   *     Provider](../../providers/tile3d/README.md) entities)* The error, in meters, introduced if
   *     a tile at level 0 (root) is rendered and its children at level 1 are not. At runtime, the
   *     geometric error is used to compute screen space error (SSE), i.e., the error measured in
   *     pixels.
   * @langDe *Deprecated (ab v5.0 müssen [3D-Tiles-Provider](../../providers/tile3d/README.md)
   *     Entities verwendet werden)* Der Fehler in Metern, der entsteht, wenn eine Kachel auf Ebene
   *     0 (Root) gerendert wird, ihre Kinder auf Ebene 1 jedoch nicht. Zur Laufzeit wird der
   *     geometrische Fehler zur Berechnung des Bildschirmabstandsfehlers (SSE) verwendet, d. h. des
   *     in Pixeln gemessenen Fehlers.
   * @default 0
   * @since v3.4
   */
  @Deprecated(since = "4.6", forRemoval = true)
  @Nullable
  Float getGeometricErrorRoot();

  /**
   * @langEn *Deprecated (from v5.0 on you have to use [3D Tiles
   *     Provider](../../providers/tile3d/README.md) entities)* The number of levels in each
   *     Subtree.
   * @langDe *Deprecated (ab v5.0 müssen [3D-Tiles-Provider](../../providers/tile3d/README.md)
   *     Entities verwendet werden)* Die Anzahl der Ebenen in jedem Subtree.
   * @default 3
   * @since v3.4
   */
  @Deprecated(since = "4.6", forRemoval = true)
  @Nullable
  Integer getSubtreeLevels();

  /**
   * @langEn *Deprecated (from v5.0 on you have to use [3D Tiles
   *     Provider](../../providers/tile3d/README.md) entities)* Controls how and when tiles are
   *     precomputed, see [Seeding options in the tile provider
   *     "Features"](../../providers/tile/10-features.md#seeding).
   * @langDe *Deprecated (ab v5.0 müssen [3D-Tiles-Provider](../../providers/tile3d/README.md)
   *     Entities verwendet werden)* Steuert wie und wann Kacheln vorberechnet werden, siehe
   *     [Optionen für das Seeding im Tile-Provider
   *     "Features"](../../providers/tile/10-features.md#seeding).
   * @default {}
   * @since v3.4
   */
  @Deprecated(since = "4.6", forRemoval = true)
  Optional<SeedingOptions> getSeeding();

  /**
   * @langEn *Deprecated (from v5.0 on you have to use [3D Tiles
   *     Provider](../../providers/tile3d/README.md) entities)* If set to `true`, each building will
   *     be translated vertically so that the bottom of the building is on the WGS 84 ellipsoid. Use
   *     this option, if the data is intended to be rendered without a terrain model.
   * @langDe *Deprecated (ab v5.0 müssen [3D-Tiles-Provider](../../providers/tile3d/README.md)
   *     Entities verwendet werden)* Bei der Einstellung `true` wird jedes Gebäude vertikal so
   *     verschoben, dass der Boden des Gebäudes auf dem WGS 84-Ellipsoid liegt. Verwenden Sie diese
   *     Option, wenn die Daten ohne ein Geländemodell gerendert werden sollen.
   * @default false
   * @since v3.4
   */
  @Deprecated(since = "4.6", forRemoval = true)
  @Nullable
  Boolean getClampToEllipsoid();

  @Deprecated(since = "4.6", forRemoval = true)
  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default boolean shouldClampToEllipsoid() {
    return Boolean.TRUE.equals(getClampToEllipsoid());
  }

  /**
   * @langEn If the 3D Tiles should be rendered in the integrated Cesium client using the terrain
   *     model from Cesium Ion, specify the access token to use in requests.
   * @langDe Wenn die 3D-Kacheln im integrierten Cesium-Client unter Verwendung des Geländemodells
   *     von Cesium Ion gerendert werden sollen, geben Sie das Zugriffstoken an, das in Anfragen
   *     verwendet werden soll.
   * @default null
   * @since v3.4
   */
  Optional<String> getIonAccessToken();

  /**
   * @langEn If the 3D Tiles should be rendered in the integrated Cesium client using the terrain
   *     model from MapTiler, specify the api key to use in requests.
   * @langDe Wenn die 3D-Kacheln im integrierten Cesium-Client unter Verwendung des Geländemodells
   *     von MapTiler gerendert werden sollen, geben Sie den API-Schlüssel an, der in Anfragen
   *     verwendet werden soll.
   * @default null
   * @since v3.4
   */
  Optional<String> getMaptilerApiKey();

  /**
   * @langEn If the 3D Tiles should be rendered in the integrated Cesium client using an external
   *     Terrain Provider, specify the URI of the provider.
   * @langDe Wenn die 3D-Kacheln im integrierten Cesium-Client unter Verwendung des Geländemodells
   *     eines externen Terrain Anbieters gerendert werden sollen, geben Sie die URI des Providers
   *     an.
   * @default null
   * @since v3.4
   */
  Optional<String> getCustomTerrainProviderUri();

  /**
   * @langEn If the terrain does not match the height values in the data, this option can be used to
   *     translate the buildings vertically in the integrated Cesium client.
   * @langDe Wenn das Gelände nicht mit den Höhenwerten in den Daten übereinstimmt, kann diese
   *     Option verwendet werden, um die Gebäude im integrierten Caesium-Client vertikal zu
   *     verschieben.
   * @default 0
   * @since v3.4
   */
  Optional<Double> getTerrainHeightDifference();

  /**
   * @langEn A style in the style repository of the collection to be used in maps with 3D Tiles.
   *     With `DEFAULT` the `defaultStyle` from the [HTML building block](html.md) is used. With
   *     `NONE` the default 3D Tiles style is used. The style must be available in the 3D Tiles
   *     Styling format. If no style is found, 'NONE' is used.
   * @langDe Ein Style im Style-Repository der Collection, der in Karten mit den 3D Tiles verwendet
   *     werden soll. Bei `DEFAULT` wird der `defaultStyle` aus dem [HTML-Baustein](html.md)
   *     verwendet. Bei `NONE` wird der Standard-Style von 3D Tiles verwendet. Der Style muss im
   *     Format 3D Tiles Styling verfügbar sein. Wird kein Style gefunden, wird `NONE` verwendet.
   * @default DEFAULT
   */
  @Nullable
  String getStyle();

  @Value.Auxiliary
  @Value.Derived
  @JsonIgnore
  default String getDatasetTileset() {
    return Objects.requireNonNullElse(getTileProviderTileset(), Tiles3dBuildingBlock.DATASET_TILES);
  }

  default String getCollectionTileset(String collectionId) {
    if (Objects.isNull(getTileProviderTileset())
        || Tiles3dBuildingBlock.DATASET_TILES.equals(getTileProviderTileset())) {
      return collectionId;
    }
    return getTileProviderTileset();
  }

  default boolean hasDatasetTiles(Tile3dProviders providers, OgcApiDataV2 apiData) {
    return Objects.nonNull(providers)
        && hasTiles(
            providers.getTile3dProvider(apiData),
            getDatasetTileset(),
            (tileset, tileAccess) -> true);
  }

  default boolean hasCollectionTiles(
      Tile3dProviders providers, OgcApiDataV2 apiData, String collectionId) {
    return Objects.nonNull(providers)
        && hasTiles(
            providers.getTile3dProvider(apiData, apiData.getCollectionData(collectionId)),
            getCollectionTileset(collectionId),
            (tileset, tileAccess) -> true);
  }

  default boolean hasTiles(
      Optional<Tile3dProvider> provider,
      String tileset,
      BiFunction<String, Tile3dAccess, Boolean> testForTileType) {
    return provider
        .filter(tileProvider -> tileProvider.getData().getTilesets().containsKey(tileset))
        .filter(
            tileProvider ->
                tileProvider.access().isAvailable()
                    && testForTileType.apply(tileset, tileProvider.access().get()))
        .isPresent();
  }

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutableTiles3dConfiguration.Builder();
  }

  @Value.Check
  default void check() {
    Preconditions.checkState(
        Objects.requireNonNullElse(getMaxLevel(), 0) <= 16,
        "The maximum level that is supported is 16. Found: %s.",
        getMaxLevel());
    //noinspection ConstantConditions
    Preconditions.checkState(
        getContentFilters().isEmpty()
            || getContentFilters().size()
                == Objects.requireNonNullElse(getMaxLevel(), 0)
                    - Objects.requireNonNullElse(getFirstLevelWithContent(), 0)
                    + 1,
        "The length of 'contentFilters' must be the same as the levels with content. Found: %s filters and %s levels with content.",
        getContentFilters().size(),
        Objects.requireNonNullElse(getMaxLevel(), 0)
            - Objects.requireNonNullElse(getFirstLevelWithContent(), 0)
            + 1);
  }
}
