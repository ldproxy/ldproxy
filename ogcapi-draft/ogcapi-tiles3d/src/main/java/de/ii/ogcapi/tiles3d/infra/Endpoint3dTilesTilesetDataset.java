/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.infra;

import static de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetsQueriesHandler.GROUP_TILES_READ;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiResourceData;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.tiles3d.app.Tiles3dBuildingBlock;
import de.ii.ogcapi.tiles3d.domain.Format3dTilesTileset;
import de.ii.ogcapi.tiles3d.domain.ImmutableQueryInputTileset;
import de.ii.ogcapi.tiles3d.domain.QueriesHandler3dTiles;
import de.ii.ogcapi.tiles3d.domain.QueriesHandler3dTiles.Query;
import de.ii.ogcapi.tiles3d.domain.QueriesHandler3dTiles.QueryInputTileset;
import de.ii.ogcapi.tiles3d.domain.Tile3dProviders;
import de.ii.ogcapi.tiles3d.domain.Tiles3dConfiguration;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import io.dropwizard.auth.Auth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title 3D Tiles Tileset
 * @path 3dtiles
 * @langEn Access a 3D Tiles 1.0 or 1.1 tileset.
 * @langDe Zugriff auf einen Kachelsatz gemäß 3D Tiles 1.0 oder 1.1.
 * @ref:formats {@link de.ii.ogcapi.tiles3d.domain.Format3dTilesTileset}
 */
@Singleton
@AutoBind
public class Endpoint3dTilesTilesetDataset extends Endpoint
    implements ConformanceClass, ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(Endpoint3dTilesTilesetDataset.class);

  private static final List<String> TAGS = ImmutableList.of("Access data as 3D Tiles");

  private final QueriesHandler3dTiles queryHandler;
  private final Tile3dProviders tile3dProviders;

  @Inject
  public Endpoint3dTilesTilesetDataset(
      ExtensionRegistry extensionRegistry,
      QueriesHandler3dTiles queryHandler,
      Tile3dProviders tile3dProviders) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
    this.tile3dProviders = tile3dProviders;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return apiData
            .getExtension(Tiles3dConfiguration.class)
            .filter(cfg -> cfg.isEnabled() && cfg.hasDatasetTiles(tile3dProviders, apiData))
            .isPresent()
        && tile3dProviders.getTileset3dMetadata(apiData).isPresent();
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of("http://www.opengis.net/spec/ogcapi-geovolumes-1/0.0/conf/core");
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return Tiles3dConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null) {
      formats = extensionRegistry.getExtensionsForType(Format3dTilesTileset.class);
    }
    return formats;
  }

  @Override
  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("3dtiles")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_3D_TILES);
    String path = "/3dtiles";
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, path);
    String operationSummary = "retrieve the root 3D Tiles tileset of the dataset";
    Optional<String> operationDescription = Optional.of("Access a 3D Tiles 1.0 or 1.1 tileset.");
    ImmutableOgcApiResourceData.Builder resourceBuilder =
        new ImmutableOgcApiResourceData.Builder().path(path);
    Map<MediaType, ApiMediaTypeContent> responseContent = getResponseContent(apiData);
    ApiOperation.getResource(
            apiData,
            path,
            false,
            queryParameters,
            ImmutableList.of(),
            responseContent,
            operationSummary,
            operationDescription,
            Optional.empty(),
            getOperationId("get3dTileset"),
            GROUP_TILES_READ,
            TAGS,
            Tiles3dBuildingBlock.MATURITY,
            Tiles3dBuildingBlock.SPEC)
        .ifPresent(operation -> resourceBuilder.putOperations(HttpMethods.GET.name(), operation));
    definitionBuilder.putResources(path, resourceBuilder.build());

    return definitionBuilder.build();
  }

  @GET
  public Response get3dTiles(
      @Auth Optional<User> optionalUser,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @Context UriInfo uriInfo) {
    QueryInputTileset queryInput =
        ImmutableQueryInputTileset.builder().from(getGenericQueryInput(api.getData())).build();

    return queryHandler.handle(Query.TILESET, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(tile3dProviders.getTile3dProviderOrThrow(apiData));
  }
}
