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
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiResourceData;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiHeader;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.routes.app.RoutingBuildingBlock;
import de.ii.ogcapi.routes.domain.HtmlForm;
import de.ii.ogcapi.routes.domain.ImmutableQueryInputRoutes;
import de.ii.ogcapi.routes.domain.ImmutableRouteDefinitionInfo;
import de.ii.ogcapi.routes.domain.QueryHandlerRoutes;
import de.ii.ogcapi.routes.domain.RoutesFormatExtension;
import de.ii.ogcapi.routes.domain.RoutingConfiguration;
import de.ii.ogcapi.routes.domain.RoutingFlag;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureProviderEntity;
import de.ii.xtraplatform.routes.sql.domain.RoutesConfiguration;
import io.dropwizard.auth.Auth;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Routes
 * @path routes
 * @langEn Fetches the list of stored routes.
 * @langDe Holt die Liste der gespeicherten Routen.
 * @ref:formats {@link de.ii.ogcapi.routes.domain.RoutesFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointRoutesGet extends Endpoint implements ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointRoutesGet.class);
  private static final List<String> TAGS = ImmutableList.of("Routing");

  private final QueryHandlerRoutes queryHandler;
  private final FeaturesCoreProviders providers;

  @Inject
  public EndpointRoutesGet(
      ExtensionRegistry extensionRegistry,
      QueryHandlerRoutes queryHandler,
      FeaturesCoreProviders providers) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
    this.providers = providers;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    Optional<RoutingConfiguration> extension = apiData.getExtension(RoutingConfiguration.class);

    return extension.filter(RoutingConfiguration::isEnabled).isPresent()
        && (extension.map(RoutingConfiguration::getHtml).map(HtmlForm::isEnabled).orElse(false)
            || extension.map(RoutingConfiguration::isManageRoutesEnabled).orElse(false));
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return RoutingConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats = extensionRegistry.getExtensionsForType(RoutesFormatExtension.class);
    return formats;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    Optional<RoutingConfiguration> config = apiData.getExtension(RoutingConfiguration.class);
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("routes")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_ROUTES_GET);
    String path = "/routes";
    HttpMethods method = HttpMethods.GET;
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, path, method);
    List<ApiHeader> headers = getHeaders(extensionRegistry, apiData, path, method);
    String operationSummary =
        config.map(RoutingConfiguration::isManageRoutesEnabled).orElse(false)
            ? "fetch the list of routes"
            : (config.map(RoutingConfiguration::getHtml).map(HtmlForm::isEnabled).orElse(false)
                ? "provide a HTML form to compute a route"
                : "");
    Optional<String> operationDescription =
        Optional.of(
            (config.map(RoutingConfiguration::isManageRoutesEnabled).orElse(false)
                    ? "This operation returns a list of routes that are currently available on the server. "
                    : "")
                + (config.map(RoutingConfiguration::getHtml).map(HtmlForm::isEnabled).orElse(false)
                    ? "The HTML representation also includes a HTML form to compute a route."
                    : ""));
    ImmutableOgcApiResourceData.Builder resourceBuilder =
        new ImmutableOgcApiResourceData.Builder().path(path);
    ApiOperation.getResource(
            apiData,
            path,
            false,
            queryParameters,
            headers,
            getResponseContent(apiData),
            operationSummary,
            operationDescription,
            Optional.empty(),
            getOperationId("getRoutes"),
            GROUP_ROUTES_READ,
            TAGS,
            RoutingBuildingBlock.MATURITY,
            RoutingBuildingBlock.SPEC)
        .ifPresent(operation -> resourceBuilder.putOperations(method.name(), operation));
    definitionBuilder.putResources(path, resourceBuilder.build());

    return definitionBuilder.build();
  }

  /**
   * lists all stored routes and prepares the necessary information to create a form for requesting
   * a new route
   *
   * @return a route according to the RouteExchangeModel
   */
  @GET
  public Response getRoutes(
      @Auth Optional<User> optionalUser,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @Context UriInfo uriInfo,
      @Context HttpServletRequest request) {

    OgcApiDataV2 apiData = api.getData();

    FeatureProvider featureProvider = providers.getFeatureProviderOrThrow(api.getData());
    ensureFeatureProviderSupportsRouting(featureProvider);

    Map<String, String> preferences =
        getProviderRoutingCfg(featureProvider)
            .map(RoutesConfiguration::getPreferences)
            .map(
                map ->
                    map.entrySet().stream()
                        .map(
                            entry ->
                                new AbstractMap.SimpleImmutableEntry<>(
                                    entry.getKey(), entry.getValue().getLabel()))
                        .collect(
                            ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue)))
            .orElse(ImmutableMap.of());

    Map<String, String> modes =
        getProviderRoutingCfg(featureProvider)
            .map(RoutesConfiguration::getModes)
            .map(
                map ->
                    map.entrySet().stream()
                        .map(
                            entry ->
                                new AbstractMap.SimpleImmutableEntry<>(
                                    entry.getKey(), entry.getValue()))
                        .collect(
                            ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue)))
            .orElse(ImmutableMap.of());

    Map<String, RoutingFlag> additionalFlags =
        apiData
            .getExtension(RoutingConfiguration.class)
            .map(RoutingConfiguration::getAdditionalFlags)
            .map(
                map ->
                    map.entrySet().stream()
                        .map(
                            entry ->
                                new AbstractMap.SimpleImmutableEntry<>(
                                    entry.getKey(), entry.getValue()))
                        .collect(
                            ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue)))
            .orElse(ImmutableMap.of());

    QueryHandlerRoutes.QueryInputRoutes queryInput =
        new ImmutableQueryInputRoutes.Builder()
            .from(getGenericQueryInput(api.getData()))
            .templateInfo(
                new ImmutableRouteDefinitionInfo.Builder()
                    .preferences(preferences)
                    .defaultPreference(
                        apiData
                            .getExtension(RoutingConfiguration.class)
                            .map(RoutingConfiguration::getDefaultPreference)
                            .orElse(preferences.keySet().stream().findFirst().orElseThrow()))
                    .modes(modes)
                    .defaultMode(
                        apiData
                            .getExtension(RoutingConfiguration.class)
                            .map(RoutingConfiguration::getDefaultMode)
                            .orElse(preferences.keySet().stream().findFirst().orElseThrow()))
                    .additionalFlags(additionalFlags)
                    .build())
            .build();
    return queryHandler.handle(QueryHandlerRoutes.Query.GET_ROUTES, queryInput, requestContext);
  }

  public static void ensureFeatureProviderSupportsRouting(FeatureProvider featureProvider) {
    if (!featureProvider.queries().isSupported()) {
      throw new IllegalStateException("Feature provider does not support queries.");
    }
    ((FeatureProviderEntity) featureProvider)
        .getData()
        .getExtension(RoutesConfiguration.class)
        .orElseThrow(() -> new IllegalStateException("Feature provider does not support routing."));
  }

  public static Optional<RoutesConfiguration> getProviderRoutingCfg(
      FeatureProvider featureProvider) {
    return ((FeatureProviderEntity) featureProvider)
        .getData()
        .getExtension(RoutesConfiguration.class);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler, providers.getFeatureProviderOrThrow(apiData));
  }
}
