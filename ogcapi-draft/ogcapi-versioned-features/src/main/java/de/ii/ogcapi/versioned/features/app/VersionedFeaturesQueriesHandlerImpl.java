/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.DefaultLinksGenerator;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.versioned.features.domain.EncodingContextTimeMap;
import de.ii.ogcapi.versioned.features.domain.FeatureEncoderTimeMap;
import de.ii.ogcapi.versioned.features.domain.ImmutableEncodingContextTimeMap;
import de.ii.ogcapi.versioned.features.domain.TimeMapFormatExtension;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesQueriesHandler;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatile;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2.State;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.cql.domain.In;
import de.ii.xtraplatform.cql.domain.ScalarLiteral;
import de.ii.xtraplatform.features.domain.CollectionMetadata;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.FeatureStream.ResultReduced;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.domain.SortKey;
import de.ii.xtraplatform.features.domain.Tuple;
import de.ii.xtraplatform.streams.domain.Reactive.Sink;
import de.ii.xtraplatform.streams.domain.Reactive.SinkReduced;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotAcceptableException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class VersionedFeaturesQueriesHandlerImpl extends AbstractVolatile
    implements VersionedFeaturesQueriesHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(VersionedFeaturesQueriesHandlerImpl.class);

  private final FeaturesCoreProviders providers;
  private final I18n i18n;
  private final Map<Query, QueryHandler<? extends QueryInput>> queryHandlers;

  @Inject
  public VersionedFeaturesQueriesHandlerImpl(
      FeaturesCoreProviders providers, I18n i18n, VolatileRegistry volatileRegistry) {
    super(volatileRegistry, VersionedFeaturesQueriesHandler.class.getSimpleName());
    this.providers = providers;
    this.i18n = i18n;
    this.queryHandlers =
        ImmutableMap.of(
            Query.TIME_MAP, QueryHandler.with(QueryInputTimeMap.class, this::getTimeMapResponse));

    onVolatileStart();
    setState(State.AVAILABLE);
  }

  @Override
  public Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers() {
    return queryHandlers;
  }

  private Response getTimeMapResponse(
      QueryInputTimeMap queryInput, ApiRequestContext requestContext) {
    OgcApi api = requestContext.getApi();
    OgcApiDataV2 apiData = api.getData();
    String collectionId = queryInput.getCollectionId();
    String featureId = queryInput.getFeatureId();

    FeatureTypeConfigurationOgcApi collectionData = apiData.getCollections().get(collectionId);
    if (collectionData == null) {
      throw new NotFoundException(
          String.format("The collection '%s' does not exist in this API.", collectionId));
    }
    String featureTypeId =
        collectionData
            .getExtension(FeaturesCoreConfiguration.class)
            .flatMap(FeaturesCoreConfiguration::getFeatureType)
            .orElse(collectionId);

    FeatureProvider provider = queryInput.getFeatureProvider();
    if (!provider.queries().isAvailable()) {
      throw new NotFoundException("Feature queries are not available.");
    }

    Tuple<FeatureSchema, FeatureSchema> primaryInterval =
        providers
            .getQueryablesSchema(apiData, collectionData)
            .flatMap(SchemaBase::getPrimaryInterval)
            .orElseThrow(
                () -> new NotFoundException("Versioning is not enabled for this collection."));

    String startFieldPath = Objects.requireNonNull(primaryInterval.first()).getFullPathAsString();
    ImmutableList.Builder<String> fields = ImmutableList.<String>builder().add(startFieldPath);
    Optional.ofNullable(primaryInterval.second())
        .map(FeatureSchema::getFullPathAsString)
        .ifPresent(fields::add);

    TimeMapFormatExtension outputFormat =
        api.getOutputFormat(
                TimeMapFormatExtension.class, requestContext.getMediaType(), Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    String featureHref =
        requestContext
            .getUriCustomizer()
            .copy()
            .clearParameters()
            .cutPathAfterSegments("items", featureId)
            .toString();

    // Standard self + alternate links via DefaultLinksGenerator — self carries ?f=<this format>
    // when other representations exist; one alternate per other representation.
    List<Link> resourceLinks =
        new DefaultLinksGenerator()
            .generateLinks(
                requestContext.getUriCustomizer(),
                requestContext.getMediaType(),
                requestContext.getAlternateMediaTypes(),
                i18n,
                requestContext.getLanguage());

    boolean prettify =
        Boolean.TRUE.equals(requestContext.getQueryParameterSet().getTypedValues().get("pretty"));

    EncodingContextTimeMap encodingContext =
        new ImmutableEncodingContextTimeMap.Builder()
            .collectionId(collectionId)
            .featureId(featureId)
            .featureHref(featureHref)
            .resourceLinks(resourceLinks)
            .api(api)
            .requestContext(requestContext)
            .i18n(i18n)
            .prettify(prettify)
            .build();

    FeatureEncoderTimeMap encoder = outputFormat.getFeatureEncoder(encodingContext);

    FeatureQuery probe =
        ImmutableFeatureQuery.builder()
            .type(featureTypeId)
            .filter(In.of(ScalarLiteral.of(featureId)))
            .limit(10_000)
            .addSortKeys(SortKey.of(startFieldPath, SortKey.Direction.ASCENDING))
            .fields(fields.build())
            .build();

    SinkReduced<Object, byte[]> sink = encoder.to(Sink.reduceByteArray());
    CompletableFuture<CollectionMetadata> onMetadata = new CompletableFuture<>();
    ResultReduced<byte[]> result;
    try {
      result =
          provider
              .queries()
              .get()
              .getFeatureStream(probe)
              .runWith(sink, Map.of(), onMetadata)
              .toCompletableFuture()
              .join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof WebApplicationException) {
        throw (WebApplicationException) e.getCause();
      }
      LOGGER.debug("Time Map probe for '{}/{}' failed; returning 404", collectionId, featureId, e);
      throw new NotFoundException("The requested feature does not exist.");
    }

    if (!result.hasFeatures()) {
      throw new NotFoundException("The requested feature does not exist.");
    }

    return Response.ok(result.reduced()).type(outputFormat.getMediaType().type()).build();
  }
}
