/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.infra;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.tiles.api.AbstractEndpointTileSetDataset;
import de.ii.ogcapi.tiles.domain.TileSetFormatExtension;
import de.ii.ogcapi.tiles.domain.TilesConfiguration;
import de.ii.ogcapi.tiles.domain.TilesProviders;
import de.ii.ogcapi.tiles.domain.TilesQueriesHandler;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @title Dataset Map Tileset
 * @path map/tiles/{tileMatrixSetId}
 * @langEn Access a dataset map tileset
 * @langDe Zugriff auf einen Rasterkachelsatz zum Datensatz
 * @ref:formats {@link de.ii.ogcapi.tiles.domain.TileSetFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointMapTileSetDataset extends AbstractEndpointTileSetDataset
    implements ConformanceClass, ApiExtensionHealth {

  private static final List<String> TAGS = ImmutableList.of("Access map tiles");

  @Inject
  EndpointMapTileSetDataset(
      ExtensionRegistry extensionRegistry,
      TilesQueriesHandler queryHandler,
      TilesProviders tilesProviders) {
    super(extensionRegistry, queryHandler, tilesProviders);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(TilesConfiguration.class)
        .filter(TilesConfiguration::isEnabled)
        .filter(cfg -> cfg.hasDatasetMapTiles(tilesProviders, apiData))
        .isPresent();
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of(
        "http://www.opengis.net/spec/ogcapi-tiles-1/1.0/conf/tileset",
        "http://www.opengis.net/spec/tms/2.0/conf/tilematrixsetlimits",
        "http://www.opengis.net/spec/tms/2.0/conf/tilesetmetadata",
        "http://www.opengis.net/spec/tms/2.0/conf/json-tilematrixsetlimits",
        "http://www.opengis.net/spec/tms/2.0/conf/json-tilesetmetadata");
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return TilesConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats = extensionRegistry.getExtensionsForType(TileSetFormatExtension.class);
    return formats;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    return computeDefinition(
        apiData,
        "map",
        ApiEndpointDefinition.SORT_PRIORITY_MAP_TILE_SET,
        "/map/tiles/{tileMatrixSetId}",
        getOperationId("getTileSet", "dataset", "map"),
        TAGS);
  }

  /**
   * retrieve tilejson for the raster tile sets
   *
   * @return a tilejson file
   */
  @Path("/tiles/{tileMatrixSetId}")
  @GET
  public Response getTileSet(
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @PathParam("tileMatrixSetId") String tileMatrixSetId) {

    return super.getTileSet(
        api.getData(),
        requestContext,
        "/map/tiles/{tileMatrixSetId}",
        Optional.empty(),
        tileMatrixSetId);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler, tilesProviders.getTileProviderOrThrow(apiData));
  }
}
