/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.core.domain.DelayedOutputStream;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.ImmutableFeatureTransformationContextGeneric;
import de.ii.ogcapi.features.search.domain.FilterOperator;
import de.ii.ogcapi.features.search.domain.ImmutableParameter;
import de.ii.ogcapi.features.search.domain.ImmutableParameters;
import de.ii.ogcapi.features.search.domain.ImmutableStoredQueries;
import de.ii.ogcapi.features.search.domain.ImmutableStoredQuery;
import de.ii.ogcapi.features.search.domain.Parameter;
import de.ii.ogcapi.features.search.domain.ParameterFormat;
import de.ii.ogcapi.features.search.domain.Parameters;
import de.ii.ogcapi.features.search.domain.ParametersFormat;
import de.ii.ogcapi.features.search.domain.QueryExpression;
import de.ii.ogcapi.features.search.domain.SearchConfiguration;
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler;
import de.ii.ogcapi.features.search.domain.StoredQueries;
import de.ii.ogcapi.features.search.domain.StoredQueriesFormat;
import de.ii.ogcapi.features.search.domain.StoredQueryExpression;
import de.ii.ogcapi.features.search.domain.StoredQueryFormat;
import de.ii.ogcapi.features.search.domain.StoredQueryRepository;
import de.ii.ogcapi.features.search.domain.StoredQueryValidator;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.HeaderCaching;
import de.ii.ogcapi.foundation.domain.HeaderContentDisposition;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.foundation.domain.ProfileExtension.ResourceType;
import de.ii.ogcapi.foundation.domain.ProfileSet;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.sorting.domain.SortingConfiguration;
import de.ii.xtraplatform.base.domain.ETag;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.cql.domain.And;
import de.ii.xtraplatform.cql.domain.Cql;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.cql.domain.Or;
import de.ii.xtraplatform.crs.domain.CrsInfo;
import de.ii.xtraplatform.crs.domain.CrsTransformer;
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.crs.domain.ImmutableEpsgCrs;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.features.domain.CollectionMetadata;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.FeatureStream;
import de.ii.xtraplatform.features.domain.FeatureStream.Result;
import de.ii.xtraplatform.features.domain.FeatureStream.ResultBase;
import de.ii.xtraplatform.features.domain.FeatureTokenEncoder;
import de.ii.xtraplatform.features.domain.ImmutableMultiFeatureQuery;
import de.ii.xtraplatform.features.domain.ImmutableSubQuery;
import de.ii.xtraplatform.features.domain.MultiFeatureQuery;
import de.ii.xtraplatform.features.domain.MultiFeatureQuery.SubQuery;
import de.ii.xtraplatform.features.domain.SortKey;
import de.ii.xtraplatform.features.domain.SortKey.Direction;
import de.ii.xtraplatform.features.domain.Tuple;
import de.ii.xtraplatform.features.domain.TypeQuery;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformations;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaAny;
import de.ii.xtraplatform.streams.domain.OutputStreamToByteConsumer;
import de.ii.xtraplatform.streams.domain.Reactive.Sink;
import de.ii.xtraplatform.streams.domain.Reactive.SinkTransformed;
import de.ii.xtraplatform.values.domain.ValueStore;
import de.ii.xtraplatform.values.domain.Values;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

@Singleton
@AutoBind
@SuppressWarnings({"PMD.GodClass", "PMD.CouplingBetweenObjects"})
public class SearchQueriesHandlerImpl extends AbstractVolatileComposed
    implements SearchQueriesHandler {

  public static final String MEDIA_TYPE_NOT_SUPPORTED =
      "The requested media type ''{0}'' is not supported, the following media types are available: {1}";

  private final I18n i18n;
  private final CrsTransformerFactory crsTransformerFactory;
  private final Map<Query, QueryHandler<? extends QueryInput>> queryHandlers;
  private final ExtensionRegistry extensionRegistry;
  private final Values<Codelist> codelistStore;
  private final CrsInfo crsInfo;
  private final Cql cql;
  private final StoredQueryRepository repository;
  private final StoredQueriesLinkGenerator linkGenerator;
  private final SchemaValidator schemaValidator;

  @Inject
  public SearchQueriesHandlerImpl(
      I18n i18n,
      CrsTransformerFactory crsTransformerFactory,
      ExtensionRegistry extensionRegistry,
      ValueStore valueStore,
      CrsInfo crsInfo,
      Cql cql,
      StoredQueryRepository repository,
      SchemaValidator schemaValidator,
      VolatileRegistry volatileRegistry) {
    super(SearchQueriesHandler.class.getSimpleName(), volatileRegistry, true);
    this.i18n = i18n;
    this.crsTransformerFactory = crsTransformerFactory;
    this.extensionRegistry = extensionRegistry;
    this.codelistStore = valueStore.forType(Codelist.class);
    this.crsInfo = crsInfo;
    this.cql = cql;
    this.repository = repository;
    this.schemaValidator = schemaValidator;
    this.linkGenerator = new StoredQueriesLinkGenerator();

    this.queryHandlers =
        ImmutableMap.of(
            Query.STORED_QUERIES,
                QueryHandler.with(QueryInputStoredQueries.class, this::getStoredQueries),
            Query.QUERY, QueryHandler.with(QueryInputQuery.class, this::executeQuery),
            Query.DEFINITION,
                QueryHandler.with(QueryInputQueryDefinition.class, this::getQueryDefinition),
            Query.PARAMETERS, QueryHandler.with(QueryInputParameters.class, this::getParameters),
            Query.PARAMETER, QueryHandler.with(QueryInputParameter.class, this::getParameter),
            Query.CREATE_REPLACE,
                QueryHandler.with(QueryInputStoredQueryCreateReplace.class, this::writeStoredQuery),
            Query.DELETE,
                QueryHandler.with(QueryInputStoredQueryDelete.class, this::deleteStoredQuery));

    onVolatileStart();

    addSubcomponent(crsTransformerFactory);
    addSubcomponent(codelistStore);
    addSubcomponent(crsInfo);
    addSubcomponent(repository);

    onVolatileStarted();
  }

  @Override
  public Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers() {
    return queryHandlers;
  }

  private static void ensureCollectionIdExists(OgcApiDataV2 apiData, String collectionId) {
    if (!apiData.isCollectionEnabled(collectionId)) {
      throw new BadRequestException(
          MessageFormat.format("The collection ''{0}'' does not exist in this API.", collectionId));
    }
  }

  private static void ensureFeatureProviderSupportsQueries(FeatureProvider featureProvider) {
    if (!featureProvider.multiQueries().isSupported()) {
      throw new IllegalStateException("Feature provider does not support queries.");
    }
  }

  private static void checkHeader(
      Optional<SearchConfiguration> searchConfiguration,
      Optional<String> ifMatch,
      Optional<String> ifUnmodifiedSince) {
    if (searchConfiguration.map(SearchConfiguration::supportsEtag).orElse(false)
        && ifMatch.isEmpty()) {
      throw new BadRequestException(
          "Requests to change a stored query must include an 'If-Match' header.");
    } else if (searchConfiguration.map(SearchConfiguration::supportsLastModified).orElse(false)
        && ifUnmodifiedSince.isEmpty()) {
      throw new BadRequestException(
          "Requests to change a stored query must include an 'If-Unmodified-Since' header.");
    }
  }

  @SuppressWarnings("PMD.ConfusingTernary")
  private Response writeStoredQuery(
      QueryInputStoredQueryCreateReplace queryInput, ApiRequestContext requestContext) {

    OgcApiDataV2 apiData = requestContext.getApi().getData();

    String queryId = queryInput.getQueryId();

    final Response.ResponseBuilder response =
        preConditionChecks(
            requestContext,
            apiData,
            queryId,
            queryInput.getIfMatch(),
            queryInput.getIfUnmodifiedSince());

    if (Objects.nonNull(response)) {
      return response.build();
    }

    StoredQueryExpression query = queryInput.getQuery();

    List<String> errors =
        query.accept(new StoredQueryValidator(query.getParameters(), Optional.of(schemaValidator)));
    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "The stored query '%s' is invalid: %s", queryId, String.join("; ", errors)));
    }

    if (queryInput.getStrict()) {
      // check collections
      query
          .getQueries()
          .forEach(
              q ->
                  q.getCollections().stream()
                      .filter(c -> c.getValue().isPresent())
                      .map(c -> c.getValue().get())
                      .forEach(
                          collectionId ->
                              ensureCollectionIdExists(
                                  requestContext.getApi().getData(), collectionId)));
      query.getCollections().stream()
          .filter(c -> c.getValue().isPresent())
          .map(c -> c.getValue().get())
          .forEach(
              collectionId ->
                  ensureCollectionIdExists(requestContext.getApi().getData(), collectionId));
    }

    if (!queryInput.getDryRun()) {
      try {
        repository.writeStoredQueryDocument(apiData, queryId, query);
      } catch (IOException e) {
        throw new IllegalStateException(
            MessageFormat.format("Error while storing query ''{0}''.", queryId), e);
      }
    }

    return Response.noContent().build();
  }

  private Response deleteStoredQuery(
      QueryInputStoredQueryDelete queryInput, ApiRequestContext requestContext) {

    OgcApiDataV2 apiData = requestContext.getApi().getData();

    String queryId = queryInput.getQueryId();

    final Response.ResponseBuilder response =
        preConditionChecks(
            requestContext,
            apiData,
            queryId,
            queryInput.getIfMatch(),
            queryInput.getIfUnmodifiedSince());

    if (Objects.nonNull(response)) {
      return response.build();
    }

    try {
      repository.deleteStoredQuery(apiData, queryId);
    } catch (IOException e) {
      throw new IllegalStateException(
          MessageFormat.format("Error while deleting stored query ''{0}''.", queryId), e);
    }
    return Response.noContent().build();
  }

  private Response.ResponseBuilder preConditionChecks(
      ApiRequestContext requestContext,
      OgcApiDataV2 apiData,
      String queryId,
      Optional<String> ifMatch,
      Optional<String> ifUnmodifiedSince) {
    Date lastModified = null;
    EntityTag etag = null;
    try {
      StoredQueryExpression currentQuery = repository.get(apiData, queryId);

      checkHeader(apiData.getExtension(SearchConfiguration.class), ifMatch, ifUnmodifiedSince);
      lastModified = repository.getLastModified(apiData, queryId);
      etag = ETag.from(currentQuery.getStableHash().getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      // ignore, no current query
    }

    return evaluatePreconditions(requestContext, lastModified, etag);
  }

  private Response getParameters(
      QueryInputParameters queryInput, ApiRequestContext requestContext) {
    ImmutableParameters.Builder builder = new ImmutableParameters.Builder();

    OgcApiDataV2 apiData = requestContext.getApi().getData();

    String queryId = queryInput.getQueryId();
    StoredQueryExpression query = repository.get(apiData, queryId);

    Map<String, JsonSchema> params = query.getAllParameters();
    params.forEach(builder::putProperties);
    params.entrySet().stream()
        .filter(entry -> entry.getValue().getDefault_().isEmpty())
        .map(Entry::getKey)
        .forEach(builder::addRequired);

    builder.lastModified(Optional.ofNullable(repository.getLastModified(apiData, queryId)));

    Parameters parameters = builder.build();

    ParametersFormat format =
        extensionRegistry.getExtensionsForType(ParametersFormat.class).stream()
            .filter(f -> requestContext.getMediaType().matches(f.getMediaType().type()))
            .findAny()
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            MEDIA_TYPE_NOT_SUPPORTED,
                            requestContext.getMediaType(),
                            extensionRegistry.getExtensionsForType(ParametersFormat.class).stream()
                                .map(f -> f.getMediaType().type().toString())
                                .collect(Collectors.joining(", ")))));

    Date lastModified = getLastModified(queryInput, parameters);
    EntityTag etag =
        sendEtag(format.getMediaType(), apiData)
            ? ETag.from(parameters, Parameters.FUNNEL, format.getMediaType().label())
            : null;
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) {
      return response.build();
    }

    return prepareSuccessResponse(
            requestContext,
            queryInput.getIncludeLinkHeader()
                ? linkGenerator.generateLinks(requestContext, i18n)
                : ImmutableList.of(),
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format("parameters.%s", format.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(format.getEntity(parameters, apiData, requestContext))
        .build();
  }

  private Response getParameter(QueryInputParameter queryInput, ApiRequestContext requestContext) {
    ImmutableParameter.Builder builder = new ImmutableParameter.Builder();

    OgcApiDataV2 apiData = requestContext.getApi().getData();

    String queryId = queryInput.getQueryId();
    StoredQueryExpression query = repository.get(apiData, queryId);

    builder.schema(
        Objects.requireNonNullElse(
            query.getAllParameters().get(queryInput.getParameterName()), JsonSchemaAny.of()));

    builder.links(linkGenerator.generateLinks(requestContext, i18n));

    builder.lastModified(Optional.ofNullable(repository.getLastModified(apiData, queryId)));

    Parameter parameter = builder.build();

    ParameterFormat format =
        extensionRegistry.getExtensionsForType(ParameterFormat.class).stream()
            .filter(f -> requestContext.getMediaType().matches(f.getMediaType().type()))
            .findAny()
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            MEDIA_TYPE_NOT_SUPPORTED,
                            requestContext.getMediaType(),
                            extensionRegistry.getExtensionsForType(ParametersFormat.class).stream()
                                .map(f -> f.getMediaType().type().toString())
                                .collect(Collectors.joining(", ")))));

    Date lastModified = getLastModified(queryInput, parameter);
    EntityTag etag =
        sendEtag(format.getMediaType(), apiData)
            ? ETag.from(parameter, Parameter.FUNNEL, format.getMediaType().label())
            : null;
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) {
      return response.build();
    }

    return prepareSuccessResponse(
            requestContext,
            queryInput.getIncludeLinkHeader() ? parameter.getLinks() : ImmutableList.of(),
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format("parameters.%s", format.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(format.getEntity(parameter, apiData, requestContext))
        .build();
  }

  private Response getStoredQueries(
      QueryInputStoredQueries queryInput, ApiRequestContext requestContext) {
    ImmutableStoredQueries.Builder builder = new ImmutableStoredQueries.Builder();

    OgcApiDataV2 apiData = requestContext.getApi().getData();
    repository
        .getAll(apiData)
        .forEach(
            q -> {
              String queryId = q.getId();
              builder.addQueries(
                  new ImmutableStoredQuery.Builder()
                      .id(queryId)
                      .title(q.getTitle())
                      .description(q.getDescription())
                      .links(
                          linkGenerator.generateStoredQueryLinks(
                              requestContext.getUriCustomizer(),
                              q.getTitle().orElse(queryId),
                              queryId,
                              q.getParameterNames(),
                              apiData
                                  .getExtension(SearchConfiguration.class)
                                  .map(SearchConfiguration::isManagerEnabled)
                                  .orElse(false),
                              i18n,
                              requestContext.getLanguage()))
                      .parameters(
                          q.getParameters().entrySet().stream()
                              .collect(
                                  Collectors.toUnmodifiableMap(Entry::getKey, Entry::getValue)))
                      .formats(
                          extensionRegistry
                              .getExtensionsForType(FeatureFormatExtension.class)
                              .stream()
                              .filter(
                                  outputFormatExtension ->
                                      outputFormatExtension.isEnabledForApi(apiData))
                              .filter(
                                  f ->
                                      Objects.nonNull(
                                          f.getFeatureContent(apiData, Optional.empty(), true)))
                              .map(
                                  f ->
                                      new AbstractMap.SimpleImmutableEntry<>(
                                          f.getMediaType().label(), f.getMediaType().parameter()))
                              .sorted(Map.Entry.comparingByKey())
                              .collect(Collectors.toUnmodifiableList()))
                      .build());
            });

    builder.links(linkGenerator.generateLinks(requestContext, i18n));

    builder.lastModified(Optional.ofNullable(repository.getLastModified(apiData)));

    StoredQueries storedQueries = builder.build();

    StoredQueriesFormat format =
        repository
            .getStoredQueriesFormatStream(apiData)
            .filter(f -> requestContext.getMediaType().matches(f.getMediaType().type()))
            .findAny()
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            MEDIA_TYPE_NOT_SUPPORTED,
                            requestContext.getMediaType(),
                            repository
                                .getStoredQueriesFormatStream(apiData)
                                .map(f -> f.getMediaType().type().toString())
                                .collect(Collectors.joining(", ")))));

    Date lastModified = getLastModified(queryInput, storedQueries);
    EntityTag etag =
        sendEtag(format.getMediaType(), apiData)
            ? ETag.from(storedQueries, StoredQueries.FUNNEL, format.getMediaType().label())
            : null;
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) {
      return response.build();
    }

    return prepareSuccessResponse(
            requestContext,
            queryInput.getIncludeLinkHeader() ? storedQueries.getLinks() : ImmutableList.of(),
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format("storedQueries.%s", format.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(format.getEntity(storedQueries, apiData, requestContext))
        .build();
  }

  private boolean sendEtag(ApiMediaType format, OgcApiDataV2 apiData) {
    return !format.type().equals(MediaType.TEXT_HTML_TYPE)
        || apiData
            .getExtension(HtmlConfiguration.class)
            .map(HtmlConfiguration::getSendEtags)
            .orElse(false);
  }

  private Response getQueryDefinition(
      QueryInputQueryDefinition queryInput, ApiRequestContext requestContext) {
    OgcApiDataV2 apiData = requestContext.getApi().getData();
    StoredQueryFormat format =
        repository
            .getStoredQueryFormatStream(apiData)
            .filter(f -> requestContext.getMediaType().matches(f.getMediaType().type()))
            .findAny()
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            MEDIA_TYPE_NOT_SUPPORTED,
                            requestContext.getMediaType(),
                            repository
                                .getStoredQueryFormatStream(apiData)
                                .map(f -> f.getMediaType().type().toString())
                                .collect(Collectors.joining(", ")))));

    String queryId = queryInput.getQueryId();
    StoredQueryExpression query = repository.get(apiData, queryId);

    Date lastModified = repository.getLastModified(apiData, queryId);
    EntityTag etag = ETag.from(query.getStableHash().getBytes(StandardCharsets.UTF_8));
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);

    if (Objects.nonNull(response)) {
      return response.build();
    }

    return prepareSuccessResponse(
            requestContext,
            queryInput.getIncludeLinkHeader()
                ? linkGenerator.generateLinks(requestContext, i18n)
                : ImmutableList.of(),
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format(
                    "%s.%s", queryInput.getQueryId(), format.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(format.getEntity(query, apiData, requestContext))
        .build();
  }

  private Response executeQuery(QueryInputQuery queryInput, ApiRequestContext requestContext) {

    FeatureProvider featureProvider = queryInput.getFeatureProvider();
    ensureFeatureProviderSupportsQueries(featureProvider);

    EntityTag etag = null;
    Date lastModified = getLastModified(queryInput);

    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) {
      return response.build();
    }

    QueryExpression queryExpression = queryInput.getQuery();
    EpsgCrs crs =
        queryExpression
            .getCrs()
            .map(EpsgCrs::fromString)
            .map(
                hCrs ->
                    queryExpression
                        .getVerticalCrs()
                        .map(
                            vCrsUri ->
                                (EpsgCrs)
                                    new ImmutableEpsgCrs.Builder()
                                        .from(hCrs)
                                        .verticalCode(EpsgCrs.fromString(vCrsUri).getCode())
                                        .build())
                        .orElse(hCrs))
            .orElse(queryInput.getDefaultCrs());

    MultiFeatureQuery query = getMultiFeatureQuery(requestContext.getApi(), queryExpression, crs);

    List<String> collectionIds =
        queryExpression.getCollections().size() == 1
            ? ImmutableList.of(queryExpression.getCollections().get(0))
            : queryExpression.getQueries().stream().map(q -> q.getCollections().get(0)).toList();
    EpsgCrs targetCrs = query.getCrs().orElse(queryInput.getDefaultCrs());
    List<Link> links =
        queryInput.isStoredQuery()
            ? new StoredQueriesLinkGenerator()
                .generateFeaturesLinks(
                    requestContext.getUriCustomizer(),
                    query.getOffset(),
                    query.getLimit(),
                    requestContext.getMediaType(),
                    requestContext.getAlternateMediaTypes(),
                    i18n,
                    requestContext.getLanguage())
            : ImmutableList.of();
    FeatureFormatExtension outputFormat =
        requestContext
            .getApi()
            .getOutputFormat(
                FeatureFormatExtension.class, requestContext.getMediaType(), Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    Tuple<StreamingOutput, CollectionMetadata> streamingOutputAndMetadata =
        getStreamingOutput(
            requestContext,
            queryExpression,
            query,
            queryInput.getAllLinksAreLocal(),
            collectionIds,
            featureProvider,
            outputFormat,
            queryInput.getDefaultCrs(),
            targetCrs,
            links);

    StreamingOutput streamingOutput = streamingOutputAndMetadata.first();
    CollectionMetadata collectionMetadata = streamingOutputAndMetadata.second();
    boolean hasNextPage =
        collectionMetadata != null
            && collectionMetadata.getNumberReturned().orElse(0) == query.getLimit();

    List<Link> filteredLinks =
        queryInput.getIncludeLinkHeader()
            ? hasNextPage
                ? links
                : links.stream()
                    .filter(link -> !"next".equalsIgnoreCase(link.getRel()))
                    .collect(ImmutableList.toImmutableList())
            : null;

    return prepareSuccessResponse(
            requestContext,
            filteredLinks,
            HeaderCaching.of(lastModified, etag, queryInput),
            targetCrs,
            HeaderContentDisposition.of(
                String.format(
                    "%s.%s", queryExpression.getId(), outputFormat.getMediaType().fileExtension())),
            collectionMetadata,
            i18n.getLanguages())
        .entity(streamingOutput)
        .build();
  }

  private MultiFeatureQuery getMultiFeatureQuery(
      OgcApi api, QueryExpression queryExpression, EpsgCrs crs) {
    Optional<Cql2Expression> topLevelFilter = queryExpression.getFilter();
    List<SubQuery> queries =
        queryExpression.getCollections().size() == 1
            ? ImmutableList.of(
                getSubQuery(
                    api.getData(),
                    queryExpression.getCollections().get(0),
                    topLevelFilter,
                    Optional.empty(),
                    Optional.empty(),
                    queryExpression.getSortby(),
                    queryExpression.getProperties(),
                    ImmutableList.of()))
            : queryExpression.getQueries().stream()
                .map(
                    q ->
                        getSubQuery(
                            api.getData(),
                            q.getCollections().get(0),
                            q.getFilter(),
                            topLevelFilter,
                            queryExpression.getFilterOperator(),
                            q.getSortby(),
                            q.getProperties(),
                            queryExpression.getProperties()))
                .toList();

    ImmutableMultiFeatureQuery.Builder finalQueryBuilder =
        ImmutableMultiFeatureQuery.builder()
            .queries(queries)
            .maxAllowableOffset(queryExpression.getMaxAllowableOffset().orElse(0.0))
            .crs(crs)
            .limit(
                queryExpression
                    .getLimit()
                    .orElse(
                        api.getData()
                            .getExtension(FeaturesCoreConfiguration.class)
                            .map(FeaturesCoreConfiguration::getDefaultPageSize)
                            .orElseThrow()))
            .offset(queryExpression.getOffset().orElse(0));

    api.getData()
        .getExtension(FeaturesCoreConfiguration.class)
        .map(FeaturesCoreConfiguration::getCoordinatePrecision)
        .ifPresent(
            coordinatePrecision -> {
              List<Integer> precisionList = crsInfo.getPrecisionList(crs, coordinatePrecision);
              if (!precisionList.isEmpty()) {
                finalQueryBuilder.geometryPrecision(precisionList);
              }
              precisionList = crsInfo.getPrecisionList(OgcCrs.CRS84h, coordinatePrecision);
              if (!precisionList.isEmpty()) {
                finalQueryBuilder.wgs84GeometryPrecision(precisionList);
              }
            });

    MultiFeatureQuery query = finalQueryBuilder.build();
    return query;
  }

  private SubQuery getSubQuery(
      OgcApiDataV2 apiData,
      String collectionId,
      Optional<Cql2Expression> filter,
      Optional<Cql2Expression> globalFilter,
      Optional<FilterOperator> filterOperator,
      List<String> sortby,
      List<String> properties,
      List<String> globalProperties) {
    {
      ensureCollectionIdExists(apiData, collectionId);

      return ImmutableSubQuery.builder()
          .type(
              apiData
                  .getExtension(FeaturesCoreConfiguration.class, collectionId)
                  .flatMap(FeaturesCoreConfiguration::getFeatureType)
                  .orElse(collectionId))
          .sortKeys(
              (!sortby.isEmpty()
                      ? sortby
                      : apiData
                          .getExtension(SortingConfiguration.class, collectionId)
                          .map(SortingConfiguration::getDefaultSortby)
                          .orElse(List.of()))
                  .stream()
                      .map(
                          s ->
                              s.startsWith("-")
                                  ? SortKey.of(s.substring(1), Direction.DESCENDING)
                                  : SortKey.of(s.replace("+", "")))
                      .toList())
          .filters(
              getEffectiveCql2Expression(filter, globalFilter, filterOperator).stream().toList())
          .fields(
              globalProperties.isEmpty() && properties.isEmpty()
                  ? ImmutableList.of("*")
                  : globalProperties.isEmpty()
                      ? properties
                      : properties.isEmpty()
                          ? globalProperties
                          : Stream.concat(globalProperties.stream(), properties.stream()).toList())
          .build();
    }
  }

  private Optional<Cql2Expression> getEffectiveCql2Expression(
      Optional<Cql2Expression> filter,
      Optional<Cql2Expression> globalFilter,
      Optional<FilterOperator> filterOperator) {
    Optional<Cql2Expression> cqlFilter = Optional.empty();
    if (filter.isPresent() && globalFilter.isPresent()) {
      cqlFilter =
          filter.map(
              f ->
                  FilterOperator.OR.equals(filterOperator.orElse(FilterOperator.AND))
                      ? Or.of(f, globalFilter.get())
                      : And.of(f, globalFilter.get()));
    } else if (filter.isPresent()) {
      cqlFilter = filter;
    } else if (globalFilter.isPresent()) {
      cqlFilter = globalFilter;
    }
    return cqlFilter;
  }

  private Tuple<StreamingOutput, CollectionMetadata> getStreamingOutput(
      ApiRequestContext requestContext,
      QueryExpression queryExpression,
      MultiFeatureQuery query,
      boolean allLinksAreLocal,
      List<String> collectionIds,
      FeatureProvider featureProvider,
      FeatureFormatExtension outputFormat,
      EpsgCrs defaultCrs,
      EpsgCrs targetCrs,
      List<Link> links) {
    OgcApi api = requestContext.getApi();
    EpsgCrs sourceCrs = null;
    Optional<CrsTransformer> crsTransformer = Optional.empty();
    if (featureProvider.crs().isAvailable()) {
      sourceCrs = featureProvider.crs().get().getNativeCrs();
      crsTransformer = crsTransformerFactory.getTransformer(sourceCrs, targetCrs);
    }

    // negotiate profiles
    List<Profile> requestedProfiles =
        extensionRegistry.getExtensionsForType(Profile.class).stream()
            .filter(profile -> queryExpression.getProfiles().contains(profile.getId()))
            .toList();
    FeaturesCoreConfiguration coreConfiguration =
        requestContext
            .getApi()
            .getData()
            .getExtension(FeaturesCoreConfiguration.class)
            .filter(ExtensionConfiguration::isEnabled)
            .filter(
                cfg ->
                    cfg.getItemType().orElse(FeaturesCoreConfiguration.ItemType.feature)
                        != FeaturesCoreConfiguration.ItemType.unknown)
            .orElseThrow(() -> new NotFoundException("Features are not supported for this API."));
    List<Profile> defaultProfilesFeaturesCore =
        extensionRegistry.getExtensionsForType(Profile.class).stream()
            .filter(
                profile ->
                    coreConfiguration.getDefaultProfiles().containsKey(profile.getProfileSet())
                        && profile
                            .getId()
                            .equals(
                                coreConfiguration
                                    .getDefaultProfiles()
                                    .get(profile.getProfileSet())))
            .toList();
    List<Profile> profiles =
        collectionIds.stream()
            .flatMap(
                collectionId ->
                    extensionRegistry.getExtensionsForType(ProfileSet.class).stream()
                        .filter(
                            p -> p.isEnabledForApi(requestContext.getApi().getData(), collectionId))
                        .map(
                            profileSet ->
                                profileSet
                                    .negotiateProfile(
                                        requestedProfiles,
                                        defaultProfilesFeaturesCore,
                                        outputFormat,
                                        ResourceType.FEATURE,
                                        api.getData(),
                                        Optional.of(collectionId))
                                    .orElse(null))
                        .filter(Objects::nonNull))
            .distinct()
            .toList();

    Map<String, Optional<FeatureSchema>> schemas =
        query.getQueries().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    TypeQuery::getType, q -> featureProvider.info().getSchema(q.getType())));

    Map<String, List<String>> fields =
        query.getQueries().stream()
            .collect(Collectors.toUnmodifiableMap(TypeQuery::getType, TypeQuery::getFields));

    ImmutableFeatureTransformationContextGeneric.Builder transformationContext =
        new ImmutableFeatureTransformationContextGeneric.Builder()
            .api(api)
            .apiData(api.getData())
            .featureSchemas(schemas)
            .ogcApiRequest(requestContext)
            .crsTransformer(crsTransformer)
            .profiles(profiles)
            .codelists(codelistStore.asMap())
            .defaultCrs(defaultCrs)
            .sourceCrs(Optional.ofNullable(sourceCrs))
            .links(links)
            .isFeatureCollection(true)
            .limit(query.getLimit())
            .offset(query.getOffset())
            .maxAllowableOffset(query.getMaxAllowableOffset())
            .geometryPrecision(query.getGeometryPrecision())
            .wgs84GeometryPrecision(query.getWgs84GeometryPrecision())
            .fields(fields)
            .allLinksAreLocal(allLinksAreLocal)
            .idsIncludeCollectionId(collectionIds.size() > 1)
            .queryId(queryExpression.getId())
            .queryTitle(queryExpression.getTitle())
            .queryDescription(queryExpression.getDescription());

    FeatureStream featureStream;
    FeatureTokenEncoder<?> encoder;
    Map<String, PropertyTransformations> propertyTransformations;

    if (outputFormat.canEncodeFeatures()) {
      featureStream = featureProvider.multiQueries().get().getFeatureStream(query);

      ImmutableFeatureTransformationContextGeneric transformationContextGeneric =
          transformationContext.outputStream(new OutputStreamToByteConsumer()).build();
      encoder =
          outputFormat
              .getFeatureEncoder(transformationContextGeneric, requestContext.getLanguage())
              .orElseThrow();

      propertyTransformations =
          getIdTransformations(
              query,
              api.getData(),
              collectionIds,
              featureProvider,
              outputFormat,
              profiles,
              transformationContextGeneric.getServiceUrl());
    } else {
      throw new NotAcceptableException(
          MessageFormat.format(
              "The requested media type {0} cannot be generated, because it does not support streaming.",
              requestContext.getMediaType().type()));
    }

    DelayedOutputStream delayedOutputStream = new DelayedOutputStream();
    SinkTransformed<Object, byte[]> featureSink =
        encoder.to(Sink.outputStream(delayedOutputStream));
    CompletableFuture<CollectionMetadata> onCollectionMetadata = new CompletableFuture<>();

    // start stream asynchronously
    CompletableFuture<Result> stream =
        featureStream
            .runWith(featureSink, propertyTransformations, onCollectionMetadata)
            .toCompletableFuture();

    // wait for collection metadata
    CollectionMetadata collectionMetadata = onCollectionMetadata.join();

    StreamingOutput streamingOutput =
        outputStream -> {
          delayedOutputStream.setOutputStream(outputStream);
          // wait for stream to finish
          run(stream::join);
        };

    return Tuple.of(streamingOutput, collectionMetadata);
  }

  private Map<String, PropertyTransformations> getIdTransformations(
      MultiFeatureQuery query,
      OgcApiDataV2 apiData,
      List<String> collectionIds,
      FeatureProvider featureProvider,
      FeatureFormatExtension outputFormat,
      List<Profile> profiles,
      String serviceUrl) {
    return IntStream.range(0, collectionIds.size())
        .boxed()
        .collect(
            Collectors.toUnmodifiableMap(
                n -> getFeatureTypeId(query, n),
                n -> {
                  String collectionId = collectionIds.get(n);
                  Optional<FeatureSchema> schema =
                      featureProvider.info().getSchema(getFeatureTypeId(query, n));
                  PropertyTransformations pt =
                      outputFormat
                          .getPropertyTransformations(
                              apiData,
                              Objects.requireNonNull(apiData.getCollections().get(collectionId)),
                              schema,
                              profiles)
                          .orElseThrow();
                  if (collectionIds.size() > 1) {
                    pt =
                        new IdTransform(featureProvider, getFeatureTypeId(query, n), collectionId)
                            .mergeInto(pt);
                  }
                  return pt.withSubstitutions(
                      FeaturesCoreProviders.DEFAULT_SUBSTITUTIONS.apply(serviceUrl));
                }));
  }

  private String getFeatureTypeId(MultiFeatureQuery query, int queryIndex) {
    return query.getQueries().get(queryIndex).getType();
  }

  @SuppressWarnings("PMD.PreserveStackTrace")
  private <U extends ResultBase> void run(Supplier<U> stream) {
    try {
      U result = stream.get();

      result.getError().ifPresent(FeatureStream::processStreamError);

    } catch (CompletionException e) {
      if (e.getCause() instanceof WebApplicationException) {
        throw (WebApplicationException) e.getCause();
      }
      throw new IllegalStateException("Feature stream error.", e.getCause());
    }
  }
}
