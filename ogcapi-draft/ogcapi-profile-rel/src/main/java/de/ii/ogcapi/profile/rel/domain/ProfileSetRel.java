/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.profile.rel.domain;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.foundation.domain.ProfileSet;
import de.ii.ogcapi.profile.rel.app.ProfileRel;
import de.ii.xtraplatform.features.domain.SchemaBase;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;

@Singleton
@AutoBind
public class ProfileSetRel extends ProfileSet implements ConformanceClass {

  private final FeaturesCoreProviders providers;

  @Inject
  public ProfileSetRel(ExtensionRegistry extensionRegistry, FeaturesCoreProviders providers) {
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
        && usesFeatureRef(apiData)
        && apiData
            .getExtension(FeaturesCoreConfiguration.class)
            .map(ExtensionConfiguration::isEnabled)
            .orElse(true);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData, collectionId)
        && usesFeatureRef(apiData, collectionId)
        && apiData
            .getExtension(FeaturesCoreConfiguration.class, collectionId)
            .map(ExtensionConfiguration::isEnabled)
            .orElse(true);
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return List.of("http://www.opengis.net/spec/ogcapi-features-5/0.0/conf/profile-references");
  }

  @Override
  public String getId() {
    return "rel";
  }

  @Override
  public Optional<Profile> getDefault(
      OgcApiDataV2 apiData, Optional<String> collectionId, FormatExtension outputFormat) {
    if (outputFormat instanceof FeatureFormatExtension featureFormatExtension) {
      if (featureFormatExtension.isComplex()) {
        return getProfiles(apiData, collectionId).stream()
            .filter(profile -> ((ProfileRel) profile).isDefaultForComplex())
            .findFirst();
      }
    }

    return getProfiles(apiData, collectionId).stream()
        .filter(profile -> ((ProfileRel) profile).isDefault())
        .findFirst();
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProfileRelConfiguration.class;
  }

  private boolean usesFeatureRef(OgcApiDataV2 apiData) {
    return apiData.getCollections().values().stream()
        .anyMatch(
            collectionData ->
                providers
                    .getFeatureSchema(apiData, collectionData)
                    .map(
                        schema ->
                            schema.getAllNestedProperties().stream()
                                .anyMatch(SchemaBase::isFeatureRef))
                    .orElse(false));
  }

  private boolean usesFeatureRef(OgcApiDataV2 apiData, String collectionId) {
    return apiData
        .getCollectionData(collectionId)
        .flatMap(
            collectionData ->
                providers
                    .getFeatureSchema(apiData, collectionData)
                    .map(
                        featureSchema ->
                            featureSchema.getAllNestedProperties().stream()
                                .anyMatch(SchemaBase::isFeatureRef)))
        .orElse(false);
  }
}
