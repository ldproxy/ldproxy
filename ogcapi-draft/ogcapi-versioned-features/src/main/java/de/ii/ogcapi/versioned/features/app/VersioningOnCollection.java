/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.collections.domain.CollectionExtension;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiCollection.Builder;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration.MutationTime;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration.TimeAxis;
import de.ii.xtraplatform.web.domain.URICustomizer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Singleton
@AutoBind
public class VersioningOnCollection implements CollectionExtension {

  @Inject
  public VersioningOnCollection() {}

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return VersionedFeaturesConfiguration.class;
  }

  @Override
  public Builder process(
      Builder collection,
      FeatureTypeConfigurationOgcApi featureType,
      OgcApi api,
      URICustomizer uriCustomizer,
      boolean isNested,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      Optional<Locale> language) {
    if (isNested || !isExtensionEnabled(featureType, VersionedFeaturesConfiguration.class)) {
      return collection;
    }

    VersionedFeaturesConfiguration config =
        featureType.getExtension(VersionedFeaturesConfiguration.class).orElseThrow();

    TimeAxis timeAxis = config.getTimeAxis();
    MutationTime mutationTime = config.getMutationTime();
    if (timeAxis == null && mutationTime == null) {
      return collection;
    }

    Map<String, Object> versioning = new LinkedHashMap<>();
    if (timeAxis != null) {
      versioning.put("timeAxis", wireValue(timeAxis.name()));
    }
    if (mutationTime != null) {
      versioning.put("mutationTime", wireValue(mutationTime.name()));
    }
    collection.putExtensions("versioning", ImmutableMap.copyOf(versioning));

    return collection;
  }

  private static String wireValue(String enumName) {
    return enumName.toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
