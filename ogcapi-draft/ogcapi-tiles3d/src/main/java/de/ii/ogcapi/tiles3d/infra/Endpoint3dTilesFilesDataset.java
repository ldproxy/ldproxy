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
import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.tiles3d.app.FormatAny;
import de.ii.ogcapi.tiles3d.app.Tiles3dBuildingBlock;
import de.ii.ogcapi.tiles3d.domain.ImmutableQueryInputFile;
import de.ii.ogcapi.tiles3d.domain.QueriesHandler3dTiles;
import de.ii.ogcapi.tiles3d.domain.QueriesHandler3dTiles.Query;
import de.ii.ogcapi.tiles3d.domain.QueriesHandler3dTiles.QueryInputFile;
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
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title 3D Tiles Files
 * @path 3dtiles/{subPath:.*}
 * @langEn Access a file in a 3D Tiles tileset.
 * @langDe Zugriff auf eine Datei in einem 3D-Tiles Tileset.
 */
@Singleton
@AutoBind
public class Endpoint3dTilesFilesDataset extends Endpoint implements ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(Endpoint3dTilesFilesDataset.class);

  private static final List<String> TAGS = ImmutableList.of("Access data as 3D Tiles");

  private final Tile3dProviders tile3dProviders;
  private final QueriesHandler3dTiles queryHandler;

  @Inject
  public Endpoint3dTilesFilesDataset(
      ExtensionRegistry extensionRegistry,
      Tile3dProviders tile3dProviders,
      QueriesHandler3dTiles queryHandler) {
    super(extensionRegistry);
    this.tile3dProviders = tile3dProviders;
    this.queryHandler = queryHandler;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return super.isEnabledForApi(apiData)
        && apiData
            .getExtension(Tiles3dConfiguration.class)
            .filter(cfg -> cfg.hasDatasetTiles(tile3dProviders, apiData))
            .isPresent();
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return Tiles3dConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    return List.of(FormatAny.INSTANCE);
  }

  @Override
  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .hidden(true)
            .apiEntrypoint("3dtiles")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_3D_TILES_CONTENT);
    String path = "/3dtiles/{subPath}";
    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, path);
    String operationSummary = "retrieve a file of the 3D Tiles tileset";
    Optional<String> operationDescription = Optional.of("Access a file in a 3D Tiles tileset.");
    ImmutableOgcApiResourceData.Builder resourceBuilder =
        new ImmutableOgcApiResourceData.Builder().path(path).pathParameters(pathParameters);
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
            getOperationId("get3dTilesFile"),
            GROUP_TILES_READ,
            TAGS,
            Tiles3dBuildingBlock.MATURITY,
            Tiles3dBuildingBlock.SPEC)
        .ifPresent(operation -> resourceBuilder.putOperations(HttpMethods.GET.name(), operation));
    definitionBuilder.putResources(path, resourceBuilder.build());

    return definitionBuilder.build();
  }

  @GET
  @Path("/{subPath:.*}")
  @SuppressWarnings("PMD.UseObjectForClearerAPI")
  public Response getContent(
      @Auth Optional<User> optionalUser,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @Context UriInfo uriInfo,
      @PathParam("subPath") String subPath) {
    QueryInputFile queryInput =
        ImmutableQueryInputFile.builder()
            .from(getGenericQueryInput(api.getData()))
            .path(subPath)
            .build();

    return queryHandler.handle(Query.FILE, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(tile3dProviders.getTile3dProviderOrThrow(apiData));
  }
}
