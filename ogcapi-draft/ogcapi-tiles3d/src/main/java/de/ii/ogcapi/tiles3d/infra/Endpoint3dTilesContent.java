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
import com.google.common.io.ByteStreams;
import de.ii.ogcapi.collections.domain.EndpointSubCollection;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiResourceData;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.tiles3d.app.Tiles3dBuildingBlock;
import de.ii.ogcapi.tiles3d.app.Tiles3dContentUtil;
import de.ii.ogcapi.tiles3d.domain.Format3dTilesContent;
import de.ii.ogcapi.tiles3d.domain.ImmutableQueryInputContentImplicit;
import de.ii.ogcapi.tiles3d.domain.QueriesHandler3dTiles;
import de.ii.ogcapi.tiles3d.domain.QueriesHandler3dTiles.Query;
import de.ii.ogcapi.tiles3d.domain.QueriesHandler3dTiles.QueryInputContentImplicit;
import de.ii.ogcapi.tiles3d.domain.Tile3dProviders;
import de.ii.ogcapi.tiles3d.domain.TileResourceCache;
import de.ii.ogcapi.tiles3d.domain.TileResourceDescriptor;
import de.ii.ogcapi.tiles3d.domain.Tiles3dConfiguration;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.cql.domain.Cql;
import de.ii.xtraplatform.services.domain.ServicesContext;
import io.dropwizard.auth.Auth;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * @title 3D Tiles Content
 * @path collections/{collectionId}/3dtiles/content_{level}_{x}_{y}
 * @langEn Access a 3D Tiles 1.1 Content file.
 * @langDe Zugriff auf eine 3D-Tiles 1.1 Kachel-Datei.
 * @ref:formats {@link de.ii.ogcapi.tiles3d.domain.Format3dTilesContent}
 */
@Singleton
@AutoBind
public class Endpoint3dTilesContent extends EndpointSubCollection implements ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(Endpoint3dTilesContent.class);

  private static final List<String> TAGS = ImmutableList.of("Access data as 3D Tiles");

  private final ServicesContext servicesContext;
  private final FeaturesCoreProviders providers;
  private final FeaturesCoreQueriesHandler queriesHandlerFeatures;
  private final Tile3dProviders tile3dProviders;
  private final QueriesHandler3dTiles queryHandler;
  private final Cql cql;
  private final TileResourceCache tileResourceCache;

  @Inject
  public Endpoint3dTilesContent(
      ServicesContext servicesContext,
      ExtensionRegistry extensionRegistry,
      FeaturesCoreProviders providers,
      FeaturesCoreQueriesHandler queriesHandlerFeatures,
      Tile3dProviders tile3dProviders,
      QueriesHandler3dTiles queryHandler,
      Cql cql,
      TileResourceCache tileResourceCache) {
    super(extensionRegistry);
    this.servicesContext = servicesContext;
    this.providers = providers;
    this.queriesHandlerFeatures = queriesHandlerFeatures;
    this.tile3dProviders = tile3dProviders;
    this.queryHandler = queryHandler;
    this.cql = cql;
    this.tileResourceCache = tileResourceCache;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData, collectionId)
        && tile3dProviders
            .getTileset3dMetadata(apiData, apiData.getCollectionData(collectionId).orElse(null))
            .map(tileset3d1 -> tileset3d1.getRoot().getImplicitTiling().isPresent())
            .orElse(false);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return Tiles3dConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null) {
      formats = extensionRegistry.getExtensionsForType(Format3dTilesContent.class);
    }
    return formats;
  }

  @Override
  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("collections")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_3D_TILES_CONTENT);
    String subSubPath = "/3dtiles/content_{level}_{x}_{y}";
    String path = "/collections/{collectionId}" + subSubPath;
    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);
    Optional<OgcApiPathParameter> optCollectionIdParam =
        pathParameters.stream().filter(param -> "collectionId".equals(param.getName())).findAny();
    if (optCollectionIdParam.isPresent()) {
      final OgcApiPathParameter collectionIdParam = optCollectionIdParam.get();
      final boolean explode = collectionIdParam.isExplodeInOpenApi(apiData);
      final List<String> collectionIds =
          explode ? collectionIdParam.getValues(apiData) : ImmutableList.of("{collectionId}");
      for (String collectionId : collectionIds) {
        if (!isEnabledForApi(apiData, collectionId)) {
          continue;
        }
        List<OgcApiQueryParameter> queryParameters =
            getQueryParameters(extensionRegistry, apiData, path, collectionId);
        String operationSummary =
            "retrieve a tile of the feature collection '" + collectionId + "'";
        Optional<String> operationDescription = Optional.of("Access a 3D Tiles 1.1 Content file.");
        String resourcePath = "/collections/" + collectionId + subSubPath;
        ImmutableOgcApiResourceData.Builder resourceBuilder =
            new ImmutableOgcApiResourceData.Builder()
                .path(resourcePath)
                .pathParameters(pathParameters);
        Map<MediaType, ApiMediaTypeContent> responseContent = getResponseContent(apiData);
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
                getOperationId("get3dTilesContent", collectionId),
                GROUP_TILES_READ,
                TAGS,
                Tiles3dBuildingBlock.MATURITY,
                Tiles3dBuildingBlock.SPEC)
            .ifPresent(
                operation -> resourceBuilder.putOperations(HttpMethods.GET.name(), operation));
        definitionBuilder.putResources(resourcePath, resourceBuilder.build());
      }
    } else {
      LOGGER.error(
          "Path parameter 'collectionId' missing for resource at path '"
              + path
              + "'. The resource will not be available.");
    }

    return definitionBuilder.build();
  }

  @GET
  @Path("/{collectionId}/3dtiles/content_{level}_{x}_{y}")
  @SuppressWarnings("PMD.UseObjectForClearerAPI")
  public Response getContent(
      @Auth Optional<User> optionalUser,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @Context UriInfo uriInfo,
      @PathParam("collectionId") String collectionId,
      @PathParam("level") String level,
      @PathParam("x") String x,
      @PathParam("y") String y)
      throws URISyntaxException {

    /*Tiles3dConfiguration cfg =
        api.getData()
            .getCollectionData(collectionId)
            .flatMap(c -> c.getExtension(Tiles3dConfiguration.class))
            .orElseThrow();

    int maxLevel = Objects.requireNonNull(cfg.getMaxLevel());
    int firstLevelWithContent = Objects.requireNonNull(cfg.getFirstLevelWithContent());*/

    int cl = Integer.parseInt(level);
    int cx = Integer.parseInt(x);
    int cy = Integer.parseInt(y);
    /*if (cl < Math.max(0, firstLevelWithContent)
        || cl > maxLevel
        || cx < 0
        || cx >= Math.pow(2, cl)
        || cy < 0
        || cy >= Math.pow(2, cl)) {
      throw new NotFoundException();
    }

    TileResourceDescriptor r = TileResourceDescriptor.contentOf(api, collectionId, cl, cx, cy);

    byte[] content = fromCache(r);

    if (Objects.isNull(content)) {
      return computeAndCache(requestContext, api, collectionId, cfg, r);
    }*/

    QueryInputContentImplicit queryInput =
        ImmutableQueryInputContentImplicit.builder()
            .from(getGenericQueryInput(api.getData()))
            .collectionId(collectionId)
            .level(cl)
            .x(cx)
            .y(cy)
            // .content(content)
            .build();

    return queryHandler.handle(Query.CONTENT, queryInput, requestContext);
  }

  private byte[] fromCache(TileResourceDescriptor r) {
    byte[] content = null;

    try {
      if (tileResourceCache.tileResourceExists(r)) {
        Optional<InputStream> contentStream = tileResourceCache.getTileResource(r);
        if (contentStream.isPresent()) {
          try (InputStream is = contentStream.get()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ByteStreams.copy(is, baos);
            content = baos.toByteArray();
          }
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return content;
  }

  private Response computeAndCache(
      ApiRequestContext requestContext,
      OgcApi api,
      String collectionId,
      Tiles3dConfiguration cfg,
      TileResourceDescriptor r)
      throws URISyntaxException {
    OgcApiDataV2 apiData = api.getData();
    Response response =
        Tiles3dContentUtil.getContent(
            servicesContext,
            extensionRegistry,
            api,
            collectionId,
            providers.getFeatureProviderOrThrow(
                apiData, apiData.getCollectionData(collectionId).orElseThrow()),
            queriesHandlerFeatures,
            cql,
            cfg,
            r,
            r.getQuery(providers),
            requestContext.getUriCustomizer(),
            Optional.of(getGenericQueryInput(apiData)));

    if (Objects.nonNull(response.getEntity())) {
      try {
        tileResourceCache.storeTileResource(r, (byte[]) response.getEntity());
      } catch (IOException e) {
        LogContext.error(LOGGER, e, "Could not write feature response to resource '{}'", r);
      }
    }

    return response;
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(
        queryHandler,
        queriesHandlerFeatures,
        tileResourceCache,
        tile3dProviders.getTile3dProviderOrThrow(apiData));
  }
}
