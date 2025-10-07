/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import static de.ii.ogcapi.features.search.domain.SearchQueriesHandler.GROUP_SEARCH_READ;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.core.domain.EndpointRequiresFeatures;
import de.ii.ogcapi.features.search.domain.ImmutableQueryInputParameter;
import de.ii.ogcapi.features.search.domain.ParameterFormat;
import de.ii.ogcapi.features.search.domain.SearchConfiguration;
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler;
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler.Query;
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler.QueryInputParameter;
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
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import java.util.List;
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
 * @title Stored Query Parameter
 * @path search/{queryId}/parameters/{name}
 * @langEn Get the details of a query parameter
 * @langDe Abrufen der Details eines Abfrageparameters
 * @ref:formats {@link de.ii.ogcapi.features.search.domain.ParameterFormat}
 */
@Singleton
@AutoBind
public class EndpointParameter extends EndpointRequiresFeatures implements ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointParameter.class);

  private static final List<String> TAGS = ImmutableList.of("Discover and execute queries");

  private final SearchQueriesHandler queryHandler;

  @Inject
  public EndpointParameter(ExtensionRegistry extensionRegistry, SearchQueriesHandler queryHandler) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return SearchConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null) {
      formats = extensionRegistry.getExtensionsForType(ParameterFormat.class);
    }
    return formats;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("search")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_SEARCH_PARAMETER);

    String path = "/search/{queryId}/parameters/{name}";
    List<OgcApiQueryParameter> params = getQueryParameters(extensionRegistry, apiData, path);
    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);
    if (pathParameters.stream().noneMatch(param -> "queryId".equals(param.getName()))) {
      LOGGER.error(
          "Path parameter 'queryId' missing for resource at path '"
              + path
              + "'. The GET method will not be available.");
    } else if (pathParameters.stream().noneMatch(param -> "name".equals(param.getName()))) {
      LOGGER.error(
          "Path parameter 'name' missing for resource at path '"
              + path
              + "'. The GET method will not be available.");
    } else {
      String operationSummary = "fetch parameter with name {name} of stored query {queryId}";
      ImmutableOgcApiResourceAuxiliary.Builder resourceBuilder =
          new ImmutableOgcApiResourceAuxiliary.Builder().path(path).pathParameters(pathParameters);
      ApiOperation.getResource(
              apiData,
              path,
              false,
              params,
              ImmutableList.of(),
              getResponseContent(apiData),
              operationSummary,
              Optional.empty(),
              Optional.empty(),
              getOperationId("getStoredQueryParameter"),
              GROUP_SEARCH_READ,
              TAGS,
              SearchBuildingBlock.MATURITY,
              SearchBuildingBlock.SPEC)
          .ifPresent(operation -> resourceBuilder.putOperations("GET", operation));
      definitionBuilder.putResources(path, resourceBuilder.build());
    }

    return definitionBuilder.build();
  }

  /**
   * Fetch a parameter of a stored query
   *
   * @param queryId the local identifier of the query
   * @param name the name of the parameter
   * @return the query result
   */
  @Path("/{queryId}/parameters/{name}")
  @GET
  public Response getParameter(
      @PathParam("queryId") String queryId,
      @PathParam("name") String name,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext) {

    OgcApiDataV2 apiData = api.getData();
    ensureSupportForFeatures(apiData);
    checkPathParameter(
        extensionRegistry, apiData, "/search/{queryId}/parameters/{name}", "queryId", queryId);
    checkPathParameter(
        extensionRegistry, apiData, "/search/{queryId}/parameters/{name}", "name", name);

    QueryInputParameter queryInput =
        new ImmutableQueryInputParameter.Builder()
            .from(getGenericQueryInput(api.getData()))
            .queryId(queryId)
            .parameterName(name)
            .build();

    return queryHandler.handle(Query.PARAMETER, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler);
  }
}
