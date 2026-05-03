/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ExtendableConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.base.domain.resiliency.OptionalVolatileCapability;
import de.ii.xtraplatform.entities.domain.EntityRegistry;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureProviderEntity;
import java.util.Optional;
import java.util.function.Function;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@AutoBind
public class FeaturesCoreProvidersImpl implements FeaturesCoreProviders {

  private final EntityRegistry entityRegistry;

  @Inject
  public FeaturesCoreProvidersImpl(EntityRegistry entityRegistry) {
    this.entityRegistry = entityRegistry;
  }

  @Override
  public boolean hasFeatureProvider(OgcApiDataV2 apiData) {
    return getFeatureProvider(apiData).isPresent();
  }

  @Override
  public Optional<FeatureProvider> getFeatureProvider(OgcApiDataV2 apiData) {
    Optional<FeatureProvider> optionalFeatureProvider = getOptionalFeatureProvider(apiData);

    if (!optionalFeatureProvider.isPresent()) {
      optionalFeatureProvider =
          entityRegistry
              .getEntity(FeatureProviderEntity.class, apiData.getId())
              .map(FeatureProvider.class::cast);
    }
    return optionalFeatureProvider;
  }

  @Override
  public <T> Optional<T> getFeatureProvider(
      OgcApiDataV2 apiData, Function<FeatureProvider, OptionalVolatileCapability<T>> capability) {
    return getFeatureProvider(apiData)
        .map(capability)
        .filter(OptionalVolatileCapability::isAvailable)
        .map(OptionalVolatileCapability::get);
  }

  @Override
  public FeatureProvider getFeatureProviderOrThrow(OgcApiDataV2 apiData) {
    return getFeatureProvider(apiData)
        .orElseThrow(() -> new IllegalStateException("No feature provider found."));
  }

  @Override
  public boolean hasFeatureProvider(
      OgcApiDataV2 apiData, FeatureTypeConfigurationOgcApi featureType) {
    return getFeatureProvider(apiData, featureType).isPresent();
  }

  @Override
  public Optional<FeatureProvider> getFeatureProvider(
      OgcApiDataV2 apiData, FeatureTypeConfigurationOgcApi featureType) {
    return getOptionalFeatureProvider(featureType).or(() -> getFeatureProvider(apiData));
  }

  @Override
  public <T> Optional<T> getFeatureProvider(
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi featureType,
      Function<FeatureProvider, OptionalVolatileCapability<T>> capability) {
    return getFeatureProvider(apiData, featureType)
        .map(capability)
        .filter(OptionalVolatileCapability::isAvailable)
        .map(OptionalVolatileCapability::get);
  }

  @Override
  public FeatureProvider getFeatureProviderOrThrow(
      OgcApiDataV2 apiData, FeatureTypeConfigurationOgcApi featureType) {
    return getOptionalFeatureProvider(featureType).orElse(getFeatureProviderOrThrow(apiData));
  }

  @Override
  public <T> T getFeatureProviderOrThrow(
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi featureType,
      Function<FeatureProvider, OptionalVolatileCapability<T>> capability) {
    return capability.apply(getFeatureProviderOrThrow(apiData, featureType)).get();
  }

  private Optional<FeatureProvider> getOptionalFeatureProvider(
      ExtendableConfiguration extendableConfiguration) {
    // return Optional.empty();
    return extendableConfiguration
        .getExtension(FeaturesCoreConfiguration.class)
        .filter(ExtensionConfiguration::isEnabled)
        .flatMap(FeaturesCoreConfiguration::getFeatureProvider)
        .flatMap(id -> entityRegistry.getEntity(FeatureProviderEntity.class, id));
  }
}
