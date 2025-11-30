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
import de.ii.ogcapi.tiles3d.domain.Format3dTilesTileset;
import de.ii.ogcapi.tiles3d.domain.QueriesHandler3dTiles;
import de.ii.ogcapi.tiles3d.domain.Tile3dProviders;
import de.ii.ogcapi.tiles3d.domain.TileResourceCache;
import de.ii.ogcapi.tiles3d.domain.TileResourceDescriptor;
import de.ii.xtraplatform.base.domain.ETag;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.blobs.domain.Blob;
import de.ii.xtraplatform.services.domain.ServicesContext;
import de.ii.xtraplatform.tiles3d.domain.ImmutableTile3dQuery;
import de.ii.xtraplatform.tiles3d.domain.Tile3dAccess;
import de.ii.xtraplatform.tiles3d.domain.Tile3dProvider;
import de.ii.xtraplatform.tiles3d.domain.Tile3dQuery;
import de.ii.xtraplatform.tiles3d.domain.spec.Tileset3d;
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
import javax.ws.rs.InternalServerErrorException;
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
            Query.FILE,
            QueryHandler.with(QueryInputContent.class, this::getContentResponse));

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

    Tileset3d tileset3d =
        tile3dProviders
            .getTileset3dMetadataOrThrow(apiData, collectionData)
            .withUris(
                requestContext
                    .getUriCustomizer()
                    .copy()
                    .clearParameters()
                    .ensureTrailingSlash()
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
    final OgcApiDataV2 apiData = requestContext.getApi().getData();
    final String collectionId = queryInput.getCollectionId();
    final FeatureTypeConfigurationOgcApi collectionData =
        apiData.getCollectionData(collectionId).orElseThrow();

    if (!apiData.isCollectionEnabled(collectionId)) {
      throw new NotFoundException(
          MessageFormat.format("The collection ''{0}'' does not exist in this API.", collectionId));
    }

    checkCollectionId(apiData, collectionId);

    Tile3dAccess tile3dAccess =
        tile3dProviders.getTile3dProviderOrThrow(apiData, collectionData, Tile3dProvider::access);

    String tileset3dId = tile3dProviders.getTileset3dId(collectionData).orElseThrow();

    try {
      Optional<Blob> tileResult = tile3dAccess.getFile(getContentQuery(tileset3dId, queryInput));

      if (tileResult.isEmpty()) {
        throw new NotFoundException();
      }

      Date lastModified = new Date(tileResult.get().lastModified());
      EntityTag eTag = tileResult.get().eTag();
      Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, eTag);
      if (Objects.nonNull(response)) {
        return response.build();
      }

      byte[] result = tileResult.get().content();

      List<Link> links = getLinks(requestContext, i18n);

      return prepareSuccessResponse(
              requestContext,
              queryInput.getIncludeLinkHeader() ? links : ImmutableList.of(),
              HeaderCaching.of(lastModified, eTag, queryInput),
              null,
              null,
              i18n.getLanguages(),
              MediaType.valueOf(tileResult.get().contentType()))
          .entity(result)
          .build();
    } catch (IOException e) {
      LogContext.errorAsDebug(
          LOGGER, e, "Could not retrieve 3D tile content for collection ''{0}''.", collectionId);
      throw new InternalServerErrorException("Could not retrieve 3D tiles file");
    }
  }

  private Tile3dQuery getContentQuery(String tileset3dId, QueryInputContent queryInput) {
    if (queryInput instanceof QueryInputFile) {
      String filePath = ((QueryInputFile) queryInput).getPath();

      return ImmutableTile3dQuery.builder()
          .tileset(tileset3dId)
          .fileName(filePath)
          .level(-1)
          .col(-1)
          .row(-1)
          .build();
    }

    QueryInputContentImplicit queryInputImplicit = (QueryInputContentImplicit) queryInput;

    return ImmutableTile3dQuery.builder()
        .tileset(tileset3dId)
        .level(queryInputImplicit.getLevel())
        .col(queryInputImplicit.getX())
        .row(queryInputImplicit.getY())
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

  private static double degToRad(double degree) {
    return degree / 180.0 * Math.PI;
  }
}
