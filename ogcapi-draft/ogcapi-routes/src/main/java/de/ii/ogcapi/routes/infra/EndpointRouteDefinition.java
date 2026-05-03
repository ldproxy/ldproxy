/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.routes.infra;

import static de.ii.ogcapi.routes.domain.QueryHandlerRoutes.GROUP_ROUTES_READ;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiResourceAuxiliary;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.routes.app.RoutingBuildingBlock;
import de.ii.ogcapi.routes.domain.ImmutableQueryInputRoute;
import de.ii.ogcapi.routes.domain.QueryHandlerRoutes;
import de.ii.ogcapi.routes.domain.RouteDefinitionFormatExtension;
import de.ii.ogcapi.routes.domain.RoutingConfiguration;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import io.dropwizard.auth.Auth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Route Definition
 * @path routes/{routeId}/definition
 * @langEn Fetch the definition of a stored route.
 * @langDe Holt die Definition einer gespeicherten Route.
 * @ref:formats {@link de.ii.ogcapi.routes.domain.RouteDefinitionFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointRouteDefinition extends Endpoint implements ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointRouteDefinition.class);

  protected static final List<String> TAGS = ImmutableList.of("Routing");

  private final QueryHandlerRoutes queryHandler;

  @Inject
  public EndpointRouteDefinition(
      ExtensionRegistry extensionRegistry, QueryHandlerRoutes queryHandler) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return RoutingConfiguration.class;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(RoutingConfiguration.class)
        .filter(RoutingConfiguration::isEnabled)
        .filter(RoutingConfiguration::isManageRoutesEnabled)
        .isPresent();
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats = extensionRegistry.getExtensionsForType(RouteDefinitionFormatExtension.class);
    return formats;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("routes")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_ROUTE_DEFINITION);
    String path = "/routes/{routeId}/definition";
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, path);
    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);
    if (pathParameters.stream().noneMatch(param -> param.getName().equals("routeId"))) {
      LOGGER.error(
          "Path parameter 'routeId' missing for resource at path '"
              + path
              + "'. The GET method will not be available.");
    } else {
      String operationSummary = "fetch the definition of route `routeId`";
      Optional<String> operationDescription =
          Optional.of(
              "This operation returns the parameters used to create the route with id `routeId`.");
      ImmutableOgcApiResourceAuxiliary.Builder resourceBuilder =
          new ImmutableOgcApiResourceAuxiliary.Builder().path(path).pathParameters(pathParameters);
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
              getOperationId("getRouteDefinition"),
              GROUP_ROUTES_READ,
              TAGS,
              RoutingBuildingBlock.MATURITY,
              RoutingBuildingBlock.SPEC)
          .ifPresent(operation -> resourceBuilder.putOperations("GET", operation));
      definitionBuilder.putResources(path, resourceBuilder.build());
    }

    return definitionBuilder.build();
  }

  /**
   * Fetch the definition of a route
   *
   * @param routeId the local identifier of route
   * @return the style in a json file
   */
  @Path("/{routeId}/definition")
  @GET
  public Response getRouteDefinition(
      @Auth Optional<User> optionalUser,
      @PathParam("routeId") String routeId,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext) {

    OgcApiDataV2 apiData = api.getData();
    checkPathParameter(
        extensionRegistry, apiData, "/routes/{routeId}/definition", "routeId", routeId);

    QueryHandlerRoutes.QueryInputRoute queryInput =
        new ImmutableQueryInputRoute.Builder()
            .from(getGenericQueryInput(api.getData()))
            .routeId(routeId)
            .build();

    return queryHandler.handle(
        QueryHandlerRoutes.Query.GET_ROUTE_DEFINITION, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler);
  }
}
