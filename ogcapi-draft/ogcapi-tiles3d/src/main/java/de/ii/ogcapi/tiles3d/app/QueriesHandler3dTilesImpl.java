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
import de.ii.ogcapi.tiles3d.domain.Format3dTilesTileset;
import de.ii.ogcapi.tiles3d.domain.QueriesHandler3dTiles;
import de.ii.ogcapi.tiles3d.domain.Tile3dProviders;
import de.ii.xtraplatform.base.domain.ETag;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.blobs.domain.Blob;
import de.ii.xtraplatform.tiles3d.domain.ImmutableTile3dQuery;
import de.ii.xtraplatform.tiles3d.domain.Tile3dAccess;
import de.ii.xtraplatform.tiles3d.domain.Tile3dProvider;
import de.ii.xtraplatform.tiles3d.domain.Tile3dQuery;
import de.ii.xtraplatform.tiles3d.domain.spec.Tileset3d;
import java.io.IOException;
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
public class QueriesHandler3dTilesImpl implements QueriesHandler3dTiles {

  private final I18n i18n;
  private final Tile3dProviders tile3dProviders;
  private final Map<Query, QueryHandler<? extends QueryInput>> queryHandlers;

  @Inject
  public QueriesHandler3dTilesImpl(I18n i18n, Tile3dProviders tile3dProviders) {
    this.i18n = i18n;
    this.tile3dProviders = tile3dProviders;
    this.queryHandlers =
        ImmutableMap.of(
            Query.TILESET,
            QueryHandler.with(QueryInputTileset.class, this::getTilesetResponse),
            Query.FILE,
            QueryHandler.with(QueryInputFile.class, this::getContentResponse));
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
    final Optional<String> collectionId = queryInput.getCollectionId();
    final Optional<FeatureTypeConfigurationOgcApi> collectionData =
        collectionId.flatMap(apiData::getCollectionData);

    if (collectionId.isPresent()) {
      checkCollectionId(api.getData(), collectionId.get());
    }

    Format3dTilesTileset outputFormat = getFormat3dTilesTileset(requestContext, collectionId);

    Tileset3d tileset3d =
        tile3dProviders
            .getTileset3dMetadataOrThrow(apiData, collectionData)
            .withCopyright(api.getData().getMetadata().flatMap(ApiMetadata::getAttribution))
            .withUris(
                requestContext
                    .getUriCustomizer()
                    .copy()
                    .clearParameters()
                    .ensureTrailingSlash()
                    .toString());

    if (tile3dProviders.getTile3dProvider(apiData, Tile3dProvider::seeding).isPresent()) {
      tileset3d =
          tileset3d.withSchemaUri(
              requestContext
                  .getUriCustomizer()
                  .copy()
                  .clearParameters()
                  .removeLastPathSegments(1)
                  .ensureLastPathSegments("gltf", "schema")
                  .toString());
    }

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
      ApiRequestContext requestContext, Optional<String> collectionId) {
    return requestContext
        .getApi()
        .getOutputFormat(Format3dTilesTileset.class, requestContext.getMediaType(), collectionId)
        .orElseThrow(
            () ->
                new NotAcceptableException(
                    MessageFormat.format(
                        "The requested media type ''{0}'' is not supported for this resource.",
                        requestContext.getMediaType())));
  }

  private Response getContentResponse(QueryInputFile queryInput, ApiRequestContext requestContext) {
    final OgcApiDataV2 apiData = requestContext.getApi().getData();
    final Optional<String> collectionId = queryInput.getCollectionId();
    final Optional<FeatureTypeConfigurationOgcApi> collectionData =
        collectionId.flatMap(apiData::getCollectionData);

    if (collectionId.isPresent()) {
      checkCollectionId(apiData, collectionId.get());
    }

    Tile3dAccess tile3dAccess =
        tile3dProviders.getTile3dProviderOrThrow(apiData, collectionData, Tile3dProvider::access);

    String tileset3dId =
        collectionData.isPresent()
            ? tile3dProviders.getTileset3dId(collectionData.get()).orElseThrow()
            : tile3dProviders.getTileset3dId(apiData).orElseThrow();

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

  private Tile3dQuery getContentQuery(String tileset3dId, QueryInputFile queryInput) {
    String filePath = queryInput.getPath();

    return ImmutableTile3dQuery.builder()
        .tileset(tileset3dId)
        .fileName(filePath)
        .level(-1)
        .col(-1)
        .row(-1)
        .build();
  }

  private boolean shouldProvideEntityTag(
      OgcApiDataV2 apiData, Optional<String> collectionId, FormatExtension outputFormat) {
    return !outputFormat.getMediaType().type().equals(MediaType.TEXT_HTML_TYPE)
        || (collectionId.isPresent()
                ? apiData.getExtension(HtmlConfiguration.class, collectionId.get())
                : apiData.getExtension(HtmlConfiguration.class))
            .map(HtmlConfiguration::getSendEtags)
            .orElse(false);
  }
}
