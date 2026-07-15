/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.profile.crs.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.ProfileSet;
import de.ii.ogcapi.profile.crs.domain.ProfileCrsConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;
import java.util.Set;

@Singleton
@AutoBind
public class ProfileSetCrs extends ProfileSet {

  public static final String ID = "crs";
  private final FeaturesCoreProviders providers;

  @Inject
  public ProfileSetCrs(ExtensionRegistry extensionRegistry, FeaturesCoreProviders providers) {
    super(extensionRegistry, MediaType.WILDCARD_TYPE);
    this.providers = providers;
  }

  @Override
  public Set<ResourceType> getResourceTypes() {
    return Set.of(ResourceType.FEATURE);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return super.isEnabledForApi(apiData)
        && usesPositionVariants(apiData)
        && apiData
            .getExtension(FeaturesCoreConfiguration.class)
            .map(ExtensionConfiguration::isEnabled)
            .orElse(true);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData, collectionId)
        && usesPositionVariants(apiData, collectionId)
        && apiData
            .getExtension(FeaturesCoreConfiguration.class, collectionId)
            .map(ExtensionConfiguration::isEnabled)
            .orElse(true);
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProfileCrsConfiguration.class;
  }

  private boolean usesPositionVariants(OgcApiDataV2 apiData) {
    return apiData.getCollections().keySet().stream()
        .anyMatch(collectionId -> usesPositionVariants(apiData, collectionId));
  }

  private boolean usesPositionVariants(OgcApiDataV2 apiData, String collectionId) {
    return apiData
        .getCollectionData(collectionId)
        .map(
            collectionData ->
                providers
                    .getFeatureSchema(apiData, collectionData)
                    .map(
                        featureSchema ->
                            featureSchema.getAllNestedProperties().stream()
                                .anyMatch(p -> p.getVariants().isPresent()))
                    .orElse(false))
        .orElse(false);
  }
}
