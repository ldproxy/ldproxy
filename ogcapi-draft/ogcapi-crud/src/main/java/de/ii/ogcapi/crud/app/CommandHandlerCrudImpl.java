/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crud.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.gravity9.jsonpatch.mergepatch.JsonMergePatch;
import de.ii.ogcapi.collections.schema.domain.SchemaConfiguration;
import de.ii.ogcapi.features.core.domain.DecoderContext;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler.Query;
import de.ii.ogcapi.features.core.domain.ImmutableDecoderContext;
import de.ii.ogcapi.features.core.domain.ImmutableValidatorContext;
import de.ii.ogcapi.features.core.domain.ValidatorContext;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableStaticRequestContext;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.foundation.domain.ProfileExtension;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.CrsInfo;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.features.domain.FeatureChange;
import de.ii.xtraplatform.features.domain.FeatureChange.Action;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.FeatureStream;
import de.ii.xtraplatform.features.domain.FeatureTokenSource;
import de.ii.xtraplatform.features.domain.FeatureTransactions;
import de.ii.xtraplatform.features.domain.ImmutableFeatureChange;
import de.ii.xtraplatform.features.domain.Tuple;
import de.ii.xtraplatform.features.json.domain.FeatureTokenDecoderGeoJson;
import de.ii.xtraplatform.geometries.domain.Axes;
import de.ii.xtraplatform.streams.domain.Reactive.Source;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threeten.extra.Interval;

@Singleton
@AutoBind
public class CommandHandlerCrudImpl extends AbstractVolatileComposed implements CommandHandlerCrud {

  private static final Logger LOGGER = LoggerFactory.getLogger(CommandHandlerCrudImpl.class);

  private final FeaturesCoreQueriesHandler queriesHandler;
  private final FeaturesCoreProviders providers;
  private final ExtensionRegistry extensionRegistry;
  private final CrsInfo crsInfo;
  private List<? extends FormatExtension> formats;

  @Inject
  public CommandHandlerCrudImpl(
      FeaturesCoreQueriesHandler queriesHandler,
      FeaturesCoreProviders providers,
      CrsInfo crsInfo,
      ExtensionRegistry extensionRegistry,
      VolatileRegistry volatileRegistry) {
    super(CommandHandlerCrud.class.getSimpleName(), volatileRegistry, true);
    this.queriesHandler = queriesHandler;
    this.providers = providers;
    this.extensionRegistry = extensionRegistry;
    this.crsInfo = crsInfo;

    onVolatileStart();

    addSubcomponent(queriesHandler);

    onVolatileStarted();
  }

  @Override
  public Response postItemsResponse(
      QueryInputFeatureCreate queryInput, ApiRequestContext requestContext) {
    FeatureTokenSource featureTokenSource = getFeatureTokenSource(queryInput, requestContext);

    return createFeature(queryInput, featureTokenSource, requestContext, Optional.empty());
  }

  private FeatureTokenSource getFeatureTokenSource(
      QueryInputFeatureCreate queryInput, ApiRequestContext requestContext) {
    InputStream contentStream = queryInput.getRequestBody();

    if (queryInput.getValidate()) {
      FeatureFormatExtension format = resolveFormat(queryInput.getContentType());
      ValidatorContext vctx = buildValidatorContext(queryInput, requestContext);

      final String body;
      try {
        body = new String(contentStream.readAllBytes(), StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new IllegalStateException(
            "Could not read content stream. Reasons: " + e.getMessage(), e);
      }
      format.validate(body, vctx);
      contentStream = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

    EpsgCrs crs = queryInput.getCrs();
    Axes axes = crsInfo.is3d(crs) ? Axes.XYZ : Axes.XY;

    return getFeatureSource(
        queryInput.getContentType(),
        contentStream,
        requestContext.getApi().getData(),
        queryInput.getCollectionId(),
        crs,
        axes);
  }

  private Response createFeature(
      QueryInputFeatureCreate queryInput,
      FeatureTokenSource featureTokenSource,
      ApiRequestContext requestContext,
      Optional<String> featureId) {
    FeatureTransactions.MutationResult result =
        queryInput
            .getFeatureProvider()
            .mutations()
            .get()
            .createFeatures(
                queryInput.getFeatureType(), featureTokenSource, queryInput.getCrs(), featureId);

    result.getError().ifPresent(FeatureStream::processStreamError);

    List<String> ids = featureId.map(List::of).orElseGet(result::getIds);

    if (ids.isEmpty()) {
      throw new IllegalArgumentException("No features found in input");
    }

    URI location = null;
    try {
      location = requestContext.getUriCustomizer().copy().ensureLastPathSegment(ids.get(0)).build();
    } catch (URISyntaxException e) {
      // ignore
    }

    Response response = Response.created(location).build();

    handleChange(
        queryInput.getFeatureProvider(),
        queryInput.getCollectionId(),
        ids,
        Optional.empty(),
        result.getSpatialExtent(),
        Optional.empty(),
        convertTemporalExtentMillisecond(result.getTemporalExtent()),
        Action.CREATE);

    return response;
  }

  @Override
  public Response putItemResponse(
      QueryInputFeatureReplace queryInput, ApiRequestContext requestContext) {
    FeatureTokenSource featureTokenSource = getFeatureTokenSource(queryInput, requestContext);
    Response previousFeature = null;

    try {
      previousFeature = getCurrentFeature(queryInput, requestContext);
    } catch (NotFoundException e) {
      if (!queryInput.isAllowCreate()) {
        throw e;
      }
    }

    if (Objects.isNull(previousFeature) && queryInput.isAllowCreate()) {
      return createFeature(
          queryInput,
          featureTokenSource,
          requestContext,
          Optional.ofNullable(queryInput.getFeatureId()));
    }

    Date lastModified = previousFeature.getLastModified();

    Response.ResponseBuilder response =
        queriesHandler.evaluatePreconditions(requestContext, lastModified, null);

    if (Objects.nonNull(response)) {
      return response.build();
    }

    return updateFeature(queryInput, featureTokenSource, previousFeature);
  }

  private Response updateFeature(
      QueryInputFeatureReplace queryInput,
      FeatureTokenSource featureTokenSource,
      Response previousFeature) {
    FeatureTransactions.MutationResult result =
        queryInput
            .getFeatureProvider()
            .mutations()
            .get()
            .updateFeature(
                queryInput.getFeatureType(),
                queryInput.getFeatureId(),
                featureTokenSource,
                queryInput.getCrs(),
                false);

    result.getError().ifPresent(FeatureStream::processStreamError);

    Optional<BoundingBox> currentBbox = parseBboxHeader(previousFeature);
    Optional<Tuple<Long, Long>> currentTime = parseTimeHeader(previousFeature);

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

    EpsgCrs crs = queryInput.getCrs();
    Axes axes = crsInfo.is3d(crs) ? Axes.XYZ : Axes.XY;

    Response feature = getCurrentFeature(queryInput, requestContext);
    Date lastModified = feature.getLastModified();

    Response.ResponseBuilder response =
        queriesHandler.evaluatePreconditions(requestContext, lastModified, null);
    if (Objects.nonNull(response)) return response.build();

    byte[] prev = (byte[]) feature.getEntity();
    final ObjectMapper mapper = new ObjectMapper();
    InputStream merged;

    try {
      final JsonMergePatch patch =
          mapper.readValue(queryInput.getRequestBody(), JsonMergePatch.class);
      JsonNode orig = mapper.readTree(prev);
      JsonNode mergedNode = patch.apply(orig);
      merged = new ByteArrayInputStream(mapper.writeValueAsBytes(mergedNode));
    } catch (Throwable e) {
      throw new IllegalArgumentException(
          "Could not parse request body as JSON Merge Patch: " + e.getMessage(), e);
    }

    FeatureTokenSource mergedSource = getMergePatchSource(merged, crs, axes);

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

    Date lastModified = feature.getLastModified();

    Response.ResponseBuilder response =
        queriesHandler.evaluatePreconditions(requestContext, lastModified, null);
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
      QueryInputFeatureCrud queryInput, ApiRequestContext requestContext) {
    try {
      if (formats == null) {
        formats = extensionRegistry.getExtensionsForType(FeatureFormatExtension.class);
      }

      ApiRequestContext requestContextGeoJson =
          new ImmutableStaticRequestContext.Builder()
              .from(requestContext)
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

  private FeatureTokenSource getFeatureSource(
      MediaType contentType,
      InputStream requestBody,
      OgcApiDataV2 apiData,
      String collectionId,
      EpsgCrs crs,
      Axes axes) {

    FeatureFormatExtension format = resolveFormat(contentType);

    FeatureSchema featureSchema =
        providers
            .getFeatureSchema(apiData, apiData.getCollections().get(collectionId))
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No feature schema for collection '" + collectionId + "'"));

    DecoderContext ctx =
        new ImmutableDecoderContext.Builder()
            .apiData(apiData)
            .collectionId(collectionId)
            .featureSchema(featureSchema)
            .crs(crs)
            .axes(axes)
            .mediaType(contentType)
            .build();

    return Source.inputStream(requestBody).via(format.getFeatureDecoder(ctx).get());
  }

  private static FeatureTokenSource getMergePatchSource(
      InputStream requestBody, EpsgCrs crs, Axes axes) {
    return Source.inputStream(requestBody)
        .via(
            new FeatureTokenDecoderGeoJson(
                Optional.of(FeatureTransactions.PATCH_NULL_VALUE), crs, axes));
  }

  private FeatureFormatExtension resolveFormat(MediaType mediaType) {
    return extensionRegistry.getExtensionsForType(FeatureFormatExtension.class).stream()
        .filter(FeatureFormatExtension::canSupportTransactions)
        .filter(f -> f.getMediaType().type().isCompatible(mediaType))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No transaction-capable feature format for media type " + mediaType));
  }

  private ValidatorContext buildValidatorContext(
      QueryInputFeatureCreate queryInput, ApiRequestContext requestContext) {
    return new ImmutableValidatorContext.Builder()
        .apiData(requestContext.getApi().getData())
        .collectionId(queryInput.getCollectionId())
        .mediaType(queryInput.getContentType())
        .type(ValidatorContext.Type.RECEIVABLES)
        .requestContext(requestContext)
        .declaredProfiles(
            queryInput.getLinkHeaders().stream()
                .flatMap(s -> Arrays.stream(s.split(",")))
                .filter(l -> l.contains("rel=\"profile\"") || l.contains("rel=profile"))
                .map(
                    l -> {
                      int start = l.indexOf('<');
                      int end = l.indexOf('>');
                      return start >= 0 && end > start ? l.substring(start + 1, end) : "";
                    })
                .map(
                    url ->
                        extensionRegistry.getExtensionsForType(Profile.class).stream()
                            .filter(p -> url.equals(ProfileExtension.getUri(p)))
                            .findFirst())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList())
        .defaultProfiles(
            resolveDefaultProfilesSchema(
                requestContext.getApi().getData(), queryInput.getCollectionId()))
        .build();
  }

  private List<Profile> resolveDefaultProfilesSchema(OgcApiDataV2 apiData, String collectionId) {
    Map<String, String> defaults =
        apiData
            .getExtension(SchemaConfiguration.class, collectionId)
            .map(SchemaConfiguration::getDefaultProfiles)
            .orElse(Map.of());
    return extensionRegistry.getExtensionsForType(Profile.class).stream()
        .filter(
            p ->
                defaults.containsKey(p.getProfileSet())
                    && p.getId().equals(defaults.get(p.getProfileSet())))
        .toList();
  }
}
