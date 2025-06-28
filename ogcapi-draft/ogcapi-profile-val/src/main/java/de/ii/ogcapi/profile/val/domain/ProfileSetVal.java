/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.profile.val.domain;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.foundation.domain.ProfileSet;
import de.ii.ogcapi.profile.val.app.ProfileVal;
import de.ii.xtraplatform.features.domain.SchemaConstraints;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;

@Singleton
@AutoBind
public class ProfileSetVal extends ProfileSet {

  public static final String ID = "val";
  private final FeaturesCoreProviders providers;

  @Inject
  public ProfileSetVal(ExtensionRegistry extensionRegistry, FeaturesCoreProviders providers) {
    super(extensionRegistry, MediaType.WILDCARD_TYPE);
    this.providers = providers;
  }

  @Override
  public ResourceType getResourceType() {
    return ResourceType.FEATURE;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return super.isEnabledForApi(apiData)
        && usesCodedValue(apiData)
        && apiData
            .getExtension(FeaturesCoreConfiguration.class)
            .map(ExtensionConfiguration::isEnabled)
            .orElse(true);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData, collectionId)
        && usesCodedValue(apiData, collectionId)
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
  public Optional<Profile> getDefault(
      OgcApiDataV2 apiData, Optional<String> collectionId, FormatExtension outputFormat) {
    if (outputFormat instanceof FeatureFormatExtension featureFormatExtension) {
      if (featureFormatExtension.isForHumans()) {
        return getProfiles(apiData, collectionId).stream()
            .filter(profile -> ((ProfileVal) profile).isDefaultForHumanReadable())
            .findFirst();
      }
    }

    return getProfiles(apiData, collectionId).stream()
        .filter(profile -> ((ProfileVal) profile).isDefault())
        .findFirst();
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProfileValConfiguration.class;
  }

  private boolean usesCodedValue(OgcApiDataV2 apiData) {
    return apiData.getCollections().keySet().stream()
        .anyMatch(collectionId -> usesCodedValue(apiData, collectionId));
  }

  private boolean usesCodedValue(OgcApiDataV2 apiData, String collectionId) {
    // only consider codelist transformations in the provider constraints as the other
    // transformations are fixed and cannot be disabled.
    return apiData
        .getCollectionData(collectionId)
        .map(
            collectionData ->
                providers
                    .getFeatureSchema(apiData, collectionData)
                    .map(
                        featureSchema ->
                            featureSchema.getAllNestedProperties().stream()
                                .anyMatch(
                                    p ->
                                        p.getConstraints()
                                            .flatMap(SchemaConstraints::getCodelist)
                                            .isPresent()))
                    .orElse(false))
        .orElse(false);
  }
}
