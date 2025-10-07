/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app.manager;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.styles.domain.StyleFormatExtension;
import de.ii.ogcapi.styles.domain.StyleRepository;
import de.ii.ogcapi.styles.domain.StylesConfiguration;
import de.ii.ogcapi.styles.domain.StylesheetContent;
import de.ii.ogcapi.styles.domain.manager.QueriesHandlerStylesManager;
import de.ii.xtraplatform.base.domain.ETag;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Singleton
@AutoBind
public class QueriesHandlerStylesManagerImpl extends AbstractVolatileComposed
    implements QueriesHandlerStylesManager {

  private final StyleRepository styleRepository;
  private final Map<Query, QueryHandler<? extends QueryInput>> queryHandlers;

  @Inject
  public QueriesHandlerStylesManagerImpl(
      StyleRepository styleRepository, VolatileRegistry volatileRegistry) {
    super(QueriesHandlerStylesManager.class.getSimpleName(), volatileRegistry, true);
    this.styleRepository = styleRepository;
    this.queryHandlers =
        ImmutableMap.of(
            Query.CREATE_STYLE,
                QueryHandler.with(QueryInputStyleCreateReplace.class, this::createOrReplaceStyle),
            Query.REPLACE_STYLE,
                QueryHandler.with(QueryInputStyleCreateReplace.class, this::createOrReplaceStyle),
            Query.DELETE_STYLE, QueryHandler.with(QueryInputStyleDelete.class, this::deleteStyle));

    onVolatileStart();

    addSubcomponent(styleRepository);

    onVolatileStarted();
  }

  @Override
  public Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers() {
    return queryHandlers;
  }

  private Response createOrReplaceStyle(
      QueryInputStyleCreateReplace queryInput, ApiRequestContext requestContext) {

    OgcApi api = requestContext.getApi();
    OgcApiDataV2 apiData = api.getData();
    Optional<String> collectionId = queryInput.getCollectionId();
    Optional<String> optionalStyleId = queryInput.getStyleId();
    boolean strict = queryInput.getStrict();
    boolean dryRun = queryInput.getDryRun();
    MediaType contentType = queryInput.getContentType();
    byte[] requestBody = queryInput.getRequestBody();

    StyleFormatExtension format =
        styleRepository
            .getStyleFormatStream(apiData, collectionId)
            .filter(f -> contentType.isCompatible(f.getMediaType().type()))
            .filter(f -> f.canSupportTransactions())
            .findAny()
            .orElseThrow(
                () ->
                    new WebApplicationException(
                        String.format(
                            "The content type '%s' is not supported for styles.",
                            contentType.getType()),
                        Response.Status.UNSUPPORTED_MEDIA_TYPE));

    // PUT: check that the style does exist (including as a derived style)
    if (optionalStyleId.isPresent()
        && !styleRepository
            .getStyleIds(apiData, collectionId, true)
            .contains(optionalStyleId.get()))
      throw new NotFoundException(
          String.format("A style with the identifier '%s' does not exist.", optionalStyleId.get()));

    // Validate stylesheet and, if supported, derive the id of the style
    Optional<String> optionalId =
        format.analyze(new StylesheetContent(requestBody, "[request body]", false), strict);

    boolean useIdFromStylesheet =
        optionalId.isPresent()
            && apiData
                .getExtension(StylesConfiguration.class)
                .map(StylesConfiguration::shouldUseIdFromStylesheet)
                .orElse(false);

    String styleId;
    if (useIdFromStylesheet) {
      if (optionalStyleId.isPresent()) {
        // PUT: the id is always taken from the path, ignore the id derived from the stylesheet
        styleId = optionalStyleId.get();
      } else {
        // POST: throw an exception, if the style id already exists as a style - excluding derived
        // styles
        if (styleRepository.getStyleIds(apiData, collectionId, false).contains(optionalId.get()))
          throw new WebApplicationException(
              String.format(
                  "A style with the identifier '%s' already exists. Please use another identifier in the stylesheet."),
              Response.Status.CONFLICT);
        styleId = optionalId.get();
      }
    } else {
      if (optionalStyleId.isEmpty()) {
        // POST: use the next available id
        styleId = styleRepository.getNewStyleId(apiData, collectionId);
      } else {
        // PUT: the id is always taken from the path
        styleId = optionalStyleId.get();
      }
    }

    if (optionalStyleId.isPresent()) {
      StylesheetContent stylesheetContent =
          styleRepository.getStylesheet(
              apiData, collectionId, styleId, format, requestContext, true);

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

      if (Objects.nonNull(response)) return response.build(); // Preconditions failed
    }

    if (dryRun) return Response.noContent().build();

    try {
      styleRepository.writeStyleDocument(apiData, collectionId, styleId, format, requestBody);
    } catch (Exception e) {
      // something went wrong, try to clean up
      try {
        styleRepository.deleteStyle(apiData, collectionId, styleId);
      } catch (IOException ioException) {
        // nothing to do
      }
      throw new WebApplicationException(
          "Could not write the style to the store.", e, Response.Status.INTERNAL_SERVER_ERROR);
    }

    if (optionalStyleId.isEmpty()) {
      // POST
      // Return 201 with Location header
      URI newURI;
      try {
        newURI =
            requestContext
                .getUriCustomizer()
                .copy()
                .clearParameters()
                .ensureLastPathSegment(styleId)
                .build();
      } catch (URISyntaxException e) {
        throw new WebApplicationException(
            "Could not determine URI for the new style.", e, Response.Status.INTERNAL_SERVER_ERROR);
      }

      return Response.created(newURI).build();
    }

    // PUT
    return Response.noContent().build();
  }

  private Response deleteStyle(QueryInputStyleDelete queryInput, ApiRequestContext requestContext) {

    try {
      styleRepository.deleteStyle(
          requestContext.getApi().getData(), queryInput.getCollectionId(), queryInput.getStyleId());
    } catch (IOException e) {
      throw new WebApplicationException(
          "Could not delete the style from the store.", e, Response.Status.INTERNAL_SERVER_ERROR);
    }

    return Response.noContent().build();
  }
}
