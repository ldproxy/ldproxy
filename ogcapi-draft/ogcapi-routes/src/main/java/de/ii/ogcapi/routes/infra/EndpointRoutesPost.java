/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.routes.infra;

import static de.ii.ogcapi.routes.domain.QueryHandlerRoutes.GROUP_ROUTES_WRITE;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiResourceData;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesQuery;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiHeader;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ClassSchemaCache;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.Example;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ImmutableExample;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.routes.app.RoutingBuildingBlock;
import de.ii.ogcapi.routes.domain.HtmlForm;
import de.ii.ogcapi.routes.domain.HtmlFormDefaults;
import de.ii.ogcapi.routes.domain.ImmutableQueryInputComputeRoute;
import de.ii.ogcapi.routes.domain.ImmutableQueryInputRoute;
import de.ii.ogcapi.routes.domain.ImmutableRouteDefinition;
import de.ii.ogcapi.routes.domain.ImmutableRouteDefinitionInputs;
import de.ii.ogcapi.routes.domain.ImmutableWaypoints;
import de.ii.ogcapi.routes.domain.ImmutableWaypointsValue;
import de.ii.ogcapi.routes.domain.QueryHandlerRoutes;
import de.ii.ogcapi.routes.domain.RouteDefinition;
import de.ii.ogcapi.routes.domain.RouteFormatExtension;
import de.ii.ogcapi.routes.domain.RouteRepository;
import de.ii.ogcapi.routes.domain.RoutingConfiguration;
import de.ii.ogcapi.routes.domain.WaypointsValue;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import de.ii.xtraplatform.routes.sql.domain.RoutesConfiguration;
import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.models.media.Schema;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Route
 * @path routes
 * @langEn This operation creates a new route. The payload of the request specifies the definition
 *     of the new route.
 * @langDe Diese Operation erstellt eine neue Route. Die Nutzlast der Anfrage gibt die Definition
 *     der neuen Route an.
 * @ref:formats {@link de.ii.ogcapi.routes.domain.RouteFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointRoutesPost extends Endpoint implements ConformanceClass, ApiExtensionHealth {

  public static final ApiMediaType REQUEST_MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(MediaType.APPLICATION_JSON_TYPE)
          .label("JSON")
          .parameter("json")
          .build();

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointRoutesPost.class);
  private static final List<String> TAGS = ImmutableList.of("Routing");
  // TODO determine the appropriate segment limit
  public static final int LIMIT = 250_000;

  private final QueryHandlerRoutes queryHandler;
  private final Schema<?> schemaRouteDefinition;
  private final Map<String, Schema<?>> referencedSchemas;
  private final FeaturesQuery ogcApiFeaturesQuery;
  private final FeaturesCoreProviders providers;
  private ObjectMapper mapper;
  private final RouteRepository routeRepository;

  @Inject
  public EndpointRoutesPost(
      ExtensionRegistry extensionRegistry,
      QueryHandlerRoutes queryHandler,
      ClassSchemaCache classSchemaCache,
      FeaturesQuery ogcApiFeaturesQuery,
      FeaturesCoreProviders providers,
      RouteRepository routeRepository) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
    this.schemaRouteDefinition = classSchemaCache.getSchema(RouteDefinition.class);
    this.referencedSchemas = classSchemaCache.getReferencedSchemas(RouteDefinition.class);
    this.ogcApiFeaturesQuery = ogcApiFeaturesQuery;
    this.providers = providers;
    this.routeRepository = routeRepository;
    this.mapper = new ObjectMapper();
    mapper.registerModule(new Jdk8Module());
    mapper.registerModule(new GuavaModule());
    mapper.configure(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, true);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of(RoutingBuildingBlock.CORE, RoutingBuildingBlock.MODE);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    Optional<RoutingConfiguration> extension = apiData.getExtension(RoutingConfiguration.class);

    return extension.filter(RoutingConfiguration::isEnabled).isPresent();
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return RoutingConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats = extensionRegistry.getExtensionsForType(RouteFormatExtension.class);
    return formats;
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    ValidationResult result = super.onStartup(api, apiValidation);

    if (apiValidation == ValidationResult.MODE.NONE) return result;

    ImmutableValidationResult.Builder builder =
        ImmutableValidationResult.builder().from(result).mode(apiValidation);

    builder = routeRepository.validate(builder, api.getData());

    return builder.build();
  }

  private Map<MediaType, ApiMediaTypeContent> getRequestContent(
      Optional<RoutingConfiguration> config) {
    List<Example> examples = ImmutableList.of();
    Optional<HtmlFormDefaults> defaults =
        config.map(RoutingConfiguration::getHtml).flatMap(HtmlForm::getDefaults);
    if (defaults.isPresent()) {
      WaypointsValue waypoints =
          new ImmutableWaypointsValue.Builder()
              .addCoordinates(defaults.get().getStart(), defaults.get().getEnd())
              .build();
      ImmutableRouteDefinitionInputs.Builder builder = new ImmutableRouteDefinitionInputs.Builder();
      config.map(RoutingConfiguration::getDefaultPreference).ifPresent(builder::preference);
      builder.waypoints(new ImmutableWaypoints.Builder().value(waypoints).build());
      defaults.get().getName().ifPresent(builder::name);
      config
          .map(RoutingConfiguration::getAdditionalFlags)
          .orElse(ImmutableMap.of())
          .entrySet()
          .stream()
          .filter(f -> f.getValue().getDefault())
          .map(Map.Entry::getKey)
          .forEach(builder::addAdditionalFlags);
      examples =
          ImmutableList.of(
              new ImmutableExample.Builder()
                  .value(
                      mapper.valueToTree(
                          new ImmutableRouteDefinition.Builder().inputs(builder.build()).build()))
                  .build());
    }
    return ImmutableMap.of(
        REQUEST_MEDIA_TYPE.type(),
        new ImmutableApiMediaTypeContent.Builder()
            .ogcApiMediaType(REQUEST_MEDIA_TYPE)
            .schema(schemaRouteDefinition)
            .schemaRef(RouteDefinition.SCHEMA_REF)
            .referencedSchemas(referencedSchemas)
            .examples(examples)
            .build());
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    Optional<RoutingConfiguration> config = apiData.getExtension(RoutingConfiguration.class);
    Optional<RoutesConfiguration> routesConfig =
        EndpointRoutesGet.getProviderRoutingCfg(providers.getFeatureProviderOrThrow(apiData));
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("routes")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_ROUTES_POST);
    String path = "/routes";
    HttpMethods method = HttpMethods.POST;
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, path, method);
    List<ApiHeader> headers = getHeaders(extensionRegistry, apiData, path, method);
    String operationSummary = "compute a route";
    String description =
        String.format(
            "This creates a new route. The payload of the request specifies the definition of the new route.\n\n"
                + (config.map(RoutingConfiguration::supportsIntermediateWaypoints).orElse(false)
                    ? "A route is defined by two or more `waypoints`, which will be visited in the order in which they are provided. "
                    : "A route is defined by two `waypoints`, the start and end location of the route. ")
                + "If the coordinates are in a coordinate reference system that is not WGS 84 longitude/latitude, "
                + "the URI of the coordinate reference system must be provided in a member `coordRefSys` in the waypoints object.\n\n"
                + "A preference how the route should be optimized can be specified (`preference`). The default value is `%s`. Valid values are:\n\n"
                + String.join(
                    "",
                    routesConfig
                        .map(RoutesConfiguration::getPreferences)
                        .map(Map::entrySet)
                        .orElseThrow()
                        .stream()
                        .map(
                            entry ->
                                String.format(
                                    "* `%s`: %s\n", entry.getKey(), entry.getValue().getLabel()))
                        .collect(Collectors.toUnmodifiableList()))
                + "\n"
                + "The mode of transportation can be specified (`mode`). The default value is `%s`. Valid values are:\n\n"
                + String.join(
                    "",
                    routesConfig
                        .map(RoutesConfiguration::getModes)
                        .map(Map::entrySet)
                        .orElseThrow()
                        .stream()
                        .map(
                            entry ->
                                String.format("* `%s`: %s\n", entry.getKey(), entry.getValue()))
                        .collect(Collectors.toUnmodifiableList()))
                + "\n"
                + "In addition, some flags can be provided in `additionalFlags` to alter the computation of the route:\n\n"
                + String.join(
                    "",
                    config
                        .map(RoutingConfiguration::getAdditionalFlags)
                        .map(Map::entrySet)
                        .orElseThrow()
                        .stream()
                        .map(
                            entry ->
                                String.format(
                                    "* `%s`: %s\n", entry.getKey(), entry.getValue().getLabel()))
                        .collect(Collectors.toUnmodifiableList()))
                + "\n"
                + "An optional `name` for the route may be provided.\n\n"
                + (config.map(RoutingConfiguration::supportsHeightRestrictions).orElse(false)
                    ? "An optional vehicle height in meter may be provided (`height_m`).\n\n"
                    : "")
                + (config.map(RoutingConfiguration::supportsWeightRestrictions).orElse(false)
                    ? "An optional vehicle weight in metric tons may be provided (`weight_t`).\n\n"
                    : "")
                + (config.map(RoutingConfiguration::supportsObstacles).orElse(false)
                    ? "An optional multi-polygon geometry of areas that should be avoided may be provided (`obstacles`).\n\n"
                    : ""),
            config.map(RoutingConfiguration::getDefaultPreference).orElseThrow(),
            config.map(RoutingConfiguration::getDefaultMode).orElseThrow());
    Optional<String> operationDescription = Optional.of(description);
    ImmutableOgcApiResourceData.Builder resourceBuilder =
        new ImmutableOgcApiResourceData.Builder().path(path);
    Map<MediaType, ApiMediaTypeContent> requestContent = getRequestContent(config);
    Map<MediaType, ApiMediaTypeContent> responseContent = getResponseContent(apiData);
    ApiOperation.of(
            requestContent,
            responseContent,
            queryParameters,
            headers,
            operationSummary,
            operationDescription,
            Optional.empty(),
            getOperationId("computeRoute"),
            GROUP_ROUTES_WRITE,
            TAGS,
            RoutingBuildingBlock.MATURITY,
            RoutingBuildingBlock.SPEC)
        .ifPresent(operation -> resourceBuilder.putOperations(method.toString(), operation));
    definitionBuilder.putResources(path, resourceBuilder.build());

    return definitionBuilder.build();
  }

  /**
   * creates a new route
   *
   * @return a route according to the RouteExchangeModel
   */
  @POST
  @SuppressWarnings("UnstableApiUsage")
  public Response computeRoute(
      @Auth Optional<User> optionalUser,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @Context HttpServletRequest request,
      byte[] requestBody) {

    OgcApiDataV2 apiData = api.getData();

    FeatureProvider featureProvider = providers.getFeatureProviderOrThrow(api.getData());
    EndpointRoutesGet.ensureFeatureProviderSupportsRouting(featureProvider);

    String featureTypeId =
        api.getData()
            .getExtension(RoutingConfiguration.class)
            .map(RoutingConfiguration::getFeatureType)
            .orElseThrow(
                () ->
                    new IllegalStateException("No feature type has been configured for routing."));

    EpsgCrs defaultCrs =
        apiData
            .getExtension(RoutingConfiguration.class)
            .map(RoutingConfiguration::getDefaultEpsgCrs)
            .orElse(OgcCrs.CRS84);
    Map<String, Integer> coordinatePrecision =
        apiData
            .getExtension(RoutingConfiguration.class)
            .map(RoutingConfiguration::getCoordinatePrecision)
            .orElse(ImmutableMap.of());
    String speedLimitUnit =
        apiData
            .getExtension(RoutingConfiguration.class)
            .map(RoutingConfiguration::getSpeedLimitUnit)
            .orElse("kmph");
    Double elevationProfileSimplificationTolerance =
        apiData
            .getExtension(RoutingConfiguration.class)
            .map(RoutingConfiguration::getElevationProfileSimplificationTolerance)
            .orElse(null);

    QueryParameterSet queryParameterSet = requestContext.getQueryParameterSet();

    FeatureQuery query =
        ogcApiFeaturesQuery.requestToBareFeatureQuery(
            api.getData(),
            featureTypeId,
            defaultCrs,
            coordinatePrecision,
            LIMIT,
            queryParameterSet,
            requestContext.getMediaType().matches(MediaType.TEXT_HTML_TYPE));

    RouteDefinition definition;
    try {
      // parse input
      definition = mapper.readValue(requestBody, RouteDefinition.class);
    } catch (IOException e) {
      throw new IllegalArgumentException(
          String.format("The content of the route definition is invalid: %s", e.getMessage()), e);
    }

    String routeId =
        Hashing.murmur3_128()
            .newHasher()
            .putObject(definition, RouteDefinition.FUNNEL)
            .putString(
                Optional.ofNullable(request.getHeader("crs")).orElse(defaultCrs.toUriString()),
                StandardCharsets.UTF_8)
            .hash()
            .toString();

    if (apiData
        .getExtension(RoutingConfiguration.class)
        .map(RoutingConfiguration::isManageRoutesEnabled)
        .orElse(false)) {
      if (routeRepository.routeExists(apiData, routeId)) {
        // If the same route is already stored, just return the stored route
        QueryHandlerRoutes.QueryInputRoute queryInput =
            new ImmutableQueryInputRoute.Builder().routeId(routeId).build();
        return queryHandler.handle(QueryHandlerRoutes.Query.GET_ROUTE, queryInput, requestContext);
      }
    }

    QueryHandlerRoutes.QueryInputComputeRoute queryInput =
        new ImmutableQueryInputComputeRoute.Builder()
            .from(getGenericQueryInput(api.getData()))
            .definition(definition)
            .routeId(routeId)
            .featureProvider(featureProvider)
            .featureTypeId(featureTypeId)
            .query(query)
            .crs(Optional.ofNullable(request.getHeader("crs")))
            .defaultCrs(defaultCrs)
            .speedLimitUnit(speedLimitUnit)
            .elevationProfileSimplificationTolerance(
                Optional.ofNullable(elevationProfileSimplificationTolerance))
            .build();
    return queryHandler.handle(QueryHandlerRoutes.Query.COMPUTE_ROUTE, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(
        queryHandler,
        ogcApiFeaturesQuery,
        routeRepository,
        providers.getFeatureProviderOrThrow(apiData));
  }
}
