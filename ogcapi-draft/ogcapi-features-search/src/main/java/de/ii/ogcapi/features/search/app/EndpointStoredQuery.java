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
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Stored Query
 * @path search/{queryId}
 * @langEn Execute the stored query. Parameters are submitted as query parameters.
 * @langDe Führt die gespeicherte Abfrage aus. Parameter werden als Abfrageparameter übergeben.
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

  @Inject
  public EndpointStoredQuery(
      ExtensionRegistry extensionRegistry,
      FeaturesCoreProviders providers,
      StoredQueryRepository repository,
      SearchQueriesHandler queryHandler,
      SchemaValidator schemaValidator) {
    super(extensionRegistry);
    this.providers = providers;
    this.repository = repository;
    this.queryHandler = queryHandler;
    this.schemaValidator = schemaValidator;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return SearchConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null) {
      formats = extensionRegistry.getExtensionsForType(FeatureFormatExtension.class);
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
        new ParameterResolver(queryParameterSet, schemaValidator).visit(storedQuery);

    FeaturesCoreConfiguration coreConfiguration =
        apiData.getExtension(FeaturesCoreConfiguration.class).orElseThrow();

    QueryInputQuery queryInput =
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
            .isStoredQuery(true)
            .build();

    return queryHandler.handle(Query.QUERY, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler, repository, providers.getFeatureProviderOrThrow(apiData));
  }
}
