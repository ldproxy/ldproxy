/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tilematrixsets.infra;

import static de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetsQueriesHandler.GROUP_TILES_READ;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiResourceAuxiliary;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.tilematrixsets.app.TileMatrixSetsBuildingBlock;
import de.ii.ogcapi.tilematrixsets.domain.ImmutableQueryInputTileMatrixSet;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetFormatExtension;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetsConfiguration;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetsQueriesHandler;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import java.util.List;
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
 * @title Tile Matrix Set
 * @path tileMatrixSets/{tileMatrixSetId}
 * @langEn Returns the definition of a tiling scheme.
 * @langDe Liefert die Definition eines Kachelschemas.
 * @ref:formats {@link de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointTileMatrixSet extends Endpoint implements ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointTileMatrixSet.class);
  private static final List<String> TAGS = ImmutableList.of("Discover and fetch tiling schemes");

  private final TileMatrixSetsQueriesHandler queryHandler;

  @Inject
  EndpointTileMatrixSet(
      ExtensionRegistry extensionRegistry, TileMatrixSetsQueriesHandler queryHandler) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return TileMatrixSetsConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats = extensionRegistry.getExtensionsForType(TileMatrixSetFormatExtension.class);
    return formats;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(TileMatrixSetsConfiguration.class)
        .filter(TileMatrixSetsConfiguration::isEnabled)
        .isPresent();
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("tileMatrixSets")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_TILE_MATRIX_SETS);
    String path = "/tileMatrixSets/{tileMatrixSetId}";
    HttpMethods method = HttpMethods.GET;
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, path);
    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);
    if (pathParameters.stream().noneMatch(param -> param.getName().equals("tileMatrixSetId"))) {
      LOGGER.error(
          "Path parameter 'tileMatrixSetId' missing for resource at path '"
              + path
              + "'. The GET method will not be available.");
    } else {
      String operationSummary = "fetch information about the tiling scheme `{tileMatrixSetId}`";
      Optional<String> operationDescription =
          Optional.of(
              "Returns the definition of the tiling scheme according to the [OGC Two Dimensional Tile Matrix Set standard](https://docs.ogc.org/is/17-083r2/17-083r2.html).");
      ImmutableOgcApiResourceAuxiliary.Builder resourceBuilder =
          new ImmutableOgcApiResourceAuxiliary.Builder().path(path).pathParameters(pathParameters);
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
              getOperationId("getTileMatrixSet"),
              GROUP_TILES_READ,
              TAGS,
              TileMatrixSetsBuildingBlock.MATURITY,
              TileMatrixSetsBuildingBlock.SPEC)
          .ifPresent(operation -> resourceBuilder.putOperations(method.name(), operation));
      definitionBuilder.putResources(path, resourceBuilder.build());
    }

    return definitionBuilder.build();
  }

  /**
   * retrieve one specific tile matrix set by id
   *
   * @param tileMatrixSetId the local identifier of a specific tile matrix set
   * @return the tiling scheme in a json file
   */
  @Path("/{tileMatrixSetId}")
  @GET
  public Response getTileMatrixSet(
      @PathParam("tileMatrixSetId") String tileMatrixSetId,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext) {

    checkPathParameter(
        extensionRegistry,
        api.getData(),
        "/tileMatrixSets/{tileMatrixSetId}",
        "tileMatrixSetId",
        tileMatrixSetId);

    TileMatrixSetsQueriesHandler.QueryInputTileMatrixSet queryInput =
        new ImmutableQueryInputTileMatrixSet.Builder()
            .from(getGenericQueryInput(api.getData()))
            .tileMatrixSetId(tileMatrixSetId)
            .build();

    return queryHandler.handle(
        TileMatrixSetsQueriesHandler.Query.TILE_MATRIX_SET, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler);
  }
}
