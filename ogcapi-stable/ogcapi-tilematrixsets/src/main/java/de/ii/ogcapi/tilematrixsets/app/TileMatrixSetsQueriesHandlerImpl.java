/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tilematrixsets.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.HeaderCaching;
import de.ii.ogcapi.foundation.domain.HeaderContentDisposition;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.tilematrixsets.domain.ImmutableTileMatrixSetLinks;
import de.ii.ogcapi.tilematrixsets.domain.ImmutableTileMatrixSetOgcApi;
import de.ii.ogcapi.tilematrixsets.domain.ImmutableTileMatrixSets;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetFormatExtension;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetOgcApi;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSets;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetsFormatExtension;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetsLinksGenerator;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetsQueriesHandler;
import de.ii.xtraplatform.base.domain.ETag;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.tiles.domain.TileMatrixSet;
import de.ii.xtraplatform.tiles.domain.TileMatrixSetRepository;
import java.net.URI;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Singleton
@AutoBind
public class TileMatrixSetsQueriesHandlerImpl extends AbstractVolatileComposed
    implements TileMatrixSetsQueriesHandler {

  private final I18n i18n;
  private final Map<Query, QueryHandler<? extends QueryInput>> queryHandlers;
  private final ExtensionRegistry extensionRegistry;
  private final TileMatrixSetRepository tileMatrixSetRepository;

  @Inject
  public TileMatrixSetsQueriesHandlerImpl(
      I18n i18n,
      ExtensionRegistry extensionRegistry,
      TileMatrixSetRepository tileMatrixSetRepository,
      VolatileRegistry volatileRegistry) {
    super(TileMatrixSetsQueriesHandler.class.getSimpleName(), volatileRegistry, true);
    this.i18n = i18n;
    this.extensionRegistry = extensionRegistry;
    this.tileMatrixSetRepository = tileMatrixSetRepository;

    this.queryHandlers =
        ImmutableMap.of(
            Query.TILE_MATRIX_SETS,
            QueryHandler.with(QueryInputTileMatrixSets.class, this::getTileMatrixSetsResponse),
            Query.TILE_MATRIX_SET,
            QueryHandler.with(QueryInputTileMatrixSet.class, this::getTileMatrixSetResponse));

    onVolatileStart();

    addSubcomponent(tileMatrixSetRepository);

    onVolatileStarted();
  }

  @Override
  public Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers() {
    return queryHandlers;
  }

  private Response getTileMatrixSetsResponse(
      QueryInputTileMatrixSets queryInput, ApiRequestContext requestContext) {
    OgcApi api = requestContext.getApi();
    String path = "/tileMatrixSets";

    TileMatrixSetsFormatExtension outputFormat =
        api.getOutputFormat(
                TileMatrixSetsFormatExtension.class,
                requestContext.getMediaType(),
                Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    final TileMatrixSetsLinkGenerator linkGenerator = new TileMatrixSetsLinkGenerator();

    List<Link> links =
        new TileMatrixSetsLinksGenerator()
            .generateLinks(
                requestContext.getUriCustomizer(),
                requestContext.getMediaType(),
                requestContext.getAlternateMediaTypes(),
                true,
                i18n,
                requestContext.getLanguage());

    TileMatrixSets tileMatrixSets =
        ImmutableTileMatrixSets.builder()
            .tileMatrixSets(
                queryInput.getTileMatrixSetIds().stream()
                    .map(tileMatrixSetRepository::get)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(
                        tileMatrixSet ->
                            ImmutableTileMatrixSetLinks.builder()
                                .id(tileMatrixSet.getId())
                                .title(tileMatrixSet.getTileMatrixSetData().getTitle())
                                .uri(tileMatrixSet.getURI().map(URI::toString))
                                .links(
                                    linkGenerator.generateTileMatrixSetsLinks(
                                        requestContext.getUriCustomizer(),
                                        tileMatrixSet.getId(),
                                        i18n,
                                        requestContext.getLanguage()))
                                .build())
                    .collect(Collectors.toList()))
            .links(links)
            .build();

    Date lastModified = getLastModified(queryInput);
    EntityTag etag =
        !outputFormat.getMediaType().type().equals(MediaType.TEXT_HTML_TYPE)
                || api.getData()
                    .getExtension(HtmlConfiguration.class)
                    .map(HtmlConfiguration::getSendEtags)
                    .orElse(false)
            ? ETag.from(tileMatrixSets, TileMatrixSets.FUNNEL, outputFormat.getMediaType().label())
            : null;
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) return response.build();

    return prepareSuccessResponse(
            requestContext,
            queryInput.getIncludeLinkHeader() ? links : null,
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format("tileMatrixSets.%s", outputFormat.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(outputFormat.getEntity(tileMatrixSets, api, requestContext))
        .build();
  }

  private Response getTileMatrixSetResponse(
      QueryInputTileMatrixSet queryInput, ApiRequestContext requestContext) {
    OgcApi api = requestContext.getApi();
    String tileMatrixSetId = queryInput.getTileMatrixSetId();
    String path = "/tileMatrixSets/" + tileMatrixSetId;

    TileMatrixSetFormatExtension outputFormat =
        api.getOutputFormat(
                TileMatrixSetFormatExtension.class, requestContext.getMediaType(), Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    List<Link> links =
        new TileMatrixSetsLinksGenerator()
            .generateLinks(
                requestContext.getUriCustomizer(),
                requestContext.getMediaType(),
                requestContext.getAlternateMediaTypes(),
                false,
                i18n,
                requestContext.getLanguage());

    TileMatrixSet tileMatrixSet =
        tileMatrixSetRepository
            .get(tileMatrixSetId)
            .orElseThrow(
                () -> new NotFoundException("Unknown tile matrix set: " + tileMatrixSetId));

    TileMatrixSetOgcApi tileMatrixSetData =
        new ImmutableTileMatrixSetOgcApi.Builder()
            .from(tileMatrixSet.getTileMatrixSetData())
            .links(links)
            .build();

    Date lastModified = getLastModified(queryInput);
    EntityTag etag =
        !outputFormat.getMediaType().type().equals(MediaType.TEXT_HTML_TYPE)
                || api.getData()
                    .getExtension(HtmlConfiguration.class)
                    .map(HtmlConfiguration::getSendEtags)
                    .orElse(false)
            ? ETag.from(
                tileMatrixSetData, TileMatrixSetOgcApi.FUNNEL, outputFormat.getMediaType().label())
            : null;
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) return response.build();

    return prepareSuccessResponse(
            requestContext,
            queryInput.getIncludeLinkHeader() ? links : null,
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format(
                    "%s.%s", tileMatrixSetId, outputFormat.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(outputFormat.getEntity(tileMatrixSetData, api, requestContext))
        .build();
  }
}
