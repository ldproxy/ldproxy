/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import static de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler.GROUP_DATA_READ;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.core.domain.EndpointRequiresFeatures;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.search.domain.ImmutableQueryInputQuery;
import de.ii.ogcapi.features.search.domain.ImmutableStoredQueryExpression;
import de.ii.ogcapi.features.search.domain.ParameterResolver;
import de.ii.ogcapi.features.search.domain.QueryExpression;
import de.ii.ogcapi.features.search.domain.QueryExpressionQueryParameter;
import de.ii.ogcapi.features.search.domain.QueryParameterTemplateParameter;
import de.ii.ogcapi.features.search.domain.SearchConfiguration;
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler;
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler.Query;
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler.QueryInputQuery;
import de.ii.ogcapi.features.search.domain.StoredQueryExpression;
import de.ii.ogcapi.features.search.domain.StoredQueryRepository;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiResourceAuxiliary;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.cql.domain.Cql;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Stored Query
 * @path search/{queryId}
 * @langEn Execute the stored query. Parameters are submitted as query parameters (GET). For a
 *     stored query without paging (`supportPaging: false`), the parameters may also be submitted
 *     URL-encoded in the request body (POST with content type `application/x-www-form-urlencoded`),
 *     for example for large geometries. Since the URI of a POST request does not include the
 *     parameters, the response includes no links and HTML is not supported.
 * @langDe Führt die gespeicherte Abfrage aus. Parameter werden als Abfrageparameter übergeben
 *     (GET). Bei einer gespeicherten Abfrage ohne Paging (`supportPaging: false`) können die
 *     Parameter auch URL-kodiert im Request-Body übergeben werden (POST mit dem Content-Type
 *     `application/x-www-form-urlencoded`), zum Beispiel bei großen Geometrien. Da die URI einer
 *     POST-Anfrage die Parameter nicht enthält, enthält die Antwort keine Links und HTML wird nicht
 *     unterstützt.
 * @ref:formats {@link de.ii.ogcapi.features.core.domain.FeatureFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointStoredQuery extends EndpointRequiresFeatures implements ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointStoredQuery.class);

  private static final List<String> TAGS = ImmutableList.of("Discover and execute queries");

  private final FeaturesCoreProviders providers;
  private final StoredQueryRepository repository;
  private final SearchQueriesHandler queryHandler;
  private final SchemaValidator schemaValidator;
  private final Cql cql;

  @Inject
  public EndpointStoredQuery(
      ExtensionRegistry extensionRegistry,
      FeaturesCoreProviders providers,
      StoredQueryRepository repository,
      SearchQueriesHandler queryHandler,
      SchemaValidator schemaValidator,
      Cql cql) {
    super(extensionRegistry);
    this.providers = providers;
    this.repository = repository;
    this.queryHandler = queryHandler;
    this.schemaValidator = schemaValidator;
    this.cql = cql;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return SearchConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null) {
      // Search responses may mix collections, so only formats that can represent a heterogeneous
      // feature collection are offered (excludes fixed-schema formats such as CSV / FlatGeobuf).
      formats =
          extensionRegistry.getExtensionsForType(FeatureFormatExtension.class).stream()
              .filter(FeatureFormatExtension::supportsHeterogeneousFeatureCollections)
              .collect(Collectors.toList());
    }
    return formats;
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    ValidationResult result = super.onStartup(api, apiValidation);

    if (apiValidation == MODE.NONE) {
      return result;
    }

    ImmutableValidationResult.Builder builder =
        ImmutableValidationResult.builder().from(result).mode(apiValidation);

    builder = repository.validate(builder, api.getData());

    return builder.build();
  }

  // TODO temporary fix, Endpoint.getDefinition() for now is no longer final;
  //      update with https://github.com/interactive-instruments/ldproxy/issues/843
  @Override
  public ApiEndpointDefinition getDefinition(OgcApiDataV2 apiData) {
    if (!isEnabledForApi(apiData)) {
      return super.getDefinition(apiData);
    }

    return apiDefinitions.computeIfAbsent(
        // override to trigger update when stored queries have changed
        repository.getAll(apiData).hashCode(),
        ignore -> {
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Generating API definition for {}", this.getClass().getSimpleName());
          }

          ApiEndpointDefinition apiEndpointDefinition = computeDefinition(apiData);

          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                "Finished generating API definition for {}", this.getClass().getSimpleName());
          }

          return apiEndpointDefinition;
        });
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("search")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_SEARCH_STORED_QUERY);

    repository
        .getAll(apiData)
        .forEach(
            query -> {
              String queryId = query.getId();
              String path = "/search/" + queryId;
              String definitionPath = "/search/{queryId}";
              List<OgcApiQueryParameter> queryParameters =
                  getQueryParameters(extensionRegistry, apiData, definitionPath).stream()
                      .filter(
                          param ->
                              !(param instanceof QueryParameterTemplateParameter)
                                  || Objects.equals(
                                      ((QueryParameterTemplateParameter) param).getQueryId(),
                                      queryId))
                      // a query without paging returns all features, an offset would be ignored
                      .filter(
                          param ->
                              !(param instanceof QueryParameterOffsetStoredQuery)
                                  || query.getSupportPaging().orElse(false))
                      .toList();

              String operationSummary = "execute stored query " + query.getTitle().orElse(queryId);
              Optional<String> operationDescription = query.getDescription();
              ImmutableOgcApiResourceAuxiliary.Builder resourceBuilder =
                  new ImmutableOgcApiResourceAuxiliary.Builder().path(path);
              ApiOperation.getResource(
                      apiData,
                      path,
                      false,
                      queryParameters,
                      ImmutableList.of(),
                      getResponseContent(apiData),
                      operationSummary,
                      operationDescription,
                      Optional.empty(),
                      getOperationId("executeStoredQuery", queryId),
                      GROUP_DATA_READ,
                      TAGS,
                      SearchBuildingBlock.MATURITY,
                      SearchBuildingBlock.SPEC)
                  .ifPresent(operation -> resourceBuilder.putOperations("GET", operation));
              // the same parameters in the request body, e.g. for geometries that are too large
              // for a URI; only without paging, since paging links, like all links and HTML,
              // would require a URI that includes the parameters
              if (!query.getSupportPaging().orElse(false)) {
                Map<MediaType, ApiMediaTypeContent> postResponseContent =
                    getResponseContent(apiData).entrySet().stream()
                        .filter(entry -> !entry.getKey().isCompatible(MediaType.TEXT_HTML_TYPE))
                        .collect(
                            ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
                ApiOperation.getResource(
                        apiData,
                        path,
                        true,
                        queryParameters,
                        ImmutableList.of(),
                        postResponseContent,
                        operationSummary,
                        Optional.of(
                            operationDescription.map(d -> d + "\n\n").orElse("")
                                + "The parameters are URL-encoded in the request body. The "
                                + "response includes no links."),
                        Optional.empty(),
                        getOperationId("executeStoredQueryPost", queryId),
                        GROUP_DATA_READ,
                        TAGS,
                        SearchBuildingBlock.MATURITY,
                        SearchBuildingBlock.SPEC)
                    .ifPresent(operation -> resourceBuilder.putOperations("POST", operation));
              }
              definitionBuilder.putResources(path, resourceBuilder.build());
            });

    return definitionBuilder.build();
  }

  /**
   * Execute a query by id
   *
   * @param queryId the local identifier of the query
   * @return the query result
   */
  @Path("/{queryId}")
  @GET
  public Response getStoredQuery(
      @PathParam("queryId") String queryId,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext) {
    return executeStoredQuery(queryId, api, requestContext, false);
  }

  /**
   * Execute a query by id with the parameters URL-encoded in the request body
   *
   * @param queryId the local identifier of the query
   * @return the query result
   */
  @Path("/{queryId}")
  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response postStoredQuery(
      @PathParam("queryId") String queryId,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext) {
    return executeStoredQuery(queryId, api, requestContext, true);
  }

  private Response executeStoredQuery(
      String queryId, OgcApi api, ApiRequestContext requestContext, boolean isPost) {
    OgcApiDataV2 apiData = api.getData();
    ensureSupportForFeatures(apiData);
    checkPathParameter(extensionRegistry, apiData, "/search/{queryId}", "queryId", queryId);

    StoredQueryExpression storedQuery = repository.get(apiData, queryId);

    ImmutableStoredQueryExpression.Builder builder =
        new ImmutableStoredQueryExpression.Builder().from(storedQuery);
    QueryParameterSet queryParameterSet = requestContext.getQueryParameterSet();
    for (OgcApiQueryParameter parameter : queryParameterSet.getDefinitions()) {
      if (parameter instanceof QueryExpressionQueryParameter) {
        ((QueryExpressionQueryParameter) parameter).applyTo(builder, queryParameterSet);
      }
    }
    storedQuery = builder.build();

    QueryExpression executableQuery =
        new ParameterResolver(queryParameterSet, schemaValidator, cql).visit(storedQuery);

    FeaturesCoreConfiguration coreConfiguration =
        apiData.getExtension(FeaturesCoreConfiguration.class).orElseThrow();

    ImmutableQueryInputQuery.Builder queryInputBuilder =
        new ImmutableQueryInputQuery.Builder()
            .from(getGenericQueryInput(apiData))
            .query(executableQuery)
            .featureProvider(providers.getFeatureProviderOrThrow(apiData))
            .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
            .minimumPageSize(Optional.ofNullable(coreConfiguration.getMinimumPageSize()))
            .defaultPageSize(Optional.ofNullable(coreConfiguration.getDefaultPageSize()))
            .maximumPageSize(Optional.ofNullable(coreConfiguration.getMaximumPageSize()))
            .allLinksAreLocal(
                api.getData()
                    .getExtension(SearchConfiguration.class)
                    .map(SearchConfiguration::getAllLinksAreLocal)
                    .orElse(false))
            .isStoredQuery(true);
    if (isPost) {
      // links would be built from the URI, which does not include the parameters
      queryInputBuilder.includeBodyLinks(false).includeLinkHeader(false);
    }
    QueryInputQuery queryInput = queryInputBuilder.build();

    return queryHandler.handle(Query.QUERY, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler, repository, providers.getFeatureProviderOrThrow(apiData));
  }
}
