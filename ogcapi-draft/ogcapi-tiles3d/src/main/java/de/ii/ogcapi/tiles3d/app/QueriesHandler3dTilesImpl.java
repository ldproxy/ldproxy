/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.features.gltf.domain.ImmutableAssetMetadata;
import de.ii.ogcapi.foundation.domain.ApiMetadata;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HeaderCaching;
import de.ii.ogcapi.foundation.domain.HeaderContentDisposition;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.tiles3d.domain.Format3dTilesSubtree;
import de.ii.ogcapi.tiles3d.domain.Format3dTilesTileset;
import de.ii.ogcapi.tiles3d.domain.ImmutableBoundingVolume;
import de.ii.ogcapi.tiles3d.domain.ImmutableContent;
import de.ii.ogcapi.tiles3d.domain.ImmutableImplicitTiling;
import de.ii.ogcapi.tiles3d.domain.ImmutableTile;
import de.ii.ogcapi.tiles3d.domain.ImmutableTileset;
import de.ii.ogcapi.tiles3d.domain.QueriesHandler3dTiles;
import de.ii.ogcapi.tiles3d.domain.Tile3dProviders;
import de.ii.ogcapi.tiles3d.domain.TileResourceCache;
import de.ii.ogcapi.tiles3d.domain.TileResourceDescriptor;
import de.ii.ogcapi.tiles3d.domain.Tiles3dConfiguration;
import de.ii.ogcapi.tiles3d.domain.Tileset;
import de.ii.xtraplatform.base.domain.ETag;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.services.domain.ServicesContext;
import de.ii.xtraplatform.tiles.domain.TileResult;
import de.ii.xtraplatform.tiles3d.domain.ImmutableTile3dQuery;
import de.ii.xtraplatform.tiles3d.domain.Tile3dAccess;
import de.ii.xtraplatform.tiles3d.domain.Tile3dProvider;
import de.ii.xtraplatform.tiles3d.domain.spec.Tileset3d;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Singleton
@AutoBind
public class QueriesHandler3dTilesImpl extends AbstractVolatileComposed
    implements QueriesHandler3dTiles {

  private final ServicesContext servicesContext;
  private final I18n i18n;
  private final Tile3dProviders tile3dProviders;
  private final FeaturesCoreQueriesHandler queriesHandlerFeatures;
  private final Map<Query, QueryHandler<? extends QueryInput>> queryHandlers;
  private final TileResourceCache tileResourceCache;

  @Inject
  public QueriesHandler3dTilesImpl(
      ServicesContext servicesContext,
      I18n i18n,
      Tile3dProviders tile3dProviders,
      FeaturesCoreQueriesHandler queriesHandlerFeatures,
      TileResourceCache tileResourceCache,
      VolatileRegistry volatileRegistry) {
    super(QueriesHandler3dTiles.class.getSimpleName(), volatileRegistry, true);
    this.servicesContext = servicesContext;
    this.i18n = i18n;
    this.tile3dProviders = tile3dProviders;
    this.queriesHandlerFeatures = queriesHandlerFeatures;
    this.tileResourceCache = tileResourceCache;
    this.queryHandlers =
        ImmutableMap.of(
            Query.TILESET,
            QueryHandler.with(QueryInputTileset.class, this::getTilesetResponse),
            Query.CONTENT,
            QueryHandler.with(QueryInputContent.class, this::getContentResponse),
            Query.SUBTREE,
            QueryHandler.with(QueryInputSubtree.class, this::getSubtreeResponse));

    onVolatileStart();

    addSubcomponent(queriesHandlerFeatures);
    addSubcomponent(tileResourceCache);

    onVolatileStarted();
  }

  @Override
  public Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers() {
    return queryHandlers;
  }

  public static void checkCollectionId(OgcApiDataV2 apiData, String collectionId) {
    if (!apiData.isCollectionEnabled(collectionId)) {
      throw new NotFoundException(
          MessageFormat.format("The collection ''{0}'' does not exist in this API.", collectionId));
    }
  }

  private Response getTilesetResponse(
      QueryInputTileset queryInput, ApiRequestContext requestContext) {

    final OgcApi api = requestContext.getApi();
    final OgcApiDataV2 apiData = api.getData();
    final String collectionId = queryInput.getCollectionId();
    final FeatureTypeConfigurationOgcApi collectionData =
        apiData.getCollectionData(collectionId).orElseThrow();

    if (!apiData.isCollectionEnabled(collectionId)) {
      throw new NotFoundException(
          MessageFormat.format("The collection ''{0}'' does not exist in this API.", collectionId));
    }

    Format3dTilesTileset outputFormat = getFormat3dTilesTileset(requestContext, collectionId);

    checkCollectionId(api.getData(), collectionId);

    // Tileset tileset = getTileset(api, collectionId, requestContext.getUriCustomizer());

    Tileset3d tileset3d =
        tile3dProviders
            .getTileset3dMetadataOrThrow(apiData, collectionData)
            .withUris(
                requestContext
                    .getUriCustomizer()
                    .copy()
                    .clearParameters()
                    .ensureLastPathSegment("content_{level}_{x}_{y}")
                    .toString(),
                requestContext
                    .getUriCustomizer()
                    .copy()
                    .clearParameters()
                    .ensureLastPathSegment("subtree_{level}_{x}_{y}")
                    .toString());

    Date lastModified = getLastModified(queryInput);
    @SuppressWarnings("UnstableApiUsage")
    EntityTag etag =
        shouldProvideEntityTag(apiData, collectionId, outputFormat)
            ? ETag.from(tileset3d, Tileset3d.FUNNEL, outputFormat.getMediaType().label())
            : null;
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) {
      return response.build();
    }

    List<Link> links = getLinks(requestContext, i18n);

    return prepareSuccessResponse(
            requestContext,
            queryInput.getIncludeLinkHeader() ? links : ImmutableList.of(),
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format(
                    "%s.tileset.%s", collectionId, outputFormat.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(outputFormat.getEntity(tileset3d, links, collectionId, api, requestContext))
        .build();
  }

  private Tileset getTileset(OgcApi api, String collectionId, URICustomizer uriCustomizer) {
    BoundingBox bbox = api.getSpatialExtent(collectionId).orElseThrow();

    Tiles3dConfiguration cfg =
        api.getData().getExtension(Tiles3dConfiguration.class, collectionId).orElseThrow();

    @SuppressWarnings("ConstantConditions")
    Tileset tileset =
        ImmutableTileset.builder()
            .asset(
                ImmutableAssetMetadata.builder()
                    .version("1.1")
                    .copyright(api.getData().getMetadata().flatMap(ApiMetadata::getAttribution))
                    .build())
            .geometricError(10_000)
            .root(
                ImmutableTile.builder()
                    .boundingVolume(
                        ImmutableBoundingVolume.builder()
                            .region(
                                ImmutableList.of(
                                    degToRad(bbox.getXmin()),
                                    degToRad(bbox.getYmin()),
                                    degToRad(bbox.getXmax()),
                                    degToRad(bbox.getYmax()),
                                    Objects.requireNonNull(bbox.getZmin()),
                                    Objects.requireNonNull(bbox.getZmax())))
                            .build())
                    .geometricError(cfg.getGeometricErrorRoot())
                    .refine("ADD")
                    .content(
                        ImmutableContent.builder()
                            .uri(
                                uriCustomizer
                                    .copy()
                                    .clearParameters()
                                    .ensureLastPathSegment("content_{level}_{x}_{y}")
                                    .toString())
                            .build())
                    .implicitTiling(
                        ImmutableImplicitTiling.builder()
                            .subdivisionScheme("QUADTREE")
                            .availableLevels(cfg.getMaxLevel() + 1)
                            .subtreeLevels(Objects.requireNonNull(cfg.getSubtreeLevels()))
                            .subtrees(
                                ImmutableContent.builder()
                                    .uri(
                                        uriCustomizer
                                            .copy()
                                            .clearParameters()
                                            .ensureLastPathSegment("subtree_{level}_{x}_{y}")
                                            .toString())
                                    .build())
                            .build())
                    .build())
            .extensionsUsed(
                ImmutableList.of(
                    "3DTILES_content_gltf", "3DTILES_implicit_tiling", "3DTILES_metadata"))
            .extensionsRequired(ImmutableList.of("3DTILES_content_gltf", "3DTILES_implicit_tiling"))
            .schemaUri(
                uriCustomizer
                    .copy()
                    .clearParameters()
                    .removeLastPathSegments(1)
                    .ensureLastPathSegments("gltf", "schema")
                    .toString())
            .build();
    return tileset;
  }

  private Format3dTilesTileset getFormat3dTilesTileset(
      ApiRequestContext requestContext, String collectionId) {
    return requestContext
        .getApi()
        .getOutputFormat(
            Format3dTilesTileset.class, requestContext.getMediaType(), Optional.of(collectionId))
        .orElseThrow(
            () ->
                new NotAcceptableException(
                    MessageFormat.format(
                        "The requested media type ''{0}'' is not supported for this resource.",
                        requestContext.getMediaType())));
  }

  private Response getContentResponse(
      QueryInputContent queryInput, ApiRequestContext requestContext) {

    Date lastModified = getLastModified(queryInput);
    EntityTag etag = null; // ETag.from(queryInput.getContent());
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) {
      return response.build();
    }

    List<Link> links = getLinks(requestContext, i18n);

    final OgcApi api = requestContext.getApi();
    final OgcApiDataV2 apiData = api.getData();
    final String collectionId = queryInput.getCollectionId();
    final FeatureTypeConfigurationOgcApi collectionData =
        apiData.getCollectionData(collectionId).orElseThrow();

    if (!apiData.isCollectionEnabled(collectionId)) {
      throw new NotFoundException(
          MessageFormat.format("The collection ''{0}'' does not exist in this API.", collectionId));
    }

    checkCollectionId(api.getData(), collectionId);

    int level = queryInput.getLevel();
    int x = queryInput.getX();
    int y = queryInput.getY();

    Tile3dAccess tile3dAccess =
        tile3dProviders.getTile3dProviderOrThrow(apiData, collectionData, Tile3dProvider::access);

    String tileset3dId = tile3dProviders.getTileset3dId(collectionData).orElseThrow();

    TileResult tileResult =
        tile3dAccess.getTile(
            ImmutableTile3dQuery.builder().tileset(tileset3dId).level(level).col(x).row(y).build());

    if (tileResult.isNotFound()) {
      throw new NotFoundException();
    }
    if (!tileResult.isAvailable()) {
      throw new IllegalStateException(
          "Tile content could not be retrieved: "
              + tileResult.getError().orElse(tileResult.getStatus().toString()));
    }

    byte[] result = tileResult.getContent().get();

    return prepareSuccessResponse(
            requestContext,
            queryInput.getIncludeLinkHeader() ? links : ImmutableList.of(),
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            null,
            /*HeaderContentDisposition.of(
            String.format(
                "%s.content_%d_%d_%d.%s",
                queryInput.getCollectionId(),
                queryInput.getLevel(),
                queryInput.getX(),
                queryInput.getY(),
                requestContext.getMediaType().fileExtension())),*/
            i18n.getLanguages())
        .entity(result)
        .build();
  }

  private Response getSubtreeResponse(
      QueryInputSubtree queryInput, ApiRequestContext requestContext) {

    final OgcApi api = requestContext.getApi();
    final OgcApiDataV2 apiData = api.getData();
    final String collectionId = queryInput.getCollectionId();
    final FeatureTypeConfigurationOgcApi collectionData =
        apiData.getCollectionData(collectionId).orElseThrow();

    if (!apiData.isCollectionEnabled(collectionId)) {
      throw new NotFoundException(
          MessageFormat.format("The collection ''{0}'' does not exist in this API.", collectionId));
    }

    checkCollectionId(api.getData(), collectionId);

    int level = queryInput.getLevel();
    int x = queryInput.getX();
    int y = queryInput.getY();

    // TileResourceDescriptor r = TileResourceDescriptor.subtreeOf(api, collectionId, level, x, y);

    Tile3dAccess tile3dAccess =
        tile3dProviders.getTile3dProviderOrThrow(apiData, collectionData, Tile3dProvider::access);

    String tileset3dId = tile3dProviders.getTileset3dId(collectionData).orElseThrow();

    TileResult tileResult =
        tile3dAccess.getSubtree(
            ImmutableTile3dQuery.builder().tileset(tileset3dId).level(level).col(x).row(y).build());

    if (!tileResult.isAvailable()) {
      throw new IllegalStateException(
          "Tile subtree could not be retrieved: "
              + tileResult.getError().orElse(tileResult.getStatus().toString()));
    }

    byte[] result = tileResult.getContent().get();

    /*byte[] result = getSubtreeContent(r);

    if (Objects.isNull(result)) {
      result =
          Subtree.getBinary(Subtree.of(servicesContext, queriesHandlerFeatures, queryInput, r));

      try {
        tileResourceCache.storeTileResource(r, result);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }*/

    // Format3dTilesSubtree outputFormat = getFormat3dTilesSubtree(queryInput, requestContext);

    Date lastModified = getLastModified(queryInput);
    EntityTag etag = ETag.from(result);
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) {
      return response.build();
    }

    return prepareSuccessResponse(
            requestContext,
            queryInput.getIncludeLinkHeader() ? getLinks(requestContext, i18n) : ImmutableList.of(),
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            null,
            /*HeaderContentDisposition.of(
            String.format(
                "%s.subtree_%d_%d_%d.%s",
                collectionId,
                level,
                x,
                y,
                outputFormat.getMediaType().fileExtension())),*/
            i18n.getLanguages())
        .entity(result)
        .build();
  }

  private byte[] getSubtreeContent(TileResourceDescriptor r) {
    byte[] result = null;

    try {
      if (tileResourceCache.tileResourceExists(r)) {
        Optional<InputStream> subtreeContent = tileResourceCache.getTileResource(r);
        if (subtreeContent.isPresent()) {
          try (InputStream is = subtreeContent.get()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ByteStreams.copy(is, baos);
            result = baos.toByteArray();
          }
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return result;
  }

  private boolean shouldProvideEntityTag(
      OgcApiDataV2 apiData, String collectionId, FormatExtension outputFormat) {
    return !outputFormat.getMediaType().type().equals(MediaType.TEXT_HTML_TYPE)
        || apiData
            .getExtension(HtmlConfiguration.class, collectionId)
            .map(HtmlConfiguration::getSendEtags)
            .orElse(false);
  }

  private Format3dTilesSubtree getFormat3dTilesSubtree(
      QueryInputSubtree queryInput, ApiRequestContext requestContext) {
    final String collectionId = queryInput.getCollectionId();
    return requestContext
        .getApi()
        .getOutputFormat(
            Format3dTilesSubtree.class, requestContext.getMediaType(), Optional.of(collectionId))
        .orElseThrow(
            () ->
                new NotAcceptableException(
                    MessageFormat.format(
                        "The requested media type ''{0}'' is not supported for this resource.",
                        requestContext.getMediaType())));
  }

  private static double degToRad(double degree) {
    return degree / 180.0 * Math.PI;
  }
}
