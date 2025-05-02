/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.api;

import static de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetsQueriesHandler.GROUP_TILES_READ;

import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiResourceSet;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.tiles.app.TilesBuildingBlock;
import de.ii.ogcapi.tiles.domain.ImmutableQueryInputTileSets.Builder;
import de.ii.ogcapi.tiles.domain.TileSetsFormatExtension;
import de.ii.ogcapi.tiles.domain.TilesProviders;
import de.ii.ogcapi.tiles.domain.TilesQueriesHandler;
import de.ii.xtraplatform.tiles.domain.TilesetMetadata;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;

public abstract class AbstractEndpointTileSetsDataset extends Endpoint {

  protected final TilesQueriesHandler queryHandler;
  protected final TilesProviders tilesProviders;

  public AbstractEndpointTileSetsDataset(
      ExtensionRegistry extensionRegistry,
      TilesQueriesHandler queryHandler,
      TilesProviders tilesProviders) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
    this.tilesProviders = tilesProviders;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats = extensionRegistry.getExtensionsForType(TileSetsFormatExtension.class);
    return formats;
  }

  protected ApiEndpointDefinition computeDefinition(
      OgcApiDataV2 apiData,
      String apiEntrypoint,
      int sortPriority,
      String path,
      String operationId,
      List<String> tags) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint(apiEntrypoint)
            .sortPriority(sortPriority);
    HttpMethods method = HttpMethods.GET;
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, path);
    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);
    String operationSummary = "retrieve a list of the available tile sets";
    Optional<String> operationDescription =
        Optional.of(
            "This operation fetches the list of multi-layer tile sets supported by this API.");
    ImmutableOgcApiResourceSet.Builder resourceBuilderSet =
        new ImmutableOgcApiResourceSet.Builder()
            .path(path)
            .pathParameters(pathParameters)
            .subResourceType("Tile Set");
    ApiOperation.getResource(
            apiData,
            path,
            false,
            queryParameters,
            ImmutableList.of(),
            getResponseContent(apiData),
            operationSummary,
            operationDescription,
            Optional.empty(),
            operationId,
            GROUP_TILES_READ,
            tags,
            TilesBuildingBlock.MATURITY,
            TilesBuildingBlock.SPEC)
        .ifPresent(operation -> resourceBuilderSet.putOperations(method.name(), operation));
    definitionBuilder.putResources(path, resourceBuilderSet.build());

    return definitionBuilder.build();
  }

  protected Response getTileSets(
      OgcApiDataV2 apiData,
      ApiRequestContext requestContext,
      String definitionPath,
      Optional<String> styleId,
      boolean onlyWebMercatorQuad) {

    if (!isEnabledForApi(apiData))
      throw new NotFoundException("Multi-collection tiles are not available in this API.");

    TilesetMetadata tilesetMetadata = tilesProviders.getTilesetMetadataOrThrow(apiData);

    styleId.ifPresent(
        id -> checkPathParameter(extensionRegistry, apiData, definitionPath, "styleId", id));

    TilesQueriesHandler.QueryInputTileSets queryInput =
        new Builder()
            .from(getGenericQueryInput(apiData))
            .tileMatrixSetIds(tilesetMetadata.getTileMatrixSets())
            .path(definitionPath)
            .styleId(styleId)
            .onlyWebMercatorQuad(onlyWebMercatorQuad)
            .tileEncodings(tilesetMetadata.getEncodings())
            .build();

    return queryHandler.handle(TilesQueriesHandler.Query.TILE_SETS, queryInput, requestContext);
  }
}
