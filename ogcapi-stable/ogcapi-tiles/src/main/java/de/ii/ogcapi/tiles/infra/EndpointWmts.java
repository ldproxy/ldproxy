/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.infra;

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
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiResourceSet;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.tiles.app.TilesBuildingBlock;
import de.ii.ogcapi.tiles.domain.ImmutableQueryInputWmts;
import de.ii.ogcapi.tiles.domain.TilesConfiguration;
import de.ii.ogcapi.tiles.domain.TilesConfiguration.WmtsScope;
import de.ii.ogcapi.tiles.domain.TilesProviders;
import de.ii.ogcapi.tiles.domain.TilesQueriesHandler;
import de.ii.ogcapi.tiles.domain.WmtsCapabilitiesFormatExtension;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

/**
 * @title WMTS Capabilities
 * @path wmts/1.0.0/WMTSCapabilities.xml
 * @langEn Access tilesets as OGC WMTS 1.0.0
 * @langDe Zugriff auf die Kachelsätze als OGC WMTS 1.0.0
 * @ref:formats {@link de.ii.ogcapi.tiles.domain.WmtsCapabilitiesFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointWmts extends Endpoint implements ApiExtensionHealth {

  private static final List<String> TAGS = ImmutableList.of("OGC WMTS 1.0.0");

  private final TilesQueriesHandler queryHandler;
  private final TilesProviders tilesProviders;

  @Inject
  EndpointWmts(
      ExtensionRegistry extensionRegistry,
      TilesQueriesHandler queryHandler,
      TilesProviders tilesProviders) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
    this.tilesProviders = tilesProviders;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(TilesConfiguration.class)
        .filter(TilesConfiguration::isEnabled)
        .filter(cfg -> cfg.getWmts() != WmtsScope.NONE)
        .isPresent();
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return TilesConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null) {
      formats = extensionRegistry.getExtensionsForType(WmtsCapabilitiesFormatExtension.class);
    }
    return formats;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("wmts")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_WMTS);
    HttpMethods method = HttpMethods.GET;
    String path = "/wmts/1.0.0/WMTSCapabilities.xml";
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, path);
    String operationSummary = "retrieve an OGC WMTS Capabilities document";
    Optional<String> operationDescription =
        Optional.of(
            "This operation fetches an OGC WMTS 1.0.0 Capabilities document (RESTful binding) for tilesets of this API.");
    ImmutableOgcApiResourceSet.Builder resourceBuilderSet =
        new ImmutableOgcApiResourceSet.Builder()
            .path(path)
            .subResourceType("OGC WMTS Service Metadata");
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
            "dataset.wmts.getCapabilities",
            GROUP_TILES_READ,
            TAGS,
            TilesBuildingBlock.MATURITY,
            Optional.of(
                ExternalDocumentation.of(
                    "https://portal.ogc.org/files/?artifact_id=35326",
                    "OGC Web Map Tile Service Implementation Standard 1.0.0")))
        .ifPresent(operation -> resourceBuilderSet.putOperations(method.name(), operation));
    definitionBuilder.putResources(path, resourceBuilderSet.build());

    return definitionBuilder.build();
  }

  @GET
  @Path("/1.0.0/WMTSCapabilities.xml")
  public Response getCapabilities(@Context OgcApi api, @Context ApiRequestContext requestContext) {
    OgcApiDataV2 apiData = api.getData();

    WmtsScope scope =
        apiData
            .getExtension(TilesConfiguration.class)
            .map(TilesConfiguration::getWmts)
            .orElse(WmtsScope.NONE);

    if (!isEnabledForApi(apiData) || scope == WmtsScope.NONE) {
      throw new NotFoundException("OGC WMTS is not supported by this API.");
    }

    TilesQueriesHandler.QueryInputWmts queryInput =
        new ImmutableQueryInputWmts.Builder()
            .from(getGenericQueryInput(apiData))
            .scope(scope)
            .build();

    return queryHandler.handle(TilesQueriesHandler.Query.WMTS, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler, tilesProviders.getTileProviderOrThrow(apiData));
  }
}
