/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crud.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.gravity9.jsonpatch.mergepatch.JsonMergePatch;
import de.ii.ogcapi.collections.schema.domain.SchemaConfiguration;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler.Query;
import de.ii.ogcapi.features.core.domain.ImmutableQueryInputSchema;
import de.ii.ogcapi.features.core.domain.JsonSchemaCache;
import de.ii.ogcapi.features.core.domain.QueriesHandlerSchema;
import de.ii.ogcapi.features.core.domain.QueriesHandlerSchema.QueryInputSchema;
import de.ii.ogcapi.features.core.domain.SchemaType;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableStaticRequestContext;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.codelists.domain.Codelist;
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
import de.ii.xtraplatform.values.domain.ValueStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threeten.extra.Interval;

@Singleton
@AutoBind
public class CommandHandlerCrudImpl extends AbstractVolatileComposed implements CommandHandlerCrud {

  private static final Logger LOGGER = LoggerFactory.getLogger(CommandHandlerCrudImpl.class);
  private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new Jdk8Module());

  private final FeaturesCoreQueriesHandler queriesHandler;
  private final ExtensionRegistry extensionRegistry;
  private final CrsInfo crsInfo;
  private final QueriesHandlerSchema schemaHandler;
  private final JsonSchemaCache schemaCache;
  private final SchemaValidator schemaValidator;
  private List<? extends FormatExtension> formats;

  @Inject
  public CommandHandlerCrudImpl(
      FeaturesCoreQueriesHandler queriesHandler,
      CrsInfo crsInfo,
      QueriesHandlerSchema schemaHandler,
      ValueStore valueStore,
      ExtensionRegistry extensionRegistry,
      VolatileRegistry volatileRegistry,
      SchemaValidator schemaValidator) {
    super(CommandHandlerCrud.class.getSimpleName(), volatileRegistry, true);
    this.queriesHandler = queriesHandler;
    this.extensionRegistry = extensionRegistry;
    this.crsInfo = crsInfo;
    this.schemaHandler = schemaHandler;
    // note that we need a separate cache to avoid are circular dependency between the schemas and
    // crud modules
    this.schemaCache = new SchemaCacheCrud(valueStore.forType(Codelist.class)::asMap);
    this.schemaValidator = schemaValidator;

    onVolatileStart();

    addSubcomponent(queriesHandler);

    onVolatileStarted();
  }

  @Override
  public Response postItemsResponse(
      QueryInputFeatureCreate queryInput, ApiRequestContext requestContext) {
    InputStream contentStream = queryInput.getRequestBody();

    if (queryInput.getValidate()) {
      final String requestBody = getAsString(contentStream);
      validate(requestBody, queryInput.getCollectionId(), queryInput.getJsonFg(), requestContext);

      // "rewind" request body stream
      contentStream = new ByteArrayInputStream(requestBody.getBytes(StandardCharsets.UTF_8));
    }

    EpsgCrs crs = queryInput.getCrs();
    Axes axes = crsInfo.is3d(crs) ? Axes.XYZ : Axes.XY;

    FeatureTokenSource featureTokenSource =
        getFeatureSource(requestContext.getMediaType(), contentStream, Optional.empty(), crs, axes);

    return createFeature(queryInput, featureTokenSource, requestContext, Optional.empty());
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

    List<String> ids;
    Response response;

    if (featureId.isPresent()) {
      ids = List.of(featureId.get());
      response = Response.noContent().build();
    } else {
      ids = result.getIds();

      URI firstFeature = null;
      try {
        firstFeature =
            requestContext.getUriCustomizer().copy().ensureLastPathSegment(ids.get(0)).build();
      } catch (URISyntaxException e) {
        // ignore
      }

      response = Response.created(firstFeature).build();
    }

    if (ids.isEmpty()) {
      throw new IllegalArgumentException("No features found in input");
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

    return response;
  }

  @Override
  public Response putItemResponse(
      QueryInputFeatureReplace queryInput, ApiRequestContext requestContext) {
    InputStream contentStream = queryInput.getRequestBody();

    if (queryInput.getValidate()) {
      final String requestBody = getAsString(contentStream);
      validate(requestBody, queryInput.getCollectionId(), queryInput.getJsonFg(), requestContext);

      // "rewind" request body stream
      contentStream = new ByteArrayInputStream(requestBody.getBytes(StandardCharsets.UTF_8));
    }

    EpsgCrs crs = queryInput.getCrs();
    Axes axes = crsInfo.is3d(crs) ? Axes.XYZ : Axes.XY;

    FeatureTokenSource featureTokenSource =
        getFeatureSource(requestContext.getMediaType(), contentStream, Optional.empty(), crs, axes);
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

    EpsgCrs crs = queryInput.getQuery().getCrs().orElseGet(queryInput::getDefaultCrs);
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

  private static String getAsString(InputStream contentStream) {
    final String requestBody;
    try {
      requestBody = new String(contentStream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Could not read request body. Reasons: " + e.getMessage(), e);
    }
    return requestBody;
  }

  private void validate(
      String requestBody, String collectionId, boolean jsonfg, ApiRequestContext requestContext) {
    Optional<Profile> requestedProfile =
        extensionRegistry.getExtensionsForType(Profile.class).stream()
            .filter(
                profile ->
                    jsonfg
                        ? "validation-receivables-jsonfg".equals(profile.getId())
                        : "validation-receivables-geojson".equals(profile.getId()))
            .findFirst();

    Optional<SchemaConfiguration> schemaConfiguration =
        requestContext.getApi().getData().getExtension(SchemaConfiguration.class, collectionId);

    Map<String, String> defaultProfiles =
        schemaConfiguration.map(SchemaConfiguration::getDefaultProfiles).orElse(Map.of());
    List<Profile> defaultProfilesSchema =
        extensionRegistry.getExtensionsForType(Profile.class).stream()
            .filter(
                profile ->
                    defaultProfiles.containsKey(profile.getProfileSet())
                        && profile.getId().equals(defaultProfiles.get(profile.getProfileSet())))
            .toList();

    QueryInputSchema queryInputSchema =
        new ImmutableQueryInputSchema.Builder()
            .collectionId(collectionId)
            .profiles(List.of(requestedProfile.get()))
            .defaultProfilesResource(defaultProfilesSchema)
            .type(SchemaType.RETURNABLES_AND_RECEIVABLES)
            .schemaCache(this.schemaCache)
            .build();

    String schema;
    try (Response response =
        schemaHandler.handle(QueriesHandlerSchema.Query.SCHEMA, queryInputSchema, requestContext)) {
      schema = MAPPER.writeValueAsString(response.getEntity());
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(
          "Could not validate request body against the JSON Schema. Reason: " + e.getMessage());
    }

    Optional<String> validationResult;
    try {
      validationResult = schemaValidator.validate(schema, requestBody);
    } catch (IOException e) {
      throw new IllegalStateException("Could not validate feature. Reason: " + e.getMessage(), e);
    }

    if (validationResult.isPresent()) {
      throw new IllegalArgumentException(
          "Request body is invalid, feature creation is rejected: " + validationResult.get());
    }
  }
}
