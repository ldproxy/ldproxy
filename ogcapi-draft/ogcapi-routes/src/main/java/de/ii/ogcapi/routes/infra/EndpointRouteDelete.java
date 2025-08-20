/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.routes.infra;

import static de.ii.ogcapi.routes.domain.QueryHandlerRoutes.GROUP_ROUTES_WRITE;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiResourceAuxiliary;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.routes.app.RoutingBuildingBlock;
import de.ii.ogcapi.routes.domain.ImmutableQueryInputRoute;
import de.ii.ogcapi.routes.domain.QueryHandlerRoutes;
import de.ii.ogcapi.routes.domain.RoutingConfiguration;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import io.dropwizard.auth.Auth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Route
 * @path routes/{routeId}
 * @langEn Delete a stored route.
 * @langDe Löscht eine gespeicherte Route.
 */
@Singleton
@AutoBind
public class EndpointRouteDelete extends Endpoint implements ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointRouteDelete.class);

  private static final List<String> TAGS = ImmutableList.of("Routing");

  private final QueryHandlerRoutes queryHandler;

  @Inject
  public EndpointRouteDelete(ExtensionRegistry extensionRegistry, QueryHandlerRoutes queryHandler) {
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
    return ImmutableList.of();
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("routes")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_ROUTE_DELETE);
    String path = "/routes/{routeId}";
    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);
    ImmutableOgcApiResourceAuxiliary.Builder resourceBuilder =
        new ImmutableOgcApiResourceAuxiliary.Builder().path(path).pathParameters(pathParameters);

    if (pathParameters.stream().noneMatch(param -> param.getName().equals("routeId"))) {
      LOGGER.error(
          "Path parameter 'routeId' missing for resource at path '"
              + path
              + "'. The DELETE method will not be available.");
    } else {
      HttpMethods method = HttpMethods.DELETE;
      List<OgcApiQueryParameter> queryParameters =
          getQueryParameters(extensionRegistry, apiData, path, method);

      String operationSummary = "delete a route";
      Optional<String> operationDescription =
          Optional.of(
              "Delete the route with identifier `routeId`. The set of available routes can be retrieved at `/routes`.");
      ApiOperation.of(
              path,
              method,
              ImmutableMap.of(),
              queryParameters,
              ImmutableList.of(),
              operationSummary,
              operationDescription,
              Optional.empty(),
              getOperationId("deleteRoute"),
              GROUP_ROUTES_WRITE,
              TAGS,
              RoutingBuildingBlock.MATURITY,
              RoutingBuildingBlock.SPEC,
              false)
          .ifPresent(operation -> resourceBuilder.putOperations(method.name(), operation));
      definitionBuilder.putResources(path, resourceBuilder.build());
    }

    return definitionBuilder.build();
  }

  /**
   * Delete a route by id
   *
   * @param routeId the local identifier of a route
   * @return the style in a json file
   */
  @Path("/{routeId}")
  @DELETE
  public Response deleteRoute(
      @Auth Optional<User> optionalUser,
      @PathParam("routeId") String routeId,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext) {

    OgcApiDataV2 apiData = api.getData();
    checkPathParameter(extensionRegistry, apiData, "/routes/{routeId}", "routeId", routeId);

    QueryHandlerRoutes.QueryInputRoute queryInput =
        new ImmutableQueryInputRoute.Builder()
            .from(getGenericQueryInput(api.getData()))
            .routeId(routeId)
            .build();

    return queryHandler.handle(QueryHandlerRoutes.Query.DELETE_ROUTE, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler);
  }
}
