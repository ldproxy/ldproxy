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
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.tiles.api.AbstractEndpointTileSetsCollection;
import de.ii.ogcapi.tiles.api.EndpointTileMixin;
import de.ii.ogcapi.tiles.domain.TilesConfiguration;
import de.ii.ogcapi.tiles.domain.TilesProviders;
import de.ii.ogcapi.tiles.domain.TilesQueriesHandler;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Collection Map Tilesets
 * @path collections/{collectionId}/styles/{styleId}/map/tiles
 * @langEn Access collection map tilesets in a specific style.
 * @langDe Zugriff auf Rasterkachelsätze einer Feature Collection in einem bestimmten Style.
 * @ref:formats {@link de.ii.ogcapi.tiles.domain.TileSetsFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointStyledMapTileSetsCollection extends AbstractEndpointTileSetsCollection
    implements ConformanceClass, ApiExtensionHealth {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(EndpointStyledMapTileSetsCollection.class);

  private static final List<String> TAGS = ImmutableList.of("Access map tiles");

  @Inject
  EndpointStyledMapTileSetsCollection(
      ExtensionRegistry extensionRegistry,
      TilesQueriesHandler queryHandler,
      TilesProviders tilesProviders) {
    super(extensionRegistry, queryHandler, tilesProviders);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    FeatureTypeConfigurationOgcApi collectionData = apiData.getCollections().get(collectionId);
    return Objects.nonNull(collectionData)
        && collectionData.getEnabled()
        && collectionData
            .getExtension(TilesConfiguration.class)
            .filter(TilesConfiguration::isEnabled)
            .filter(cfg -> cfg.hasCollectionStyledMapTiles(tilesProviders, apiData, collectionId))
            .isPresent();
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of(
        "http://www.opengis.net/spec/ogcapi-tiles-1/1.0/conf/tilesets-list",
        "http://www.opengis.net/spec/ogcapi-tiles-1/1.0/conf/geodata-tilesets");
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return TilesConfiguration.class;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    return computeDefinition(
        apiData,
        "collections",
        ApiEndpointDefinition.SORT_PRIORITY_MAP_TILE_SETS_COLLECTION,
        "/collections/{collectionId}",
        "/styles/{styleId}/map/tiles",
        getOperationId(
            "getTileSetsList",
            EndpointTileMixin.COLLECTION_ID_PLACEHOLDER,
            "collection",
            "style",
            "map"),
        TAGS);
  }

  @Path("/{collectionId}/styles/{styleId}/map/tiles")
  @GET
  public Response getTileSets(
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @PathParam("collectionId") String collectionId,
      @PathParam("styleId") String styleId) {
    return super.getTileSets(
        api.getData(),
        requestContext,
        "/collections/{collectionId}/styles/{styleId}/map/tiles",
        collectionId,
        Optional.of(styleId),
        false);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler, tilesProviders.getTileProviderOrThrow(apiData));
  }
}
