/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Singleton
@AutoBind
public class VersionedFeaturesConformanceClass implements ConformanceClass {

  // TODO replace with the assigned OGC URIs once the proposal is converted into a candidate
  // standard with a Part number.
  static final String CORE =
      "http://www.opengis.net/spec/ogcapi-features-n/0.0/conf/versioned-features-core";
  static final String MULTIPLE_VERSIONS =
      "http://www.opengis.net/spec/ogcapi-features-n/0.0/conf/versioned-features-multiple-versions-in-response";
  static final String PROFILE_VERSIONS_AS_FEATURES =
      "http://www.opengis.net/spec/ogcapi-features-n/0.0/conf/versioned-features-profile-versions-as-features";
  static final String PROFILE_VERSIONS_AS_FEATURES_UNIQUE_IDS =
      "http://www.opengis.net/spec/ogcapi-features-n/0.0/conf/versioned-features-profile-versions-as-features-unique-ids";
  static final String MUTATIONS =
      "http://www.opengis.net/spec/ogcapi-features-n/0.0/conf/versioned-features-mutations";

  @Inject
  public VersionedFeaturesConformanceClass() {}

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return VersionedFeaturesConfiguration.class;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return apiData.getCollections().values().stream()
        .anyMatch(
            collectionData ->
                collectionData
                    .getExtension(VersionedFeaturesConfiguration.class)
                    .filter(ExtensionConfiguration::isEnabled)
                    .isPresent());
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    List<String> uris = new ArrayList<>();
    uris.add(CORE);
    uris.add(MULTIPLE_VERSIONS);
    uris.add(PROFILE_VERSIONS_AS_FEATURES);
    boolean anyCollectionHasCompositeIdPattern =
        apiData.getCollections().values().stream()
            .anyMatch(
                collectionData ->
                    collectionData
                        .getExtension(VersionedFeaturesConfiguration.class)
                        .filter(ExtensionConfiguration::isEnabled)
                        .map(VersionedFeaturesConfiguration::getCompositeIdPattern)
                        .filter(p -> p != null && !p.isBlank())
                        .isPresent());
    if (anyCollectionHasCompositeIdPattern) {
      uris.add(PROFILE_VERSIONS_AS_FEATURES_UNIQUE_IDS);
    }
    boolean anyCollectionHasMutations =
        apiData.getCollections().values().stream()
            .anyMatch(
                collectionData ->
                    collectionData
                        .getExtension(VersionedFeaturesConfiguration.class)
                        .filter(ExtensionConfiguration::isEnabled)
                        .map(VersionedFeaturesConfiguration::getMutationTime)
                        .filter(Objects::nonNull)
                        .isPresent());
    if (anyCollectionHasMutations) {
      uris.add(MUTATIONS);
    }
    return ImmutableList.copyOf(uris);
  }
}
