/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.routes.app;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.DefaultLinksGenerator;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.routes.domain.ImmutableRouteDefinition;
import de.ii.ogcapi.routes.domain.ImmutableRoutes;
import de.ii.ogcapi.routes.domain.Route;
import de.ii.ogcapi.routes.domain.RouteDefinition;
import de.ii.ogcapi.routes.domain.RouteDefinitionFormatExtension;
import de.ii.ogcapi.routes.domain.RouteFormatExtension;
import de.ii.ogcapi.routes.domain.RouteRepository;
import de.ii.ogcapi.routes.domain.Routes;
import de.ii.ogcapi.routes.domain.RoutesFormatExtension;
import de.ii.ogcapi.routes.domain.RoutesLinksGenerator;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.base.domain.AppLifeCycle;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatile;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.values.domain.Identifier;
import de.ii.xtraplatform.values.domain.KeyValueStore;
import de.ii.xtraplatform.values.domain.ValueStore;
import de.ii.xtraplatform.web.domain.LastModified;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class RouteRepositoryFiles extends AbstractVolatile
    implements RouteRepository, AppLifeCycle {

  private static final Logger LOGGER = LoggerFactory.getLogger(RouteRepositoryFiles.class);

  private final ExtensionRegistry extensionRegistry;
  private final KeyValueStore<Route> routesStore;
  private final KeyValueStore<RouteDefinition> routeDefinitionsStore;
  private final I18n i18n;
  private final DefaultLinksGenerator defaultLinkGenerator;
  private final RoutesLinksGenerator routesLinkGenerator;
  private final ObjectMapper mapper;

  @Inject
  public RouteRepositoryFiles(
      AppContext appContext,
      ValueStore valueStore,
      ExtensionRegistry extensionRegistry,
      I18n i18n,
      VolatileRegistry volatileRegistry) {
    super(volatileRegistry, "app/routes");
    this.routesStore = valueStore.forTypeWritable(Route.class);
    this.routeDefinitionsStore = valueStore.forTypeWritable(RouteDefinition.class);
    this.i18n = i18n;
    this.extensionRegistry = extensionRegistry;
    this.defaultLinkGenerator = new DefaultLinksGenerator();
    this.routesLinkGenerator = new RoutesLinksGenerator();
    this.mapper = new ObjectMapper();
    mapper.registerModule(new Jdk8Module());
    mapper.registerModule(new GuavaModule());
    mapper.configure(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, true);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  @Override
  public CompletionStage<Void> onStart(boolean isStartupAsync) {
    onVolatileStart();

    return volatileRegistry
        .onAvailable(routesStore, routeDefinitionsStore)
        .thenRun(() -> setState(State.AVAILABLE));
  }

  @Override
  public Stream<RoutesFormatExtension> getRoutesFormatStream(OgcApiDataV2 apiData) {
    return extensionRegistry.getExtensionsForType(RoutesFormatExtension.class).stream()
        .filter(format -> format.isEnabledForApi(apiData));
  }

  @Override
  public Stream<RouteFormatExtension> getRouteFormatStream(OgcApiDataV2 apiData) {
    return extensionRegistry.getExtensionsForType(RouteFormatExtension.class).stream()
        .filter(format -> format.isEnabledForApi(apiData));
  }

  @Override
  public Stream<RouteDefinitionFormatExtension> getRouteDefinitionFormatStream(
      OgcApiDataV2 apiData) {
    return extensionRegistry.getExtensionsForType(RouteDefinitionFormatExtension.class).stream()
        .filter(format -> format.isEnabledForApi(apiData));
  }

  private List<String> getRouteIds(OgcApiDataV2 apiData) {
    return routesStore.identifiers(apiData.getId()).stream()
        .map(Identifier::id)
        .sorted()
        .collect(ImmutableList.toImmutableList());
  }

  @Override
  public Routes getRoutes(OgcApiDataV2 apiData, ApiRequestContext requestContext) {
    List<String> routeIds = getRouteIds(apiData);

    ImmutableRoutes.Builder builder =
        new ImmutableRoutes.Builder()
            .addAllLinks(
                defaultLinkGenerator.generateLinks(
                    requestContext.getUriCustomizer(),
                    requestContext.getMediaType(),
                    requestContext.getAlternateMediaTypes(),
                    i18n,
                    requestContext.getLanguage()));

    routeIds.forEach(
        routeId ->
            builder.addLinks(
                routesLinkGenerator.generateRouteLink(
                    routeId,
                    getRouteName(apiData, routeId, requestContext.getLanguage()),
                    requestContext.getUriCustomizer(),
                    i18n,
                    requestContext.getLanguage())));
    return builder.build();
  }

  @Override
  public boolean routeExists(OgcApiDataV2 apiData, String routeId) {
    return routesStore.has(routeId, apiData.getId());
  }

  private String getRouteName(OgcApiDataV2 apiData, String routeId, Optional<Locale> language) {
    return getRouteDefinition(apiData, routeId)
        .getInputs()
        .getName()
        .orElse(i18n.get("routeLinkFallback", language));
  }

  @Override
  public RouteDefinition getRouteDefinition(OgcApiDataV2 apiData, String routeId) {
    if (!routeExists(apiData, routeId)) {
      throw new NotFoundException(
          MessageFormat.format("The route ''{0}'' does not exist in this API.", routeId));
    }

    return routeDefinitionsStore.get(routeId, apiData.getId());
  }

  @Override
  public ImmutableValidationResult.Builder validate(
      ImmutableValidationResult.Builder builder, OgcApiDataV2 apiData) {
    List<String> routeIds = getRouteIds(apiData);

    routeIds.forEach(
        routeId -> {
          if (!routesStore.has(routeId, apiData.getId()))
            builder.addStrictErrors("Route Repository: Route '{}' is not available.", routeId);
          if (!routeDefinitionsStore.has(routeId, apiData.getId()))
            builder.addStrictErrors(
                "Route Repository: The definition of route '{}' is not available.", routeId);
        });

    return builder;
  }

  @Override
  public Route getRoute(OgcApiDataV2 apiData, String routeId, RouteFormatExtension format) {
    if (!routeExists(apiData, routeId)) {
      throw new NotFoundException(
          MessageFormat.format("The route ''{0}'' does not exist in this API.", routeId));
    }

    return routesStore.get(routeId, apiData.getId());
  }

  @Override
  public Date getLastModified(OgcApiDataV2 apiData, String routeId) {
    if (routeDefinitionsStore.has(routeId, apiData.getId())) {
      return LastModified.from(routeDefinitionsStore.lastModified(routeId, apiData.getId()));
    }

    return null;
  }

  @Override
  public void writeRouteAndDefinition(
      OgcApiDataV2 apiData,
      String routeId,
      RouteFormatExtension format,
      byte[] routeBytes,
      RouteDefinition routeDefinition,
      List<Link> routeDefinitionLinks)
      throws IOException {
    try {
      Route route = mapper.readValue(routeBytes, Route.class);
      routesStore.put(routeId, route, apiData.getId()).join();
    } catch (IOException e) {
      routesStore.delete(routeId, apiData.getId());
      throw e;
    } catch (CompletionException e) {
      routesStore.delete(routeId, apiData.getId());
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      throw e;
    }
    try {
      RouteDefinition definition =
          new ImmutableRouteDefinition.Builder()
              .from(routeDefinition)
              .links(routeDefinitionLinks)
              .build();
      routeDefinitionsStore.put(routeId, definition, apiData.getId()).join();
    } catch (CompletionException e) {
      deleteRoute(apiData, routeId);
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      throw e;
    }
  }

  @Override
  public void deleteRoute(OgcApiDataV2 apiData, String routeId) throws IOException {
    try {
      routesStore.delete(routeId, apiData.getId()).join();
      routeDefinitionsStore.delete(routeId, apiData.getId()).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      throw e;
    }
  }
}
