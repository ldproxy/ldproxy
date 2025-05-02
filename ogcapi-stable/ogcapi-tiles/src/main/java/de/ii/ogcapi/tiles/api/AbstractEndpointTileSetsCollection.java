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
import de.ii.ogcapi.collections.domain.EndpointSubCollection;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
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
import de.ii.ogcapi.tiles.domain.TilesConfiguration;
import de.ii.ogcapi.tiles.domain.TilesProviders;
import de.ii.ogcapi.tiles.domain.TilesQueriesHandler;
import de.ii.xtraplatform.tiles.domain.TilesetMetadata;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractEndpointTileSetsCollection extends EndpointSubCollection {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(AbstractEndpointTileSetsCollection.class);

  protected final TilesQueriesHandler queryHandler;
  protected final TilesProviders tilesProviders;

  public AbstractEndpointTileSetsCollection(
      ExtensionRegistry extensionRegistry,
      TilesQueriesHandler queryHandler,
      TilesProviders tilesProviders) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
    this.tilesProviders = tilesProviders;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return apiData
        .getCollectionData(collectionId)
        .flatMap(cfg -> cfg.getExtension(TilesConfiguration.class))
        .filter(TilesConfiguration::isEnabled)
        .filter(cfg -> cfg.hasCollectionTiles(tilesProviders, apiData, collectionId))
        .isPresent();
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
      String basePath,
      String subSubPath,
      String operationIdWithPlaceholders,
      List<String> tags) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint(apiEntrypoint)
            .sortPriority(sortPriority);
    final String path = basePath + subSubPath;
    final HttpMethods method = HttpMethods.GET;
    final List<OgcApiPathParameter> pathParameters =
        getPathParameters(extensionRegistry, apiData, path);
    final Optional<OgcApiPathParameter> optCollectionIdParam =
        pathParameters.stream().filter(param -> param.getName().equals("collectionId")).findAny();
    if (optCollectionIdParam.isEmpty()) {
      LOGGER.error(
          "Path parameter 'collectionId' missing for resource at path '"
              + path
              + "'. The GET method will not be available.");
    } else {
      final OgcApiPathParameter collectionIdParam = optCollectionIdParam.get();
      boolean explode = collectionIdParam.isExplodeInOpenApi(apiData);
      final List<String> collectionIds =
          (explode) ? collectionIdParam.getValues(apiData) : ImmutableList.of("{collectionId}");
      for (String collectionId : collectionIds) {
        List<OgcApiQueryParameter> queryParameters =
            getQueryParameters(extensionRegistry, apiData, path, collectionId);
        String operationSummary = "retrieve a list of the available tile sets";
        Optional<String> operationDescription =
            Optional.of(
                "This operation fetches the list of tile sets available for this collection.");
        String resourcePath = path.replace("{collectionId}", collectionId);
        ImmutableOgcApiResourceSet.Builder resourceBuilder =
            new ImmutableOgcApiResourceSet.Builder()
                .path(resourcePath)
                .pathParameters(pathParameters)
                .subResourceType("Tile Set");
        Map<MediaType, ApiMediaTypeContent> responseContent = getResponseContent(apiData);
        String operationId =
            EndpointTileMixin.getOperationId(
                operationIdWithPlaceholders, collectionId, apiData, tilesProviders);
        ApiOperation.getResource(
                apiData,
                resourcePath,
                false,
                queryParameters,
                ImmutableList.of(),
                responseContent,
                operationSummary,
                operationDescription,
                Optional.empty(),
                operationId,
                GROUP_TILES_READ,
                tags,
                TilesBuildingBlock.MATURITY,
                TilesBuildingBlock.SPEC)
            .ifPresent(
                operation -> resourceBuilder.putOperations(HttpMethods.GET.name(), operation));
        definitionBuilder.putResources(resourcePath, resourceBuilder.build());
      }
    }

    return definitionBuilder.build();
  }

  protected Response getTileSets(
      OgcApiDataV2 apiData,
      ApiRequestContext requestContext,
      String definitionPath,
      String collectionId,
      Optional<String> styleId,
      boolean onlyWebMercatorQuad) {

    checkPathParameter(extensionRegistry, apiData, definitionPath, "collectionId", collectionId);
    styleId.ifPresent(
        id -> checkPathParameter(extensionRegistry, apiData, definitionPath, "styleId", id));

    TilesetMetadata tilesetMetadata =
        tilesProviders.getTilesetMetadataOrThrow(apiData, apiData.getCollectionData(collectionId));

    TilesQueriesHandler.QueryInputTileSets queryInput =
        new Builder()
            .from(getGenericQueryInput(apiData))
            .collectionId(collectionId)
            .styleId(styleId)
            .tileMatrixSetIds(tilesetMetadata.getTileMatrixSets())
            .path(definitionPath)
            .onlyWebMercatorQuad(onlyWebMercatorQuad)
            .tileEncodings(tilesetMetadata.getEncodings())
            .build();

    return queryHandler.handle(TilesQueriesHandler.Query.TILE_SETS, queryInput, requestContext);
  }
}
