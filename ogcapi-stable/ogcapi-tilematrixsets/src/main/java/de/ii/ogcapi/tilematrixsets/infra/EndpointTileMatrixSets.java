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
import com.google.common.collect.ImmutableSet;
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
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiResourceSet;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.tilematrixsets.app.TileMatrixSetsBuildingBlock;
import de.ii.ogcapi.tilematrixsets.domain.ImmutableQueryInputTileMatrixSets;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetsConfiguration;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetsFormatExtension;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetsQueriesHandler;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Tile Matrix Sets
 * @path tileMatrixSets
 * @langEn Returns the list of tiling schemes.
 * @langDe Liefert die Liste der Kachelschemas.
 * @ref:formats {@link de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetsFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointTileMatrixSets extends Endpoint implements ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointTileMatrixSets.class);
  private static final List<String> TAGS = ImmutableList.of("Discover and fetch tiling schemes");

  private final TileMatrixSetsQueriesHandler queryHandler;

  @Inject
  EndpointTileMatrixSets(
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
      formats = extensionRegistry.getExtensionsForType(TileMatrixSetsFormatExtension.class);
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
    String path = "/tileMatrixSets";
    HttpMethods method = HttpMethods.GET;
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, path);
    String operationSummary = "retrieve a list of the available tiling schemes";
    Optional<String> operationDescription =
        Optional.of(
            "This operation fetches the set of tiling schemes supported by this API. "
                + "For each tiling scheme the id, a title and the link to the tiling scheme object is provided.");
    ImmutableOgcApiResourceSet.Builder resourceBuilderSet =
        new ImmutableOgcApiResourceSet.Builder().path(path).subResourceType("Tile Matrix Set");
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
            getOperationId("getTileMatrixSets"),
            GROUP_TILES_READ,
            TAGS,
            TileMatrixSetsBuildingBlock.MATURITY,
            TileMatrixSetsBuildingBlock.SPEC)
        .ifPresent(operation -> resourceBuilderSet.putOperations(method.name(), operation));
    definitionBuilder.putResources(path, resourceBuilderSet.build());

    return definitionBuilder.build();
  }

  /**
   * retrieve all available tile matrix sets
   *
   * @return all tile matrix sets in a json array or an HTML view
   */
  @GET
  public Response getTileMatrixSets(
      @Context OgcApi api, @Context ApiRequestContext requestContext) {

    if (!isEnabledForApi(api.getData()))
      throw new NotFoundException("Tile matrix sets are not available in this API.");

    ImmutableSet<String> tmsSet =
        getPathParameters(extensionRegistry, api.getData(), "/tileMatrixSets/{tileMatrixSetId}")
            .stream()
            .filter(param -> param.getName().equalsIgnoreCase("tileMatrixSetId"))
            .findFirst()
            .map(
                param ->
                    param.getValues(api.getData()).stream().collect(ImmutableSet.toImmutableSet()))
            .orElse(ImmutableSet.of());

    TileMatrixSetsQueriesHandler.QueryInputTileMatrixSets queryInput =
        new ImmutableQueryInputTileMatrixSets.Builder()
            .from(getGenericQueryInput(api.getData()))
            .tileMatrixSetIds(tmsSet)
            .build();

    return queryHandler.handle(
        TileMatrixSetsQueriesHandler.Query.TILE_MATRIX_SETS, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler);
  }
}
