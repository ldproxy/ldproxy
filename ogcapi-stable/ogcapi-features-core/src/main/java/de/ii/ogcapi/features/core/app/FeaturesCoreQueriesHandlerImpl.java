/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.core.domain.DelayedOutputStream;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeatureLinksGenerator;
import de.ii.ogcapi.features.core.domain.FeatureTransformationQueryParameter;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.features.core.domain.FeaturesLinksGenerator;
import de.ii.ogcapi.features.core.domain.ImmutableFeatureTransformationContextGeneric;
import de.ii.ogcapi.features.core.domain.ProfileFeatureQuery;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.HeaderCaching;
import de.ii.ogcapi.foundation.domain.HeaderContentDisposition;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.foundation.domain.ProfileExtension.ResourceType;
import de.ii.ogcapi.foundation.domain.ProfileSet;
import de.ii.ogcapi.foundation.domain.QueriesHandler;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.CrsTransformer;
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.features.domain.CollectionMetadata;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.FeatureStream;
import de.ii.xtraplatform.features.domain.FeatureStream.Result;
import de.ii.xtraplatform.features.domain.FeatureStream.ResultBase;
import de.ii.xtraplatform.features.domain.FeatureStream.ResultReduced;
import de.ii.xtraplatform.features.domain.FeatureTokenEncoder;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.domain.Tuple;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformations;
import de.ii.xtraplatform.geometries.domain.GeometryType;
import de.ii.xtraplatform.streams.domain.OutputStreamToByteConsumer;
import de.ii.xtraplatform.streams.domain.Reactive.Sink;
import de.ii.xtraplatform.streams.domain.Reactive.SinkReduced;
import de.ii.xtraplatform.streams.domain.Reactive.SinkTransformed;
import de.ii.xtraplatform.strings.domain.StringTemplateFilters;
import de.ii.xtraplatform.values.domain.ValueStore;
import de.ii.xtraplatform.values.domain.Values;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class FeaturesCoreQueriesHandlerImpl extends AbstractVolatileComposed
    implements FeaturesCoreQueriesHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(FeaturesCoreQueriesHandlerImpl.class);

  private final I18n i18n;
  private final CrsTransformerFactory crsTransformerFactory;
  private final Map<Query, QueryHandler<? extends QueryInput>> queryHandlers;
  private final Values<Codelist> codelistStore;
  private final ExtensionRegistry extensionRegistry;

  @Inject
  public FeaturesCoreQueriesHandlerImpl(
      I18n i18n,
      CrsTransformerFactory crsTransformerFactory,
      ValueStore valueStore,
      VolatileRegistry volatileRegistry,
      ExtensionRegistry extensionRegistry) {
    super(FeaturesCoreQueriesHandler.class.getSimpleName(), volatileRegistry, true);
    this.i18n = i18n;
    this.crsTransformerFactory = crsTransformerFactory;
    this.codelistStore = valueStore.forType(Codelist.class);
    this.extensionRegistry = extensionRegistry;

    this.queryHandlers =
        ImmutableMap.of(
            Query.FEATURES, QueryHandler.with(QueryInputFeatures.class, this::getItemsResponse),
            Query.FEATURE, QueryHandler.with(QueryInputFeature.class, this::getItemResponse));

    onVolatileStart();

    addSubcomponent(crsTransformerFactory);

    onVolatileStarted();
  }

  @Override
  public Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers() {
    return queryHandlers;
  }

  private Response getItemsResponse(
      QueryInputFeatures queryInput, ApiRequestContext requestContext) {

    OgcApi api = requestContext.getApi();
    String collectionId = queryInput.getCollectionId();
    FeatureQuery query = queryInput.getQuery();

    Optional<Integer> defaultPageSize = queryInput.getDefaultPageSize();

    FeatureFormatExtension outputFormat =
        api.getOutputFormat(
                FeatureFormatExtension.class,
                requestContext.getMediaType(),
                Optional.of(collectionId))
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType().type())));

    if (query.hitsOnly() && !outputFormat.supportsHitsOnly()) {
      throw new NotAcceptableException(
          MessageFormat.format(
              "The requested media type ''{0}'' does not support ''resultType=hits''.",
              requestContext.getMediaType().type()));
    }

    return getResponse(
        api,
        requestContext,
        collectionId,
        null,
        queryInput,
        query,
        queryInput.getFeatureProvider(),
        null,
        outputFormat,
        defaultPageSize,
        queryInput.getIncludeLinkHeader(),
        queryInput.getDefaultCrs(),
        queryInput.sendResponseAsStream());
  }

  private Response getItemResponse(QueryInputFeature queryInput, ApiRequestContext requestContext) {

    OgcApi api = requestContext.getApi();
    String collectionId = queryInput.getCollectionId();
    String featureId = queryInput.getFeatureId();
    FeatureQuery query = queryInput.getQuery();

    FeatureFormatExtension outputFormat =
        api.getOutputFormat(
                FeatureFormatExtension.class,
                requestContext.getMediaType(),
                Optional.of(collectionId))
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    if (outputFormat.getMediaType().type().equals(MediaType.TEXT_HTML_TYPE)
        && !api.getData()
            .getExtension(HtmlConfiguration.class, collectionId)
            .map(HtmlConfiguration::getSendEtags)
            .orElse(false)) {
      query = ImmutableFeatureQuery.builder().from(query).eTag(Optional.empty()).build();
    }

    String persistentUri = null;
    Optional<String> template =
        api.getData().getCollections().get(collectionId).getPersistentUriTemplate();
    if (template.isPresent()) {
      persistentUri = StringTemplateFilters.applyTemplate(template.get(), featureId);
    }

    boolean sendResponseAsStream =
        outputFormat.getMediaType().type().equals(MediaType.TEXT_HTML_TYPE)
            && api.getData()
                .getExtension(HtmlConfiguration.class, collectionId)
                .map(HtmlConfiguration::getSendEtags)
                .orElse(false);

    return getResponse(
        api,
        requestContext,
        collectionId,
        featureId,
        queryInput,
        query,
        queryInput.getFeatureProvider(),
        persistentUri,
        outputFormat,
        Optional.empty(),
        queryInput.getIncludeLinkHeader(),
        queryInput.getDefaultCrs(),
        sendResponseAsStream);
  }

  private Response getResponse(
      OgcApi api,
      ApiRequestContext requestContext,
      String collectionId,
      String featureId,
      QueryInputFeaturesBase queryInput,
      FeatureQuery query,
      FeatureProvider featureProvider,
      String canonicalUri,
      FeatureFormatExtension outputFormat,
      Optional<Integer> defaultPageSize,
      boolean includeLinkHeader,
      EpsgCrs defaultCrs,
      boolean sendResponseAsStream) {

    QueriesHandler.ensureCollectionIdExists(api.getData(), collectionId);
    QueriesHandler.ensureFeatureProviderSupportsQueries(featureProvider);

    Optional<CrsTransformer> crsTransformer = Optional.empty();

    EpsgCrs sourceCrs = null;
    EpsgCrs targetCrs = query.getCrs().orElse(defaultCrs);
    if (featureProvider.crs().isAvailable()) {
      sourceCrs = featureProvider.crs().get().getNativeCrs();
      crsTransformer = crsTransformerFactory.getTransformer(sourceCrs, targetCrs);
    }

    List<ApiMediaType> alternateMediaTypes = requestContext.getAlternateMediaTypes();

    List<ProfileSet> allProfileSets = extensionRegistry.getExtensionsForType(ProfileSet.class);

    List<Profile> profiles =
        negotiateProfiles(
            allProfileSets,
            outputFormat,
            ResourceType.FEATURE,
            api.getData(),
            Optional.of(collectionId),
            queryInput.getProfiles(),
            queryInput.getDefaultProfilesResource());

    Map<ApiMediaType, List<Profile>> alternateProfiles =
        getAlternateProfiles(
            allProfileSets,
            api.getData(),
            collectionId,
            requestContext.getMediaType(),
            requestContext.getAlternateMediaTypes(),
            queryInput.getProfiles());

    List<Link> links =
        Objects.isNull(featureId)
            ? new FeaturesLinksGenerator()
                .generateLinks(
                    requestContext.getUriCustomizer(),
                    query.getOffset(),
                    query.getLimit(),
                    defaultPageSize.orElse(0),
                    requestContext.getMediaType(),
                    alternateMediaTypes,
                    profiles,
                    alternateProfiles,
                    i18n,
                    requestContext.getLanguage())
            : new FeatureLinksGenerator()
                .generateLinks(
                    requestContext.getUriCustomizer(),
                    requestContext.getMediaType(),
                    alternateMediaTypes,
                    profiles,
                    alternateProfiles,
                    outputFormat.getCollectionMediaType(),
                    canonicalUri,
                    i18n,
                    requestContext.getLanguage());

    String featureTypeId =
        api.getData()
            .getCollections()
            .get(collectionId)
            .getExtension(FeaturesCoreConfiguration.class)
            .map(cfg -> cfg.getFeatureType().orElse(collectionId))
            .orElse(collectionId);

    Optional<FeatureSchema> schema = featureProvider.info().getSchema(featureTypeId);

    if (schema.filter(SchemaBase::hasEmbeddedFeature).isPresent()
        && !outputFormat.supportsEmbedding()) {
      throw new NotAcceptableException(
          "The requested media type does not support embedding. Please contact the server administrator.");
    }

    ImmutableFeatureTransformationContextGeneric.Builder transformationContext =
        new ImmutableFeatureTransformationContextGeneric.Builder()
            .api(api)
            .apiData(api.getData())
            .featureSchemas(ImmutableMap.of(collectionId, schema))
            .ogcApiRequest(requestContext)
            .profiles(profiles)
            .crsTransformer(crsTransformer)
            .codelists(codelistStore.asMap())
            .defaultCrs(defaultCrs)
            .sourceCrs(Optional.ofNullable(sourceCrs))
            .links(links)
            .isFeatureCollection(Objects.isNull(featureId))
            .isHitsOnly(query.hitsOnly())
            .fields(ImmutableMap.of(collectionId, query.getFields()))
            .limit(query.getLimit())
            .offset(query.getOffset())
            .maxAllowableOffset(query.getMaxAllowableOffset())
            .geometryPrecision(query.getGeometryPrecision())
            .wgs84GeometryPrecision(query.getWgs84GeometryPrecision());

    if (outputFormat.isRestrictedToSimpleFeaturesGeometries(profiles)) {
      Set<GeometryType> geometryTypes = featureProvider.info().getGeometryTypes();
      if (!GeometryType.onlySimpleFeatureGeometries(geometryTypes)) {
        query =
            ImmutableFeatureQuery.builder().from(query).forceSimpleFeatureGeometry(true).build();
      }
    }

    QueryParameterSet queryParameterSet = requestContext.getQueryParameterSet();
    for (OgcApiQueryParameter parameter : queryParameterSet.getDefinitions()) {
      if (parameter instanceof FeatureTransformationQueryParameter) {
        ((FeatureTransformationQueryParameter) parameter)
            .applyTo(transformationContext, queryParameterSet);
      }
    }

    for (Profile profile : profiles) {
      if (profile instanceof ProfileFeatureQuery) {
        query = ((ProfileFeatureQuery) profile).transformFeatureQuery(query);
      }
    }

    FeatureStream featureStream;
    FeatureTokenEncoder<?> encoder;
    Map<String, PropertyTransformations> propertyTransformations = ImmutableMap.of();

    if (outputFormat.canPassThroughFeatures()
        && featureProvider.passThrough().isAvailable()
        && outputFormat
            .getMediaType()
            .matches(featureProvider.passThrough().get().getMediaType())) {
      featureStream = featureProvider.passThrough().get().getFeatureStreamPassThrough(query);
      ImmutableFeatureTransformationContextGeneric transformationContextGeneric =
          transformationContext.outputStream(new OutputStreamToByteConsumer()).build();
      encoder =
          outputFormat
              .getFeatureEncoderPassThrough(
                  transformationContextGeneric, requestContext.getLanguage())
              .get();
    } else if (outputFormat.canEncodeFeatures()) {
      if (!featureProvider.queries().isAvailable()) {
        throw new ServiceUnavailableException(
            "The API currently cannot access features. Please try again later.");
      }
      featureStream = featureProvider.queries().get().getFeatureStream(query);

      ImmutableFeatureTransformationContextGeneric transformationContextGeneric =
          transformationContext.outputStream(new OutputStreamToByteConsumer()).build();
      encoder =
          outputFormat
              .getFeatureEncoder(transformationContextGeneric, requestContext.getLanguage())
              .get();

      propertyTransformations =
          outputFormat
              .getPropertyTransformations(
                  api.getData(), api.getData().getCollections().get(collectionId), schema, profiles)
              .map(
                  pt ->
                      ImmutableMap.of(
                          featureTypeId,
                          pt.withSubstitutions(
                              FeaturesCoreProviders.DEFAULT_SUBSTITUTIONS.apply(
                                  transformationContextGeneric.getServiceUrl()))))
              .orElse(ImmutableMap.of());
    } else {
      throw new NotAcceptableException(
          MessageFormat.format(
              "The requested media type {0} cannot be generated, because it does not support streaming.",
              requestContext.getMediaType().type()));
    }

    Date lastModified = getLastModified(queryInput);
    EntityTag etag = null;
    String spatialExtentHeader = null;
    String temporalExtentHeader = null;
    byte[] bytes = null;
    StreamingOutput streamingOutput = null;
    CollectionMetadata collectionMetadata = null;
    boolean hasNextPage = false;

    if (!sendResponseAsStream) {
      Tuple<ResultReduced<byte[]>, CollectionMetadata> resultAndMetadata =
          reduce(featureStream, Objects.nonNull(featureId), encoder, propertyTransformations);
      ResultReduced<byte[]> result = resultAndMetadata.first();
      collectionMetadata = resultAndMetadata.second();
      hasNextPage =
          collectionMetadata != null
              && collectionMetadata.getNumberReturned().orElse(0) == query.getLimit();

      bytes = result.reduced();

      if (result.getETag().isPresent()) {
        etag = result.getETag().get();
        LOGGER.debug("ETag {}", etag);
      }

      if (result.getLastModified().isPresent() && Objects.isNull(lastModified)) {
        lastModified = Date.from(result.getLastModified().get());
        LOGGER.debug("Last-Modified {}", lastModified);
      }

      if (result.getSpatialExtent().isPresent()) {
        // Structured Fields does only support 3 decimal places for some reason. We still provide
        // all decimal places, but other parsers will likely round the values to 3 places. The
        // min values need to be rounded down, the max values need to be rounded up to ensure that
        // the coordinates are in the bbox.
        BoundingBox bbox = result.getSpatialExtent().get();
        spatialExtentHeader =
            String.format(
                Locale.US,
                "%f, %f, %f, %f",
                bbox.getXmin(),
                bbox.getYmin(),
                bbox.getXmax(),
                bbox.getYmax());
      }

      if (result.getTemporalExtent().isPresent()) {
        // Structured Fields does not support timestamps, so the typical approach is to use seconds
        // since epoch
        Tuple<Instant, Instant> interval = result.getTemporalExtent().get();
        if (Objects.nonNull(interval.first()) && Objects.nonNull(interval.second())) {
          temporalExtentHeader =
              String.format(
                  "start=%d, end=%d",
                  interval.first().getEpochSecond(), interval.second().getEpochSecond());
        } else if (Objects.nonNull(interval.first())) {
          temporalExtentHeader = String.format("start=%d", interval.first().getEpochSecond());
        } else if (Objects.nonNull(interval.second())) {
          temporalExtentHeader = String.format("end=%d", interval.second().getEpochSecond());
        }
      }
    }

    Response.ResponseBuilder responsePre =
        evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(responsePre)) {
      return responsePre.build();
    }

    if (sendResponseAsStream) {
      Tuple<StreamingOutput, CollectionMetadata> streamingOutputAndMetadata =
          stream(featureStream, Objects.nonNull(featureId), encoder, propertyTransformations);
      streamingOutput = streamingOutputAndMetadata.first();
      collectionMetadata = streamingOutputAndMetadata.second();
      hasNextPage =
          collectionMetadata != null
              && collectionMetadata.getNumberReturned().orElse(0) == query.getLimit();
    }

    List<Link> filteredLinks =
        includeLinkHeader
            ? hasNextPage
                ? links
                : links.stream()
                    .filter(link -> !"next".equalsIgnoreCase(link.getRel()))
                    .collect(ImmutableList.toImmutableList())
            : null;

    Response.ResponseBuilder response =
        prepareSuccessResponse(
            requestContext,
            filteredLinks,
            HeaderCaching.of(lastModified, etag, queryInput),
            outputFormat.getContentCrs(targetCrs),
            HeaderContentDisposition.of(
                String.format(
                    "%s.%s",
                    Objects.isNull(featureId) ? collectionId : featureId,
                    outputFormat.getMediaType().fileExtension())),
            Objects.isNull(featureId) ? collectionMetadata : null,
            i18n.getLanguages());

    if (Objects.nonNull(spatialExtentHeader)) {
      response.header(BOUNDING_BOX_HEADER, spatialExtentHeader);
    }

    if (Objects.nonNull(temporalExtentHeader)) {
      response.header(TEMPORAL_EXTENT_HEADER, temporalExtentHeader);
    }

    return response.entity(Objects.nonNull(bytes) ? bytes : streamingOutput).build();
  }

  private Tuple<StreamingOutput, CollectionMetadata> stream(
      FeatureStream featureTransformStream,
      boolean failIfNoFeatures,
      final FeatureTokenEncoder<?> encoder,
      Map<String, PropertyTransformations> propertyTransformations) {
    DelayedOutputStream delayedOutputStream = new DelayedOutputStream();
    SinkTransformed<Object, byte[]> featureSink =
        encoder.to(Sink.outputStream(delayedOutputStream));
    CompletableFuture<CollectionMetadata> onCollectionMetadata = new CompletableFuture<>();

    // start stream asynchronously
    CompletableFuture<Result> stream =
        featureTransformStream
            .runWith(featureSink, propertyTransformations, onCollectionMetadata)
            .toCompletableFuture();

    // wait for collection metadata
    CollectionMetadata collectionMetadata = onCollectionMetadata.join();

    StreamingOutput streamingOutput =
        outputStream -> {
          delayedOutputStream.setOutputStream(outputStream);

          // wait for stream to finish
          run(stream::join, failIfNoFeatures);
        };

    return Tuple.of(streamingOutput, collectionMetadata);
  }

  private Tuple<ResultReduced<byte[]>, CollectionMetadata> reduce(
      FeatureStream featureTransformStream,
      boolean failIfNoFeatures,
      final FeatureTokenEncoder<?> encoder,
      Map<String, PropertyTransformations> propertyTransformations) {

    SinkReduced<Object, byte[]> featureSink = encoder.to(Sink.reduceByteArray());
    CompletableFuture<CollectionMetadata> onCollectionMetadata = new CompletableFuture<>();

    // start stream asynchronously
    CompletableFuture<ResultReduced<byte[]>> stream =
        featureTransformStream
            .runWith(featureSink, propertyTransformations, onCollectionMetadata)
            .toCompletableFuture();

    // wait for collection metadata
    CollectionMetadata collectionMetadata = onCollectionMetadata.join();

    return Tuple.of(run(stream::join, failIfNoFeatures), collectionMetadata);
  }

  private <U extends ResultBase> U run(Supplier<U> stream, boolean failIfNoFeatures) {
    try {
      U result = stream.get();

      result.getError().ifPresent(FeatureStream::processStreamError);

      if (failIfNoFeatures && !result.hasFeatures()) {
        throw new NotFoundException("The requested feature does not exist.");
      }

      return result;

    } catch (CompletionException e) {
      if (e.getCause() instanceof WebApplicationException) {
        throw (WebApplicationException) e.getCause();
      }
      throw new IllegalStateException("Feature stream error.", e.getCause());
    }
  }
}
