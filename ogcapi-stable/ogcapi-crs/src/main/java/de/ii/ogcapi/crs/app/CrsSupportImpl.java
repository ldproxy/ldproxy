/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crs.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import de.ii.ogcapi.crs.domain.CrsConfiguration;
import de.ii.ogcapi.crs.domain.CrsSupport;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;

@Singleton
@AutoBind
public class CrsSupportImpl implements CrsSupport, ApiExtensionHealth {

  private final FeaturesCoreProviders providers;

  @Inject
  public CrsSupportImpl(FeaturesCoreProviders providers) {
    this.providers = providers;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CrsConfiguration.class;
  }

  @Override
  public boolean isEnabled(OgcApiDataV2 apiData) {
    return isEnabledForApi(apiData);
  }

  @Override
  public List<EpsgCrs> getSupportedCrsList(OgcApiDataV2 apiData) {
    if (!isEnabled(apiData)) return ImmutableList.of();

    return getSupportedCrsList(apiData, null);
  }

  @Override
  public List<EpsgCrs> getSupportedCrsList(
      OgcApiDataV2 apiData, @Nullable FeatureTypeConfigurationOgcApi featureTypeConfiguration) {
    EpsgCrs nativeCrs = getStorageCrs(apiData, Optional.ofNullable(featureTypeConfiguration));
    EpsgCrs defaultCrs = getDefaultCrs(apiData, Optional.ofNullable(featureTypeConfiguration));
    Set<EpsgCrs> additionalCrs =
        getAdditionalCrs(apiData, Optional.ofNullable(featureTypeConfiguration));

    return Stream.concat(Stream.of(defaultCrs, nativeCrs), additionalCrs.stream())
        .distinct()
        .collect(ImmutableList.toImmutableList());
  }

  @Override
  public boolean isSupported(OgcApiDataV2 apiData, EpsgCrs crs) {
    return isSupported(apiData, null, crs);
  }

  @Override
  public boolean isSupported(
      OgcApiDataV2 apiData,
      @Nullable FeatureTypeConfigurationOgcApi featureTypeConfiguration,
      EpsgCrs crs) {
    return getSupportedCrsList(apiData, featureTypeConfiguration).contains(crs);
  }

  @Override
  public EpsgCrs getStorageCrs(
      OgcApiDataV2 apiData, Optional<FeatureTypeConfigurationOgcApi> featureTypeConfiguration) {
    FeatureProvider provider =
        featureTypeConfiguration.isPresent()
            ? providers.getFeatureProviderOrThrow(apiData, featureTypeConfiguration.get())
            : providers.getFeatureProviderOrThrow(apiData);

    if (!provider.crs().isSupported()) {
      throw new IllegalStateException("Provider has no CRS support.");
    }

    return provider.crs().get().getNativeCrs();
  }

  private EpsgCrs getDefaultCrs(
      OgcApiDataV2 apiData, Optional<FeatureTypeConfigurationOgcApi> featureTypeConfiguration) {
    return apiData.getExtension(FeaturesCoreConfiguration.class).get().getDefaultEpsgCrs();
  }

  private Set<EpsgCrs> getAdditionalCrs(
      OgcApiDataV2 apiData, Optional<FeatureTypeConfigurationOgcApi> featureTypeConfiguration) {
    return apiData
        .getExtension(CrsConfiguration.class)
        .map(CrsConfiguration::getAdditionalCrs)
        .orElse(ImmutableSet.of());
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(providers.getFeatureProviderOrThrow(apiData).crs());
  }
}
