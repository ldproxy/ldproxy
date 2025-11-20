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
import de.ii.ogcapi.collections.domain.EndpointSubCollection;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiResourceData;
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
import de.ii.ogcapi.tiles3d.app.Format3dTilesContentBinary;
import de.ii.ogcapi.tiles3d.app.Tiles3dBuildingBlock;
import de.ii.ogcapi.tiles3d.domain.Format3dTilesContent;
import de.ii.ogcapi.tiles3d.domain.ImmutableQueryInputContentExplicit;
import de.ii.ogcapi.tiles3d.domain.QueriesHandler3dTiles;
import de.ii.ogcapi.tiles3d.domain.QueriesHandler3dTiles.Query;
import de.ii.ogcapi.tiles3d.domain.QueriesHandler3dTiles.QueryInputContentExplicit;
import de.ii.ogcapi.tiles3d.domain.Tile3dProviders;
import de.ii.ogcapi.tiles3d.domain.Tiles3dConfiguration;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import io.dropwizard.auth.Auth;
import java.net.URISyntaxException;
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
 * @title 3D Tiles Content
 * @path collections/{collectionId}/3dtiles/{content}
 * @langEn Access a 3D Tiles 1.1 Content file.
 * @langDe Zugriff auf eine 3D-Tiles 1.1 Kachel-Datei.
 * @ref:formats {@link de.ii.ogcapi.tiles3d.domain.Format3dTilesContent}
 */
@Singleton
@AutoBind
public class Endpoint3dTilesContentExplicit extends EndpointSubCollection
    implements ApiExtensionHealth {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(Endpoint3dTilesContentExplicit.class);

  private static final List<String> TAGS = ImmutableList.of("Access data as 3D Tiles");

  private final Tile3dProviders tile3dProviders;
  private final QueriesHandler3dTiles queryHandler;

  @Inject
  public Endpoint3dTilesContentExplicit(
      ExtensionRegistry extensionRegistry,
      Tile3dProviders tile3dProviders,
      QueriesHandler3dTiles queryHandler) {
    super(extensionRegistry);
    this.tile3dProviders = tile3dProviders;
    this.queryHandler = queryHandler;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData, collectionId)
        && tile3dProviders
            .getTileset3dMetadata(apiData, apiData.getCollectionData(collectionId).orElse(null))
            .map(tileset3d1 -> tileset3d1.getRoot().getImplicitTiling().isEmpty())
            .orElse(false);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return Tiles3dConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null) {
      formats =
          extensionRegistry.getExtensionsForType(Format3dTilesContent.class).stream()
              .filter(format -> format instanceof Format3dTilesContentBinary)
              .toList();
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
    String subSubPath = "/3dtiles/{content}";
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
  @Path("/{collectionId}/3dtiles/{content}")
  @SuppressWarnings("PMD.UseObjectForClearerAPI")
  public Response getContent(
      @Auth Optional<User> optionalUser,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @Context UriInfo uriInfo,
      @PathParam("collectionId") String collectionId,
      @PathParam("content") String content)
      throws URISyntaxException {

    QueryInputContentExplicit queryInput =
        ImmutableQueryInputContentExplicit.builder()
            .from(getGenericQueryInput(api.getData()))
            .collectionId(collectionId)
            .content(content)
            .build();

    return queryHandler.handle(Query.CONTENT, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler, tile3dProviders.getTile3dProviderOrThrow(apiData));
  }
}
