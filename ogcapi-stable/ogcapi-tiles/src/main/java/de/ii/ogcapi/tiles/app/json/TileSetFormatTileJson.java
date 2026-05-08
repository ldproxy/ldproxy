/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.app.json;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiMetadata;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ClassSchemaCache;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetLimitsOgcApi;
import de.ii.ogcapi.tiles.domain.ImmutableTileJson;
import de.ii.ogcapi.tiles.domain.TileJson;
import de.ii.ogcapi.tiles.domain.TilePoint;
import de.ii.ogcapi.tiles.domain.TileSet;
import de.ii.ogcapi.tiles.domain.TileSet.DataType;
import de.ii.ogcapi.tiles.domain.TileSetFormatExtension;
import de.ii.ogcapi.tiles.domain.TilesConfiguration;
import de.ii.ogcapi.tiles.domain.TilesProviders;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.tiles.domain.TilesBoundingBox;
import de.ii.xtraplatform.tiles.domain.TilesetMetadata;
import de.ii.xtraplatform.tiles.domain.VectorLayer;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @title TileJSON
 */
@Singleton
@AutoBind
public class TileSetFormatTileJson implements TileSetFormatExtension {

  public static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("application", "vnd.mapbox.tile+json"))
          .label("TileJSON")
          .parameter("tilejson")
          .fileExtension("tile.json")
          .build();

  private final Schema<?> schemaTileJson;
  private final Map<String, Schema<?>> referencedSchemas;
  private final TilesProviders tilesProviders;

  @Inject
  public TileSetFormatTileJson(ClassSchemaCache classSchemaCache, TilesProviders tilesProviders) {
    schemaTileJson = classSchemaCache.getSchema(TileJson.class);
    referencedSchemas = classSchemaCache.getReferencedSchemas(TileJson.class);
    this.tilesProviders = tilesProviders;
  }

  @Override
  public ApiMediaType getMediaType() {
    return MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(schemaTileJson)
        .schemaRef(TileJson.SCHEMA_REF)
        .referencedSchemas(referencedSchemas)
        .ogcApiMediaType(MEDIA_TYPE)
        .build();
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return TilesConfiguration.class;
  }

  @Override
  public Object getTileSetEntity(
      TileSet tileset,
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      ApiRequestContext requestContext) {
    List<VectorLayer> vectorLayers = ImmutableList.of();
    if (tileset.getDataType() == DataType.vector) {
      Optional<TilesetMetadata> tilesetMetadata =
          tilesProviders.getTilesetMetadata(
              apiData, collectionId.flatMap(apiData::getCollectionData));
      List<FeatureSchema> vectorSchemas =
          tilesetMetadata.map(TilesetMetadata::getVectorSchemas).orElse(List.of());
      vectorLayers =
          vectorSchemas.stream()
              .map(
                  schema ->
                      VectorLayer.of(
                          schema,
                          tilesetMetadata.flatMap(
                              metadata ->
                                  Optional.ofNullable(
                                      metadata.getLevels().get(tileset.getTileMatrixSetId())))))
              .collect(Collectors.toList());
    }

    // TODO: add support for version (manage revisions to the data)
    return ImmutableTileJson.builder()
        .tilejson("3.0.0")
        .name(apiData.getLabel())
        .description(apiData.getDescription())
        .tiles(ImmutableList.of(getTilesUriTemplate(tileset)))
        .bounds(getBounds(tileset))
        .minzoom(getMinzoom(tileset))
        .maxzoom(getMaxzoom(tileset))
        .center(getCenter(tileset))
        .vectorLayers(vectorLayers)
        .attribution(apiData.getMetadata().flatMap(ApiMetadata::getAttribution))
        .build();
  }

  private String getTilesUriTemplate(TileSet tileset) {
    return tileset.getLinks().stream()
        .filter(link -> link.getRel().equalsIgnoreCase("item"))
        .findFirst()
        .map(Link::getHref)
        .orElseThrow(
            () ->
                new RuntimeException("No tile URI template with link relation type 'item' found."))
        .replace("{tileMatrixSetId}", tileset.getTileMatrixSetId())
        .replace("{tileMatrix}", "{z}")
        .replace("{tileRow}", "{y}")
        .replace("{tileCol}", "{x}");
  }

  /**
   * derive the bbox as a sequence left, bottom, right, upper
   *
   * @param tileset the tile set metadata according to the OGC Tile Matrix Set standard
   * @return the bbox
   */
  private static List<Double> getBounds(TileSet tileset) {
    TilesBoundingBox bbox = tileset.getBoundingBox();
    return ImmutableList.of(
        bbox.getLowerLeft()[0].doubleValue(),
        bbox.getLowerLeft()[1].doubleValue(),
        bbox.getUpperRight()[0].doubleValue(),
        bbox.getUpperRight()[1].doubleValue());
  }

  /**
   * derive the minimum zoom level
   *
   * @param tileset the tile set metadata according to the OGC Tile Matrix Set standard
   * @return the zoom level
   */
  private static Optional<Integer> getMinzoom(TileSet tileset) {
    return tileset.getTileMatrixSetLimits().stream()
        .map(TileMatrixSetLimitsOgcApi::getTileMatrix)
        .map(Integer::valueOf)
        .min(Integer::compareTo);
  }

  /**
   * derive the maximum zoom level
   *
   * @param tileset the tile set metadata according to the OGC Tile Matrix Set standard
   * @return the zoom level
   */
  private static Optional<Integer> getMaxzoom(TileSet tileset) {
    return tileset.getTileMatrixSetLimits().stream()
        .map(TileMatrixSetLimitsOgcApi::getTileMatrix)
        .map(Integer::valueOf)
        .max(Integer::compareTo);
  }

  /**
   * derive the default view as longitude, latitude, zoom level
   *
   * @param tileset the tile set metadata according to the OGC Tile Matrix Set standard
   * @return the default view
   */
  private static List<Number> getCenter(TileSet tileset) {
    TilesBoundingBox bbox = tileset.getBoundingBox();
    double centerLon =
        tileset
            .getCenterPoint()
            .map(TilePoint::getCoordinates)
            .filter(coord -> coord.size() >= 2)
            .map(coord -> coord.get(0))
            .orElse(
                bbox.getLowerLeft()[0].doubleValue()
                    + (bbox.getUpperRight()[0].doubleValue() - bbox.getLowerLeft()[0].doubleValue())
                        * 0.5);
    double centerLat =
        tileset
            .getCenterPoint()
            .map(TilePoint::getCoordinates)
            .filter(coord -> coord.size() >= 2)
            .map(coord -> coord.get(1))
            .orElse(
                bbox.getLowerLeft()[1].doubleValue()
                    + (bbox.getUpperRight()[1].doubleValue() - bbox.getLowerLeft()[1].doubleValue())
                        * 0.5);
    int defaultZoomLevel =
        tileset
            .getCenterPoint()
            .map(TilePoint::getTileMatrix)
            .flatMap(level -> level)
            .map(Integer::valueOf)
            .orElse(0);
    return ImmutableList.of(centerLon, centerLat, defaultZoomLevel);
  }
}
