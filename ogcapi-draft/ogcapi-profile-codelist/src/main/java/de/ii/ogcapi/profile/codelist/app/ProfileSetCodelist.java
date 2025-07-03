/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.profile.codelist.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.ProfileSet;
import de.ii.ogcapi.profile.codelist.domain.ProfileCodelistConfiguration;
import de.ii.xtraplatform.features.domain.SchemaConstraints;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;

@Singleton
@AutoBind
public class ProfileSetCodelist extends ProfileSet {

  public static final String ID = "codelist";

  public static final MediaType MEDIA_TYPE = new MediaType("application", "schema+json");

  private final FeaturesCoreProviders providers;

  @Inject
  public ProfileSetCodelist(ExtensionRegistry extensionRegistry, FeaturesCoreProviders providers) {
    super(extensionRegistry, MEDIA_TYPE);
    this.providers = providers;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return apiData.getCollections().keySet().stream()
            .anyMatch(collectionId -> isEnabledForApi(apiData, collectionId))
        && usesCodedValue(apiData);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return !getProfiles(apiData, Optional.of(collectionId)).isEmpty()
        && usesCodedValue(apiData, collectionId);
  }

  @Override
  public ResourceType getResourceType() {
    return ResourceType.SCHEMA;
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProfileCodelistConfiguration.class;
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
