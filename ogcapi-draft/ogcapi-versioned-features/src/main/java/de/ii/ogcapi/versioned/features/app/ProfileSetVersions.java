/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.ProfileSet;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;
import java.util.Optional;

/**
 * Profile set {@code versions} for the Versioned Features building block. Carries the {@code
 * versions-as-features} profile (and, later, {@code versions-as-features-unique-ids}) which select
 * the response shape on the items list of a versioned collection.
 */
@Singleton
@AutoBind
public class ProfileSetVersions extends ProfileSet {

  public static final String ID = "versions";

  @Inject
  public ProfileSetVersions(ExtensionRegistry extensionRegistry) {
    super(extensionRegistry, MediaType.WILDCARD_TYPE);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return apiData.getCollections().keySet().stream()
        .anyMatch(collectionId -> isEnabledForApi(apiData, collectionId));
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return !getProfiles(apiData, Optional.of(collectionId)).isEmpty();
  }

  @Override
  public ResourceType getResourceType() {
    return ResourceType.FEATURE;
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return VersionedFeaturesConfiguration.class;
  }
}
