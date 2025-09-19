/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.app.html;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import de.ii.ogcapi.foundation.domain.CollectionExtent;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcResourceMetadata;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.html.domain.ImmutableMapClient;
import de.ii.ogcapi.html.domain.ImmutableSource;
import de.ii.ogcapi.html.domain.MapClient;
import de.ii.ogcapi.html.domain.MapClient.Popup;
import de.ii.ogcapi.html.domain.MapClient.Source.TYPE;
import de.ii.ogcapi.html.domain.MapClient.Type;
import de.ii.ogcapi.html.domain.OgcApiView;
import de.ii.ogcapi.tiles.domain.TilePoint;
import de.ii.ogcapi.tiles.domain.TileSet;
import de.ii.ogcapi.tiles.domain.TileSet.DataType;
import de.ii.ogcapi.tiles.domain.TileSets;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.tiles.domain.TileMatrix;
import de.ii.xtraplatform.tiles.domain.TileMatrixSet;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Value.Style(builder = "new")
@Value.Immutable
public abstract class TileSetsView extends OgcApiView {
  private static final Logger LOGGER = LoggerFactory.getLogger(TileSetsView.class);

  private static final Map<Integer, String> GEOMETRY_TYPES =
      ImmutableMap.of(0, "points", 1, "lines", 2, "polygons");

  public abstract I18n i18n();

  public abstract Optional<Locale> language();

  @Value.Derived
  public String tileJsonTitle() {
    return i18n().get("tileJsonTitle", language());
  }

  @Value.Derived
  public String mapTitle() {
    return i18n().get("mapTitle", language());
  }

  @Value.Derived
  public String metadataTitle() {
    return i18n().get("metadataTitle", language());
  }

  @Value.Derived
  public String tileMatrixSetTitle() {
    return i18n().get("tileMatrixSetTitle", language());
  }

  @Value.Derived
  public String tilesetDescription() {
    return i18n().get("tilesetDescriptionTitle", language());
  }

  @Value.Derived
  public String templateTitle() {
    return i18n().get("templateTitle", language());
  }

  @Value.Derived
  public String howToUse() {
    return i18n().get("howToUseTitle", language());
  }

  @Value.Derived
  public String none() {
    return i18n().get("none", language());
  }

  @Value.Default
  public boolean withOlMap() {
    return true;
  }

  @Value.Default
  public boolean spatialSearch() {
    return false;
  }

  @Value.Derived
  public Boolean isVector() {
    if (tileTemplate().isPresent()) {
      return true;
    } else {
      return false;
    }
  }

  @Value.Derived
  public List<Set<Map.Entry<String, String>>> tileCollections() {
    return tiles().getTilesets().stream()
        .filter(
            tms ->
                mapClientType().equals(MapClient.Type.OPEN_LAYERS)
                    || tms.getTileMatrixSetId().equals("WebMercatorQuad"))
        .map(
            tms -> {
              String tmsId = tms.getTileMatrixSetId();
              TileMatrixSet tileMatrixSet = tileMatrixSets().get(tmsId);
              if (tileMatrixSet == null) return null;
              BoundingBox bbox = tileMatrixSet.getBoundingBox();
              String extent =
                  "["
                      + bbox.getXmin()
                      + ","
                      + bbox.getYmin()
                      + ","
                      + bbox.getXmax()
                      + ","
                      + bbox.getYmax()
                      + "]";
              int maxLevel = tileMatrixSet.getMaxLevel();
              List<TileMatrix> tileMatrixList = tileMatrixSet.getTileMatrices(0, maxLevel);
              String sizes =
                  String.format(
                      "[%s]",
                      tileMatrixList.stream()
                          .map(
                              tileMatrix ->
                                  String.format(
                                      "[%d, %d]",
                                      tileMatrix.getMatrixWidth(), tileMatrix.getMatrixHeight()))
                          .collect(Collectors.joining(", ")));
              double diff = bbox.getXmax() - bbox.getXmin();
              String resolutions =
                  String.format(
                      "[%s]",
                      tileMatrixList.stream()
                          .map(
                              tileMatrix ->
                                  String.valueOf(
                                      diff
                                          / (tileMatrix.getMatrixWidth()
                                              * tileMatrixSet.getTileSize())))
                          .collect(Collectors.joining(", ")));

              String level =
                  tms.getCenterPoint()
                      .flatMap(TilePoint::getTileMatrix)
                      .orElse(getDefaultLevel(diff, tileMatrixSet.getMaxLevel()));
              String lon =
                  tms.getCenterPoint().isPresent()
                          && tms.getCenterPoint().get().getCoordinates().size() >= 2
                      ? Double.toString(tms.getCenterPoint().get().getCoordinates().get(0))
                      : spatialExtent().isPresent()
                          ? Double.toString(
                              spatialExtent().get().getXmax() * 0.5
                                  + spatialExtent().get().getXmin() * 0.5)
                          : "0.0";
              String lat =
                  tms.getCenterPoint().isPresent()
                          && tms.getCenterPoint().get().getCoordinates().size() >= 2
                      ? Double.toString(tms.getCenterPoint().get().getCoordinates().get(1))
                      : spatialExtent().isPresent()
                          ? Double.toString(
                              spatialExtent().get().getYmax() * 0.5
                                  + spatialExtent().get().getYmin() * 0.5)
                          : "0.0";

              return new ImmutableMap.Builder<String, String>()
                  .put("tileMatrixSet", tmsId)
                  .put("maxLevel", String.valueOf(maxLevel))
                  .put("extent", extent)
                  .put("defaultZoomLevel", level)
                  .put("defaultCenterLon", lon)
                  .put("defaultCenterLat", lat)
                  .put("resolutions", resolutions)
                  .put("sizes", sizes)
                  .put("projection", tileMatrixSet.getCrs().toSimpleString())
                  .build()
                  .entrySet();
            })
        .collect(Collectors.toList());
  }

  @Value.Derived
  public List<Link> tileTemplates() {
    return tiles().getTilesets().stream()
        .map(OgcResourceMetadata::getLinks)
        .flatMap(Collection::stream)
        .filter(link -> Objects.equals(link.getRel(), "item"))
        .collect(Collectors.toUnmodifiableList());
  }

  @Value.Derived
  public Optional<String> tileTemplate() {
    return tileTemplates().stream()
        .filter(link -> Objects.equals(link.getType(), "application/vnd.mapbox-vector-tile"))
        .map(Link::getHref)
        .map(
            href ->
                href.replaceAll(
                    "/\\w+/\\{tileMatrix}/\\{tileRow}/\\{tileCol}",
                    "/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}"))
        .findFirst();
  }

  @Value.Derived
  public String tilesUrl() {
    if (tileTemplate().isPresent()) {
      return tileTemplate().get();
    } else {
      return tileTemplates().stream()
          .filter(link -> link.getType().startsWith("image/"))
          .map(Link::getHref)
          .map(
              href ->
                  href.replaceAll(
                      "/\\w+/\\{tileMatrix}/\\{tileRow}/\\{tileCol}",
                      "/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}"))
          .findFirst()
          .orElse(null);
    }
  }

  @Value.Derived
  public String tileJsonLink() {
    return tiles().getTilesets().stream()
        .map(OgcResourceMetadata::getLinks)
        .flatMap(Collection::stream)
        .filter(link -> Objects.equals(link.getRel(), "self"))
        .map(Link::getHref)
        .findFirst()
        .map(href -> href.replaceAll("/\\w+$", "/WebMercatorQuad") + "?f=tilejson")
        .orElse(null);
  }

  @Value.Derived
  public Map<String, String> bbox() {
    return spatialExtent()
        .map(
            boundingBox ->
                ImmutableMap.of(
                    "minLng", Double.toString(boundingBox.getXmin()),
                    "minLat", Double.toString(boundingBox.getYmin()),
                    "maxLng", Double.toString(boundingBox.getXmax()),
                    "maxLat", Double.toString(boundingBox.getYmax())))
        .orElse(null);
  }

  public abstract Boolean removeZoomLevelConstraints();

  @Value.Derived
  public MapClient mapClient() {
    if (tiles().getTilesets().size() >= 1) {
      if (mapClientType().equals(MapClient.Type.MAP_LIBRE)) {
        Optional<TileSet> tileSet =
            tiles().getTilesets().stream()
                .filter(ts -> ts.getTileMatrixSetId().equals("WebMercatorQuad"))
                .findAny();
        if (tileSet.isPresent()) {
          Multimap<String, List<String>> layers = getLayers(tileSet.get());

          return buildMapClient(
              Type.MAP_LIBRE,
              styleUrl(),
              removeZoomLevelConstraints(),
              center(),
              htmlConfig(),
              layers);
        } else {
          LOGGER.error(
              "Configuration error: {} as the client for the HTML representation of tile sets requires that a tile set with the tiling scheme {} exists.",
              mapClientType(),
              "WebMercatorQuad");
          return null;
        }
      } else if (mapClientType().equals(MapClient.Type.OPEN_LAYERS)) {
        Multimap<String, List<String>> layers = getLayers(tiles().getTilesets().get(0));

        return buildMapClient(
            Type.OPEN_LAYERS,
            styleUrl(),
            removeZoomLevelConstraints(),
            center(),
            htmlConfig(),
            layers);
      } else {
        LOGGER.error(
            "Configuration error: {} is not a supported map client for the HTML representation of tile sets.",
            mapClientType());
        return null;
      }
    } else {
      return null;
    }
  }

  @Value.Derived
  public String xyzTemplate() {
    return tilesUrl()
        .replace("{tileMatrixSetId}", "WebMercatorQuad")
        .replace("{tileMatrix}", "{z}")
        .replace("{tileRow}", "{y}")
        .replace("{tileCol}", "{x}");
  }

  public abstract TileSets tiles();

  public abstract Optional<String> collectionId();

  @Value.Derived
  public Optional<TilePoint> center() {
    // the center of all tilesets is the same, the only difference is the tiling scheme
    return tiles().getTilesets().stream().findAny().flatMap(TileSet::getCenterPoint);
  }

  @Value.Derived
  public Optional<BoundingBox> spatialExtent() {
    return tiles().getTilesets().stream()
        .map(TileSet::getBoundingBox)
        // the bbox of all tilesets is the same, the only difference is the tiling scheme
        .findAny()
        .map(
            tBbox ->
                BoundingBox.of(
                    tBbox.getLowerLeft()[0].doubleValue(),
                    tBbox.getLowerLeft()[1].doubleValue(),
                    tBbox.getUpperRight()[0].doubleValue(),
                    tBbox.getUpperRight()[1].doubleValue(),
                    OgcCrs.CRS84))
        .or(() -> apiData().getDefaultExtent().flatMap(CollectionExtent::getSpatial));
  }

  @Value.Derived
  public List<String> tileMatrixSetIds() {
    return tiles().getTilesets().stream()
        .map(TileSet::getTileMatrixSetId)
        .filter(
            tileMatrixSetId ->
                mapClientType().equals(MapClient.Type.OPEN_LAYERS)
                    || tileMatrixSetId.equals("WebMercatorQuad"))
        .collect(Collectors.toList());
  }

  public abstract MapClient.Type mapClientType();

  @Nullable
  public abstract String styleUrl();

  public abstract URICustomizer uriCustomizer();

  public abstract Map<String, TileMatrixSet> tileMatrixSets();

  public TileSetsView() {
    super("tiles.mustache");
  }

  private MapClient buildMapClient(
      Type type,
      String styleUrl,
      boolean removeZoomLevelConstraints,
      Optional<TilePoint> center,
      HtmlConfiguration htmlConfig,
      Multimap<String, List<String>> layers) {
    return new ImmutableMapClient.Builder()
        .type(type)
        .backgroundUrl(Optional.ofNullable(htmlConfig.getBasemapUrl()))
        .attribution(Optional.ofNullable(htmlConfig.getBasemapAttribution()))
        .data(
            new ImmutableSource.Builder()
                .type(isVector() ? TYPE.vector : TYPE.raster)
                .url(xyzTemplate())
                .putAllLayers(layers)
                .build())
        .bounds(Optional.ofNullable(bbox()))
        .popup(Popup.CLICK_PROPERTIES)
        .styleUrl(Optional.ofNullable(styleUrl))
        .removeZoomLevelConstraints(removeZoomLevelConstraints)
        .center(center.map(TilePoint::getCoordinates).orElse(ImmutableList.of()))
        .zoom(center.flatMap(TilePoint::getTileMatrix).map(Double::parseDouble))
        .build();
  }

  private Multimap<String, List<String>> getLayers(TileSet tileSet) {
    return tileSet.getLayers().stream()
        .filter(layer -> layer.getDataType() == DataType.vector)
        .map(
            layer ->
                layer
                    .getGeometryDimension()
                    .map(
                        dim ->
                            new SimpleImmutableEntry<>(
                                layer.getId(), ImmutableList.of(GEOMETRY_TYPES.get(dim))))
                    .orElse(
                        new SimpleImmutableEntry<>(
                            layer.getId(),
                            ImmutableList.of(
                                GEOMETRY_TYPES.get(0),
                                GEOMETRY_TYPES.get(1),
                                GEOMETRY_TYPES.get(2)))))
        .collect(
            ImmutableSetMultimap.toImmutableSetMultimap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private String getDefaultLevel(double lonDiff, int maxLevel) {
    int defaultLevel = 0;
    double dx = 360;
    while (dx > lonDiff && defaultLevel <= maxLevel) {
      dx = dx / 2.0;
      defaultLevel++;
    }
    return String.valueOf(defaultLevel);
  }
}
