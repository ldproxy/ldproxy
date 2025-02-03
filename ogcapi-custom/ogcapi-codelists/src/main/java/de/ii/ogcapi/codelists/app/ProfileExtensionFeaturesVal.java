/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.codelists.app;

import static de.ii.xtraplatform.features.domain.profile.ProfileSetVal.AS_CODE;
import static de.ii.xtraplatform.features.domain.profile.ProfileSetVal.AS_TITLE;
import static de.ii.xtraplatform.features.domain.profile.ProfileSetVal.mapToTitle;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.ProfileExtensionFeatures;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.profile.ImmutableProfileTransformations;
import de.ii.xtraplatform.features.domain.profile.ProfileSet;
import de.ii.xtraplatform.features.domain.profile.ProfileSetVal;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class ProfileExtensionFeaturesVal extends ProfileExtensionFeatures {

  private final ProfileSet profileSet;

  @Inject
  public ProfileExtensionFeaturesVal(
      ExtensionRegistry extensionRegistry, FeaturesCoreProviders providers) {
    super(extensionRegistry, providers);
    profileSet = new ProfileSetVal();
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return super.isEnabledForApi(apiData) && usesCodedValue(apiData);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData) && usesCodedValue(apiData, collectionId);
  }

  @Override
  public String getPrefix() {
    return profileSet.getPrefix();
  }

  @Override
  public List<String> getValues() {
    return profileSet.getValues();
  }

  @Override
  public List<String> getSupportedValues(boolean complex, boolean humanReadable) {
    return getValues();
  }

  @Override
  public String getDefaultValue(boolean complex, boolean humanReadable) {
    if (humanReadable) {
      return AS_TITLE;
    }

    return AS_CODE;
  }

  @Override
  public void addPropertyTransformations(
      String value,
      FeatureSchema schema,
      String mediaType,
      ImmutableProfileTransformations.Builder builder) {
    if (!getValues().contains(value)) {
      return;
    }

    if (AS_TITLE.equals(value)) {
      schema.getAllNestedProperties().stream()
          .filter(p -> p.getConstraints().map(c -> c.getCodelist().isPresent()).orElse(false))
          .forEach(property -> mapToTitle(property, builder));
    }
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
                    .map(ProfileSetVal::usesCodedValue)
                    .orElse(false))
        .orElse(false);
  }
}
