/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.DatetimeDefaultProvider;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Objects;
import java.util.Optional;

/**
 * On a collection where the {@code VERSIONED_FEATURES} building block is enabled, defaults a
 * missing {@code datetime} query parameter to the configured {@code defaultDatetime} ({@code now}
 * unless overridden). The standard {@code datetime} parser then resolves the value ({@code "now"}
 * resolves to the request timestamp) and runs it through {@code TIntersects}, which yields only the
 * version of each feature that was valid at that time given that retired versions carry a closed
 * primary interval.
 */
@Singleton
@AutoBind
public class DefaultDatetimeOnVersionedCollection implements DatetimeDefaultProvider {

  @Inject
  public DefaultDatetimeOnVersionedCollection() {}

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return VersionedFeaturesConfiguration.class;
  }

  @Override
  public Optional<String> getDefault(
      OgcApiDataV2 apiData, FeatureTypeConfigurationOgcApi collectionData) {
    return collectionData
        .getExtension(VersionedFeaturesConfiguration.class)
        .filter(ExtensionConfiguration::isEnabled)
        .map(c -> Objects.requireNonNullElse(c.getDefaultDatetime(), "now"));
  }
}
