/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crud.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler.Query;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableRequestContext.Builder;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.CrsInfo;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.features.domain.FeatureChange;
import de.ii.xtraplatform.features.domain.FeatureChange.Action;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureStream;
import de.ii.xtraplatform.features.domain.FeatureTokenSource;
import de.ii.xtraplatform.features.domain.FeatureTransactions;
import de.ii.xtraplatform.features.domain.ImmutableFeatureChange;
import de.ii.xtraplatform.features.domain.Tuple;
import de.ii.xtraplatform.features.json.domain.FeatureTokenDecoderGeoJson;
import de.ii.xtraplatform.geometries.domain.Axes;
import de.ii.xtraplatform.streams.domain.Reactive.Source;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threeten.extra.Interval;

@Singleton
@AutoBind
public class CommandHandlerCrudImpl extends AbstractVolatileComposed implements CommandHandlerCrud {

  private static final Logger LOGGER = LoggerFactory.getLogger(CommandHandlerCrudImpl.class);

  private final FeaturesCoreQueriesHandler queriesHandler;
  private final ExtensionRegistry extensionRegistry;
  private List<? extends FormatExtension> formats;
  private CrsInfo crsInfo;

  @Inject
  public CommandHandlerCrudImpl(
      FeaturesCoreQueriesHandler queriesHandler,
      CrsInfo crsInfo,
      ExtensionRegistry extensionRegistry,
      VolatileRegistry volatileRegistry) {
    super(CommandHandlerCrud.class.getSimpleName(), volatileRegistry, true);
    this.queriesHandler = queriesHandler;
    this.extensionRegistry = extensionRegistry;
    this.crsInfo = crsInfo;

    onVolatileStart();

    addSubcomponent(queriesHandler);

    onVolatileStarted();
  }

  @Override
  public Response postItemsResponse(
      QueryInputFeatureCreate queryInput, ApiRequestContext requestContext) {

    EpsgCrs crs = queryInput.getCrs().orElseGet(queryInput::getDefaultCrs);
    Axes axes = crsInfo.is3d(crs) ? Axes.XYZ : Axes.XY;

    FeatureTokenSource featureTokenSource =
        getFeatureSource(
            requestContext.getMediaType(),
            queryInput.getRequestBody(),
            Optional.empty(),
            crs,
            axes);

    FeatureTransactions.MutationResult result =
        queryInput
            .getFeatureProvider()
            .mutations()
            .get()
            .createFeatures(queryInput.getFeatureType(), featureTokenSource, crs);

    result.getError().ifPresent(FeatureStream::processStreamError);

    List<String> ids = result.getIds();

    if (ids.isEmpty()) {
      throw new IllegalArgumentException("No features found in input");
    }
    URI firstFeature = null;
    try {
      firstFeature =
          requestContext.getUriCustomizer().copy().ensureLastPathSegment(ids.get(0)).build();
    } catch (URISyntaxException e) {
      // ignore
    }

    handleChange(
        queryInput.getFeatureProvider(),
        queryInput.getCollectionId(),
        ids,
        Optional.empty(),
        result.getSpatialExtent(),
        Optional.empty(),
        convertTemporalExtentMillisecond(result.getTemporalExtent()),
        Action.CREATE);

    return Response.created(firstFeature).build();
  }

  @Override
  public Response putItemResponse(
      QueryInputFeatureReplace queryInput, ApiRequestContext requestContext) {

    EpsgCrs crs = queryInput.getQuery().getCrs().orElseGet(queryInput::getDefaultCrs);
    Axes axes = crsInfo.is3d(crs) ? Axes.XYZ : Axes.XY;

    Response feature = getCurrentFeature(queryInput, requestContext);
    EntityTag eTag = feature.getEntityTag();
    Date lastModified = feature.getLastModified();

    Response.ResponseBuilder response =
        queriesHandler.evaluatePreconditions(requestContext, lastModified, eTag);
    if (Objects.nonNull(response)) return response.build();

    FeatureTokenSource featureTokenSource =
        getFeatureSource(
            requestContext.getMediaType(),
            queryInput.getRequestBody(),
            Optional.empty(),
            crs,
            axes);

    FeatureTransactions.MutationResult result =
        queryInput
            .getFeatureProvider()
            .mutations()
            .get()
            .updateFeature(
                queryInput.getFeatureType(),
                queryInput.getFeatureId(),
                featureTokenSource,
                crs,
                false);

    result.getError().ifPresent(FeatureStream::processStreamError);

    Optional<BoundingBox> currentBbox = parseBboxHeader(feature);
    Optional<Tuple<Long, Long>> currentTime = parseTimeHeader(feature);

    handleChange(
        queryInput.getFeatureProvider(),
        queryInput.getCollectionId(),
        result.getIds(),
        currentBbox,
        result.getSpatialExtent(),
        convertTemporalExtentSecond(currentTime),
        convertTemporalExtentMillisecond(result.getTemporalExtent()),
        Action.UPDATE);

    return Response.noContent().build();
  }

  @Override
  public Response patchItemResponse(
      QueryInputFeatureReplace queryInput, ApiRequestContext requestContext) {

    EpsgCrs crs = queryInput.getQuery().getCrs().orElseGet(queryInput::getDefaultCrs);
    Axes axes = crsInfo.is3d(crs) ? Axes.XYZ : Axes.XY;

    Response feature = getCurrentFeature(queryInput, requestContext);
    EntityTag eTag = feature.getEntityTag();
    Date lastModified = feature.getLastModified();

    Response.ResponseBuilder response =
        queriesHandler.evaluatePreconditions(requestContext, lastModified, eTag);
    if (Objects.nonNull(response)) return response.build();

    byte[] prev = (byte[]) feature.getEntity();
    InputStream merged =
        new SequenceInputStream(new ByteArrayInputStream(prev), queryInput.getRequestBody());

    FeatureTokenSource mergedSource =
        getFeatureSource(
            requestContext.getMediaType(),
            merged,
            Optional.of(FeatureTransactions.PATCH_NULL_VALUE),
            crs,
            axes);

    FeatureTransactions.MutationResult result =
        queryInput
            .getFeatureProvider()
            .mutations()
            .get()
            .updateFeature(
                queryInput.getFeatureType(), queryInput.getFeatureId(), mergedSource, crs, true);

    result.getError().ifPresent(FeatureStream::processStreamError);

    Optional<BoundingBox> currentBbox = parseBboxHeader(feature);
    Optional<Tuple<Long, Long>> currentTime = parseTimeHeader(feature);

    handleChange(
        queryInput.getFeatureProvider(),
        queryInput.getCollectionId(),
        result.getIds(),
        currentBbox,
        result.getSpatialExtent(),
        convertTemporalExtentSecond(currentTime),
        convertTemporalExtentMillisecond(result.getTemporalExtent()),
        Action.UPDATE);

    return Response.noContent().build();
  }

  @Override
  public Response deleteItemResponse(
      QueryInputFeatureDelete queryInput, ApiRequestContext requestContext) {

    Response feature = getCurrentFeature(queryInput, requestContext);

    EntityTag eTag = feature.getEntityTag();
    Date lastModified = feature.getLastModified();

    Response.ResponseBuilder response =
        queriesHandler.evaluatePreconditions(requestContext, lastModified, eTag);
    if (Objects.nonNull(response)) {
      return response.build();
    }

    FeatureTransactions.MutationResult result =
        queryInput
            .getFeatureProvider()
            .mutations()
            .get()
            .deleteFeature(queryInput.getCollectionId(), queryInput.getFeatureId());

    result.getError().ifPresent(FeatureStream::processStreamError);

    Optional<BoundingBox> currentBbox = parseBboxHeader(feature);
    Optional<Tuple<Long, Long>> currentTime = parseTimeHeader(feature);

    handleChange(
        queryInput.getFeatureProvider(),
        queryInput.getCollectionId(),
        result.getIds(),
        currentBbox,
        result.getSpatialExtent(),
        convertTemporalExtentSecond(currentTime),
        convertTemporalExtentMillisecond(result.getTemporalExtent()),
        Action.DELETE);

    return Response.noContent().build();
  }

  private @NotNull Response getCurrentFeature(
      QueryInputFeatureWithQueryParameterSet queryInput, ApiRequestContext requestContext) {
    try {
      if (formats == null) {
        formats = extensionRegistry.getExtensionsForType(FeatureFormatExtension.class);
      }

      ApiRequestContext requestContextGeoJson =
          new Builder()
              .from(requestContext)
              .request(Optional.empty())
              .requestUri(
                  requestContext
                      .getUriCustomizer()
                      .clearParameters()
                      // query parameters have been evaluated and are not necessary here
                      .build())
              .queryParameterSet(queryInput.getQueryParameterSet())
              .mediaType(
                  new ImmutableApiMediaType.Builder()
                      .type(new MediaType("application", "geo+json"))
                      .label("GeoJSON")
                      .parameter("json")
                      .build())
              .alternateMediaTypes(
                  formats.stream()
                      .filter(
                          f ->
                              f.isEnabledForApi(
                                  requestContext.getApi().getData(), queryInput.getCollectionId()))
                      .map(FormatExtension::getMediaType)
                      .filter(
                          mediaType -> !"geo+json".equalsIgnoreCase(mediaType.type().getSubtype()))
                      .collect(Collectors.toUnmodifiableSet()))
              .build();

      return queriesHandler.handle(Query.FEATURE, queryInput, requestContextGeoJson);
    } catch (URISyntaxException e) {
      throw new IllegalStateException(
          String.format(
              "Could not retrieve current GeoJSON feature for evaluating preconditions. Reason: %s",
              e.getMessage()),
          e);
    }
  }

  private void handleChange(
      FeatureProvider featureProvider,
      String collectionId,
      List<String> ids,
      Optional<BoundingBox> oldBbox,
      Optional<BoundingBox> newBbox,
      Optional<Interval> oldInterval,
      Optional<Interval> newInterval,
      Action action) {
    FeatureChange change =
        ImmutableFeatureChange.builder()
            .action(action)
            .featureType(collectionId)
            .featureIds(ids)
            .oldBoundingBox(oldBbox)
            .newBoundingBox(newBbox)
            .oldInterval(oldInterval)
            .newInterval(newInterval)
            .build();
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Feature Change: {}", change);
    }
    featureProvider.changes().handle(change);
  }

  private static Optional<BoundingBox> parseBboxHeader(Response response) {
    // TODO this should use a structured fields parser (RFC 8941), but there are only experimental
    //      Java implementations
    String crsHeader = response.getHeaderString("Content-Crs");
    String bboxHeader = response.getHeaderString(FeaturesCoreQueriesHandler.BOUNDING_BOX_HEADER);
    return Objects.nonNull(crsHeader) && Objects.nonNull(bboxHeader)
        ? Optional.of(
            BoundingBox.of(
                bboxHeader, EpsgCrs.fromString(crsHeader.substring(1, crsHeader.length() - 1))))
        : Optional.empty();
  }

  private static Optional<Tuple<Long, Long>> parseTimeHeader(Response response) {
    // TODO this should use a structured fields parser (RFC 8941), but there are only experimental
    //      Java implementations
    String timeHeader = response.getHeaderString(FeaturesCoreQueriesHandler.TEMPORAL_EXTENT_HEADER);
    Optional<Tuple<Long, Long>> currentTime = Optional.empty();
    if (Objects.nonNull(timeHeader)) {
      Matcher startMatcher = Pattern.compile("^.*start\\s*=\\s*(\\d+).*$").matcher(timeHeader);
      boolean startIsSet = startMatcher.find();
      Matcher endMatcher = Pattern.compile("^.*end\\s*=\\s*(\\d+).*$").matcher(timeHeader);
      boolean endIsSet = endMatcher.find();
      if (startIsSet && endIsSet) {
        currentTime =
            Optional.of(
                Tuple.of(
                    Long.parseLong(startMatcher.group(1)), Long.parseLong(endMatcher.group(1))));
      } else if (startIsSet) {
        currentTime = Optional.of(Tuple.of(Long.parseLong(startMatcher.group(1)), null));
      } else if (endIsSet) {
        currentTime = Optional.of(Tuple.of(null, Long.parseLong(endMatcher.group(1))));
      }
    }
    return currentTime;
  }

  private Optional<Interval> convertTemporalExtentSecond(Optional<Tuple<Long, Long>> interval) {
    if (interval.isEmpty()) {
      return Optional.empty();
    }

    Long begin = interval.get().first();
    Long end = interval.get().second();

    Instant beginInstant = Objects.nonNull(begin) ? Instant.ofEpochSecond(begin) : Instant.MIN;
    Instant endInstant = Objects.nonNull(end) ? Instant.ofEpochSecond(end) : Instant.MAX;

    return Optional.of(Interval.of(beginInstant, endInstant));
  }

  private Optional<Interval> convertTemporalExtentMillisecond(
      Optional<Tuple<Long, Long>> interval) {
    if (interval.isEmpty()) {
      return Optional.empty();
    }

    Long begin = interval.get().first();
    Long end = interval.get().second();

    Instant beginInstant = Objects.nonNull(begin) ? Instant.ofEpochMilli(begin) : Instant.MIN;
    Instant endInstant = Objects.nonNull(end) ? Instant.ofEpochMilli(end) : Instant.MAX;

    return Optional.of(Interval.of(beginInstant, endInstant));
  }

  // TODO: to InputFormat extension matching the mediaType
  private static FeatureTokenSource getFeatureSource(
      ApiMediaType mediaType,
      InputStream requestBody,
      Optional<String> nullValue,
      EpsgCrs crs,
      Axes axes) {

    FeatureTokenDecoderGeoJson featureTokenDecoderGeoJson =
        new FeatureTokenDecoderGeoJson(nullValue, crs, axes);

    return Source.inputStream(requestBody).via(featureTokenDecoderGeoJson);
  }
}
