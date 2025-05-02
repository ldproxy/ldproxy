/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.api;

import static de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetsQueriesHandler.GROUP_TILES_READ;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.collections.domain.EndpointSubCollection;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiResourceData;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetLimitsGenerator;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetLimitsOgcApi;
import de.ii.ogcapi.tiles.app.TilesBuildingBlock;
import de.ii.ogcapi.tiles.domain.ImmutableQueryInputTile;
import de.ii.ogcapi.tiles.domain.TileFormatExtension;
import de.ii.ogcapi.tiles.domain.TilesProviders;
import de.ii.xtraplatform.tiles.domain.MinMax;
import de.ii.xtraplatform.tiles.domain.TileMatrixSet;
import de.ii.xtraplatform.tiles.domain.TileMatrixSetRepository;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoMultiBind
public interface EndpointTileMixin {

  Logger LOGGER = LoggerFactory.getLogger(EndpointTileMixin.class);
  String COLLECTION_ID_PLACEHOLDER = "__collectionId__";

  default ApiEndpointDefinition computeDefinitionSingle(
      ExtensionRegistry extensionRegistry,
      EndpointSubCollection endpoint,
      OgcApiDataV2 apiData,
      TilesProviders tilesProviders,
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
        endpoint.getPathParameters(extensionRegistry, apiData, path);
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
            endpoint.getQueryParameters(extensionRegistry, apiData, path, collectionId);
        String operationSummary = "fetch a tile of the collection '" + collectionId + "'";
        Optional<String> operationDescription =
            Optional.of(
                "The tile in the requested tiling scheme ('{tileMatrixSetId}'), "
                    + "on the requested zoom level ('{tileMatrix}'), with the requested grid coordinates ('{tileRow}', '{tileCol}') is returned. "
                    + "The tile has a single layer with all selected features in the bounding box of the tile with the requested properties.");
        String resourcePath = path.replace("{collectionId}", collectionId);
        ImmutableOgcApiResourceData.Builder resourceBuilder =
            new ImmutableOgcApiResourceData.Builder()
                .path(resourcePath)
                .pathParameters(pathParameters);
        Map<MediaType, ApiMediaTypeContent> responseContent =
            getContent(endpoint, apiData, Optional.of(collectionId), path);
        String operationId =
            getOperationId(operationIdWithPlaceholders, collectionId, apiData, tilesProviders);
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

  default ApiEndpointDefinition computeDefinitionMulti(
      ExtensionRegistry extensionRegistry,
      Endpoint endpoint,
      OgcApiDataV2 apiData,
      TilesProviders tilesProviders,
      String apiEntrypoint,
      int sortPriority,
      String path,
      String operationIdWithPlaceholders,
      List<String> tags) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint(apiEntrypoint)
            .sortPriority(sortPriority);
    final HttpMethods method = HttpMethods.GET;
    final List<OgcApiPathParameter> pathParameters =
        endpoint.getPathParameters(extensionRegistry, apiData, path);
    final List<OgcApiQueryParameter> queryParameters =
        endpoint.getQueryParameters(extensionRegistry, apiData, path);
    String operationSummary = "fetch a tile with multiple layers, one per collection";
    Optional<String> operationDescription =
        Optional.of(
            "The tile in the requested tiling scheme ('{tileMatrixSetId}'), "
                + "on the requested zoom level ('{tileMatrix}'), with the requested grid coordinates ('{tileRow}', '{tileCol}') is returned. "
                + "The tile has one layer per collection with all selected features in the bounding box of the tile with the requested properties.");
    ImmutableOgcApiResourceData.Builder resourceBuilder =
        new ImmutableOgcApiResourceData.Builder().path(path).pathParameters(pathParameters);
    Map<MediaType, ApiMediaTypeContent> responseContent =
        getContent(endpoint, apiData, Optional.empty(), path);
    String operationId = getOperationId(operationIdWithPlaceholders, "", apiData, tilesProviders);
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
            operationId,
            GROUP_TILES_READ,
            tags,
            TilesBuildingBlock.MATURITY,
            TilesBuildingBlock.SPEC)
        .ifPresent(operation -> resourceBuilder.putOperations(method.name(), operation));
    definitionBuilder.putResources(path, resourceBuilder.build());

    return definitionBuilder.build();
  }

  default QueryInput getQueryInputTile(
      ExtensionRegistry extensionRegistry,
      Endpoint endpoint,
      TileMatrixSetLimitsGenerator limitsGenerator,
      TileMatrixSetRepository tileMatrixSetRepository,
      OgcApi api,
      TilesProviders tilesProviders,
      ApiRequestContext requestContext,
      String definitionPath,
      Optional<String> collectionId,
      Optional<String> styleId,
      String tileMatrixSetId,
      String tileLevel,
      String tileRow,
      String tileCol)
      throws NotFoundException {
    OgcApiDataV2 apiData = api.getData();
    Optional<FeatureTypeConfigurationOgcApi> collectionData =
        collectionId.map(id -> apiData.getCollections().get(id));

    collectionId.ifPresent(
        id ->
            endpoint.checkPathParameter(
                extensionRegistry, apiData, definitionPath, "collectionId", id));
    styleId.ifPresent(
        id ->
            endpoint.checkPathParameter(extensionRegistry, apiData, definitionPath, "styleId", id));
    endpoint.checkPathParameter(
        extensionRegistry, apiData, definitionPath, "tileMatrixSetId", tileMatrixSetId);
    endpoint.checkPathParameter(
        extensionRegistry, apiData, definitionPath, "tileMatrix", tileLevel);
    endpoint.checkPathParameter(extensionRegistry, apiData, definitionPath, "tileRow", tileRow);
    endpoint.checkPathParameter(extensionRegistry, apiData, definitionPath, "tileCol", tileCol);

    int row;
    int col;
    int level;
    try {
      level = Integer.parseInt(tileLevel);
      row = Integer.parseInt(tileRow);
      col = Integer.parseInt(tileCol);
    } catch (NumberFormatException e) {
      throw new ServerErrorException(
          "Could not convert tile coordinates that have been validated to integers", 500);
    }

    TileMatrixSet tileMatrixSet =
        tileMatrixSetRepository
            .get(tileMatrixSetId)
            .orElseThrow(
                () -> new NotFoundException("Unknown tile matrix set: " + tileMatrixSetId));

    MinMax zoomLevels =
        tilesProviders
            .getTilesetMetadataOrThrow(apiData, collectionData)
            .getLevels()
            .get(tileMatrixSetId);
    if (zoomLevels.getMax() < level || zoomLevels.getMin() > level)
      throw new NotFoundException(
          "The requested tile is outside the zoom levels for this tile set.");

    TileMatrixSetLimitsOgcApi tileLimits =
        limitsGenerator.getTileMatrixSetLimits(api, tileMatrixSet, level, collectionId);

    if (tileLimits != null) {
      if (tileLimits.getMaxTileCol() < col
          || tileLimits.getMinTileCol() > col
          || tileLimits.getMaxTileRow() < row
          || tileLimits.getMinTileRow() > row)
        // return 404, if outside the range
        throw new NotFoundException(
            "The requested tile is outside of the limits for this zoom level and tile set.");
    }

    String path =
        definitionPath
            .replace("{collectionId}", collectionId.orElse(""))
            .replace("{tileMatrixSetId}", tileMatrixSetId)
            .replace("{tileMatrix}", tileLevel)
            .replace("{tileRow}", tileRow)
            .replace("{tileCol}", tileCol);

    TileFormatExtension outputFormat =
        requestContext
            .getApi()
            .getOutputFormat(TileFormatExtension.class, requestContext.getMediaType(), collectionId)
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    return new ImmutableQueryInputTile.Builder()
        .from(endpoint.getGenericQueryInput(apiData))
        .collectionId(collectionId)
        .styleId(styleId)
        .outputFormat(outputFormat)
        .tileMatrixSet(tileMatrixSet)
        .level(level)
        .row(row)
        .col(col)
        .parameters(requestContext.getQueryParameterSet())
        .build();
  }

  private static Map<MediaType, ApiMediaTypeContent> getContent(
      Endpoint endpoint, OgcApiDataV2 apiData, Optional<String> optionalCollectionId, String path) {
    return endpoint.getResourceFormats().stream()
        .filter(
            outputFormatExtension ->
                optionalCollectionId
                    .map(
                        collectionId ->
                            collectionId.equals("{collectionId}")
                                ? apiData.getCollections().keySet().stream()
                                    .anyMatch(
                                        cid ->
                                            outputFormatExtension.isEnabledForApi(apiData, cid)
                                                && (!(outputFormatExtension
                                                        instanceof TileFormatExtension)
                                                    || ((TileFormatExtension) outputFormatExtension)
                                                        .isApplicable(apiData, cid, path)))
                                : outputFormatExtension.isEnabledForApi(apiData, collectionId)
                                    && (!(outputFormatExtension instanceof TileFormatExtension)
                                        || ((TileFormatExtension) outputFormatExtension)
                                            .isApplicable(apiData, collectionId, path)))
                    .orElse(
                        outputFormatExtension.isEnabledForApi(apiData)
                            && (!(outputFormatExtension instanceof TileFormatExtension)
                                || ((TileFormatExtension) outputFormatExtension)
                                    .isApplicable(apiData, path))))
        .map(FormatExtension::getContent)
        .filter(Objects::nonNull)
        .collect(Collectors.toMap(c -> c.getOgcApiMediaType().type(), c -> c));
  }

  static String getOperationId(
      String id, String collectionId, OgcApiDataV2 apiData, TilesProviders tilesProviders) {
    return collectionId.startsWith("{")
        ? id.replace(EndpointTileMixin.COLLECTION_ID_PLACEHOLDER + ".", "")
        : id.replace(EndpointTileMixin.COLLECTION_ID_PLACEHOLDER, collectionId);
  }
}
