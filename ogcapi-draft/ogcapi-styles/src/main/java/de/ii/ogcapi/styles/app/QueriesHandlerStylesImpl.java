/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.DefaultLinksGenerator;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.HeaderCaching;
import de.ii.ogcapi.foundation.domain.HeaderContentDisposition;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.styles.domain.QueriesHandlerStyles;
import de.ii.ogcapi.styles.domain.StyleFormatExtension;
import de.ii.ogcapi.styles.domain.StyleLegendFormatExtension;
import de.ii.ogcapi.styles.domain.StyleMetadata;
import de.ii.ogcapi.styles.domain.StyleMetadataFormatExtension;
import de.ii.ogcapi.styles.domain.StyleRepository;
import de.ii.ogcapi.styles.domain.Styles;
import de.ii.ogcapi.styles.domain.StylesFormatExtension;
import de.ii.ogcapi.styles.domain.StylesheetContent;
import de.ii.xtraplatform.base.domain.ETag;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.blobs.domain.Blob;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import de.ii.xtraplatform.tiles.domain.TileMatrixSet;
import de.ii.xtraplatform.web.domain.LastModified;
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
public class QueriesHandlerStylesImpl extends AbstractVolatileComposed
    implements QueriesHandlerStyles {

  private final I18n i18n;
  private final ExtensionRegistry extensionRegistry;
  private final StyleRepository styleRepository;
  private final ResourceStore legendsStore;
  private final Map<Query, QueryHandler<? extends QueryInput>> queryHandlers;

  @Inject
  public QueriesHandlerStylesImpl(
      I18n i18n,
      StyleRepository styleRepository,
      VolatileRegistry volatileRegistry,
      ExtensionRegistry extensionRegistry,
      ResourceStore blobStore) {
    super(QueriesHandlerStyles.class.getSimpleName(), volatileRegistry, true);
    this.i18n = i18n;
    this.styleRepository = styleRepository;
    this.extensionRegistry = extensionRegistry;
    this.legendsStore = blobStore.with(StylesBuildingBlock.STORE_RESOURCE_TYPE_LEGENDS);
    ;
    this.queryHandlers =
        ImmutableMap.of(
            Query.STYLES, QueryHandler.with(QueryInputStyles.class, this::getStylesResponse),
            Query.STYLE, QueryHandler.with(QueryInputStyle.class, this::getStyleResponse),
            Query.STYLE_METADATA,
                QueryHandler.with(QueryInputStyle.class, this::getStyleMetadataResponse),
            Query.STYLE_LEGEND,
                QueryHandler.with(QueryInputStyle.class, this::getStyleLegendResponse));

    onVolatileStart();

    addSubcomponent(styleRepository);

    onVolatileStarted();
  }

  @Override
  public Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers() {
    return queryHandlers;
  }

  private Response getStylesResponse(
      QueryInputStyles queryInput, ApiRequestContext requestContext) {
    OgcApi api = requestContext.getApi();
    OgcApiDataV2 apiData = api.getData();
    Optional<String> collectionId = queryInput.getCollectionId();

    Styles styles = styleRepository.getStyles(apiData, collectionId, requestContext, true);

    StylesFormatExtension format =
        styleRepository
            .getStylesFormatStream(apiData, collectionId)
            .filter(f -> requestContext.getMediaType().matches(f.getMediaType().type()))
            .findAny()
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported, the following media types are available: {1}",
                            requestContext.getMediaType(),
                            String.join(
                                ", ",
                                styleRepository
                                    .getStylesFormatStream(apiData, collectionId)
                                    .map(f -> f.getMediaType().type().toString())
                                    .collect(Collectors.toUnmodifiableList())))));

    Date lastModified = styles.getLastModified().orElse(null);
    EntityTag etag =
        !format.getMediaType().type().equals(MediaType.TEXT_HTML_TYPE)
                || (collectionId.isEmpty()
                        ? apiData.getExtension(HtmlConfiguration.class)
                        : apiData.getExtension(HtmlConfiguration.class, collectionId.get()))
                    .map(HtmlConfiguration::getSendEtags)
                    .orElse(false)
            ? ETag.from(styles, Styles.FUNNEL, format.getMediaType().label())
            : null;
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) return response.build();

    return prepareSuccessResponse(
            requestContext,
            queryInput.getIncludeLinkHeader() ? styles.getLinks() : null,
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format("styles.%s", format.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(format.getStylesEntity(styles, apiData, collectionId, requestContext))
        .build();
  }

  private Response getStyleResponse(QueryInputStyle queryInput, ApiRequestContext requestContext) {
    OgcApi api = requestContext.getApi();
    OgcApiDataV2 apiData = api.getData();
    Optional<String> collectionId = queryInput.getCollectionId();
    String styleId = queryInput.getStyleId();
    Optional<TileMatrixSet> tileMatrixSet = queryInput.getTileMatrixSet();

    StyleFormatExtension format =
        styleRepository
            .getStyleFormatStream(apiData, collectionId)
            .filter(f -> requestContext.getMediaType().matches(f.getMediaType().type()))
            .findAny()
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported, the following media types are available: {1}",
                            requestContext.getMediaType(),
                            String.join(
                                ", ",
                                styleRepository
                                    .getStyleFormatStream(apiData, collectionId)
                                    .map(f -> f.getMediaType().type().toString())
                                    .collect(Collectors.toUnmodifiableList())))));

    StylesheetContent stylesheetContent =
        styleRepository.getStylesheet(apiData, collectionId, styleId, format, requestContext, true);

    // collect self/alternate links, but only, if we need to return them in the headers
    List<Link> links = null;
    if (queryInput.getIncludeLinkHeader()) {
      final DefaultLinksGenerator defaultLinkGenerator = new DefaultLinksGenerator();
      List<ApiMediaType> alternateMediaTypes =
          styleRepository
              .getStylesheetMediaTypes(apiData, collectionId, styleId, true, true)
              .stream()
              .filter(apiMediaType -> !apiMediaType.type().equals(format.getMediaType().type()))
              .collect(Collectors.toUnmodifiableList());
      links =
          defaultLinkGenerator.generateLinks(
              requestContext.getUriCustomizer(),
              format.getMediaType(),
              alternateMediaTypes,
              i18n,
              requestContext.getLanguage());
    }

    Date lastModified =
        styleRepository.getStylesheetLastModified(apiData, collectionId, styleId, format, true);
    EntityTag etag =
        !format.getMediaType().type().equals(MediaType.TEXT_HTML_TYPE)
                || (collectionId.isEmpty()
                        ? apiData.getExtension(HtmlConfiguration.class)
                        : apiData.getExtension(HtmlConfiguration.class, collectionId.get()))
                    .map(HtmlConfiguration::getSendEtags)
                    .orElse(false)
            ? ETag.from(stylesheetContent.getContent())
            : null;
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) return response.build();

    return prepareSuccessResponse(
            requestContext,
            links,
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(String.format("%s.%s", styleId, format.getFileExtension())),
            i18n.getLanguages())
        .entity(
            format.getStyleEntity(
                stylesheetContent, api, collectionId, styleId, tileMatrixSet, requestContext))
        .build();
  }

  private Response getStyleMetadataResponse(
      QueryInputStyle queryInput, ApiRequestContext requestContext) {
    OgcApi api = requestContext.getApi();
    OgcApiDataV2 apiData = api.getData();
    Optional<String> collectionId = queryInput.getCollectionId();
    StyleMetadata metadata =
        styleRepository.getStyleMetadata(
            apiData, collectionId, queryInput.getStyleId(), requestContext);

    StyleMetadataFormatExtension format =
        styleRepository
            .getStyleMetadataFormatStream(apiData, collectionId)
            .filter(f -> requestContext.getMediaType().matches(f.getMediaType().type()))
            .findAny()
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported, the following media types are available: {1}",
                            requestContext.getMediaType(),
                            String.join(
                                ", ",
                                styleRepository
                                    .getStyleMetadataFormatStream(apiData, collectionId)
                                    .map(f -> f.getMediaType().type().toString())
                                    .collect(Collectors.toUnmodifiableList())))));

    Date lastModified =
        styleRepository.getStyleLastModified(apiData, collectionId, queryInput.getStyleId());
    EntityTag etag =
        !format.getMediaType().type().equals(MediaType.TEXT_HTML_TYPE)
                || (collectionId.isEmpty()
                        ? apiData.getExtension(HtmlConfiguration.class)
                        : apiData.getExtension(HtmlConfiguration.class, collectionId.get()))
                    .map(HtmlConfiguration::getSendEtags)
                    .orElse(false)
            ? ETag.from(metadata, StyleMetadata.FUNNEL, format.getMediaType().label())
            : null;
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) return response.build();

    return prepareSuccessResponse(
            requestContext,
            queryInput.getIncludeLinkHeader() ? metadata.getLinks() : null,
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format(
                    "%s.metadata.%s",
                    queryInput.getStyleId(), format.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(format.getStyleMetadataEntity(metadata, apiData, collectionId, requestContext))
        .build();
  }

  private Response getStyleLegendResponse(
      QueryInputStyle queryInput, ApiRequestContext requestContext) {
    OgcApi api = requestContext.getApi();
    OgcApiDataV2 apiData = api.getData();
    Optional<String> collectionId = queryInput.getCollectionId();
    String styleId = queryInput.getStyleId();

    final StyleLegendFormatExtension format =
        extensionRegistry.getExtensionsForType(StyleLegendFormatExtension.class).stream()
            .filter(f -> requestContext.getMediaType().matches(f.getMediaType().type()))
            .findAny()
            .map(StyleLegendFormatExtension.class::cast)
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type {0} cannot be generated.",
                            requestContext.getMediaType().type())));

    Blob blob =
        styleRepository
            .getStyleLegend(apiData, collectionId, styleId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        MessageFormat.format("Style ''{0}'' has no legend.", styleId)));

    Date lastModified = LastModified.from(blob.lastModified());
    EntityTag eTag = blob.eTag();
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, eTag);
    if (Objects.nonNull(response)) return response.build();

    return prepareSuccessResponse(
            requestContext,
            null,
            HeaderCaching.of(lastModified, eTag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format(
                    "%s.legend.%s",
                    queryInput.getStyleId(), format.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(format.getStyleLegendImage(blob, apiData, collectionId, requestContext))
        .type(blob.contentType())
        .build();
  }
}
