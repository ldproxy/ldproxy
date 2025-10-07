/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.routes.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import de.ii.ogcapi.common.domain.ConformanceDeclarationExtension;
import de.ii.ogcapi.common.domain.ImmutableConformanceDeclaration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.routes.domain.RoutingConfiguration;
import de.ii.ogcapi.routes.infra.EndpointRoutesGet;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.routes.sql.domain.RoutesConfiguration;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** add routes information to the conformance declaration */
@Singleton
@AutoBind
public class RoutesOnConformanceDeclaration implements ConformanceDeclarationExtension {

  private final FeaturesCoreProviders providers;

  @Inject
  public RoutesOnConformanceDeclaration(FeaturesCoreProviders providers) {
    this.providers = providers;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return RoutingConfiguration.class;
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    ValidationResult result = ConformanceDeclarationExtension.super.onStartup(api, apiValidation);

    if (apiValidation == ValidationResult.MODE.NONE) return result;

    ImmutableValidationResult.Builder builder =
        ImmutableValidationResult.builder().from(result).mode(apiValidation);

    // check that there is at least one preference/mode and that the default preference/mode is one
    // of them
    FeatureProvider featureProvider = providers.getFeatureProviderOrThrow(api.getData());

    List<String> preferences =
        List.copyOf(
            EndpointRoutesGet.getProviderRoutingCfg(featureProvider)
                .map(RoutesConfiguration::getPreferences)
                .map(Map::keySet)
                .orElse(ImmutableSet.of()));
    if (preferences.isEmpty())
      builder.addErrors("Routing: There must be at least one value for the routing preference.");
    api.getData()
        .getExtension(RoutingConfiguration.class)
        .map(RoutingConfiguration::getDefaultPreference)
        .ifPresentOrElse(
            defaultPreference -> {
              if (!preferences.contains(defaultPreference)) {
                builder.addErrors(
                    "Routing: The default preference '{}' is not one of the known preference values: {}.",
                    defaultPreference,
                    preferences.toString());
              }
            },
            () -> builder.addErrors("Routing: No default preference has been configured."));

    List<String> modes =
        List.copyOf(
            EndpointRoutesGet.getProviderRoutingCfg(featureProvider)
                .map(RoutesConfiguration::getModes)
                .map(Map::keySet)
                .orElse(ImmutableSet.of()));
    if (modes.isEmpty())
      builder.addErrors("Routing: There must be at least one value for the routing mode.");
    api.getData()
        .getExtension(RoutingConfiguration.class)
        .map(RoutingConfiguration::getDefaultMode)
        .ifPresentOrElse(
            defaultMode -> {
              if (!modes.contains(defaultMode)) {
                builder.addErrors(
                    "Routing: The default mode '{}' is not one of the known modes: {}.",
                    defaultMode,
                    modes.toString());
              }
            },
            () -> builder.addErrors("Routing: No default mode has been configured."));
    modes.forEach(
        mode -> {
          if (!EndpointRoutesGet.getProviderRoutingCfg(featureProvider)
              .map(RoutesConfiguration::getFromToQuery)
              .map(q -> q.containsKey(mode))
              .orElse(false))
            builder.addErrors("Routing: No 'fromToQuery' is specified for mode '{}'.", mode);
          if (!EndpointRoutesGet.getProviderRoutingCfg(featureProvider)
              .map(RoutesConfiguration::getEdgesQuery)
              .map(q -> q.containsKey(mode))
              .orElse(false))
            builder.addErrors("Routing: No 'edgesQuery' is specified for mode '{}'.", mode);
        });

    return builder.build();
  }

  @Override
  public ImmutableConformanceDeclaration.Builder process(
      ImmutableConformanceDeclaration.Builder builder,
      OgcApiDataV2 apiData,
      URICustomizer uriCustomizer,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      Optional<Locale> language) {

    if (!isEnabledForApi(apiData)) {
      return builder;
    }

    FeatureProvider featureProvider = providers.getFeatureProviderOrThrow(apiData);

    List<String> preferences =
        List.copyOf(
            EndpointRoutesGet.getProviderRoutingCfg(featureProvider)
                .map(RoutesConfiguration::getPreferences)
                .map(Map::keySet)
                .orElse(ImmutableSet.of()));
    List<String> modes =
        List.copyOf(
            EndpointRoutesGet.getProviderRoutingCfg(featureProvider)
                .map(RoutesConfiguration::getModes)
                .map(Map::keySet)
                .orElse(ImmutableSet.of()));
    builder.putExtensions(
        "properties",
        ImmutableMap.of(
            RoutingBuildingBlock.CORE,
            ImmutableMap.of(
                "preferences", preferences.stream().collect(ImmutableList.toImmutableList())),
            RoutingBuildingBlock.MODE,
            ImmutableMap.of("modes", modes.stream().collect(ImmutableList.toImmutableList()))));
    return builder;
  }
}
