/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import static de.ii.xtraplatform.features.domain.profile.ProfileSetRel.AS_KEY;
import static de.ii.xtraplatform.features.domain.profile.ProfileSetRel.AS_LINK;
import static de.ii.xtraplatform.features.domain.profile.ProfileSetRel.AS_URI;
import static de.ii.xtraplatform.features.domain.profile.ProfileSetRel.mapToLink;
import static de.ii.xtraplatform.features.domain.profile.ProfileSetRel.reduceToKey;
import static de.ii.xtraplatform.features.domain.profile.ProfileSetRel.reduceToLink;
import static de.ii.xtraplatform.features.domain.profile.ProfileSetRel.reduceToUri;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.ProfileExtensionFeatures;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.domain.profile.ImmutableProfileTransformations.Builder;
import de.ii.xtraplatform.features.domain.profile.ProfileSet;
import de.ii.xtraplatform.features.domain.profile.ProfileSetRel;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;

@Singleton
@AutoBind
public class ProfileExtensionFeaturesRel extends ProfileExtensionFeatures {

  private final ProfileSet profileSet;

  @Inject
  public ProfileExtensionFeaturesRel(
      ExtensionRegistry extensionRegistry, FeaturesCoreProviders providers) {
    super(extensionRegistry, providers);
    profileSet = new ProfileSetRel();
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return super.isEnabledForApi(apiData) && usesFeatureRef(apiData);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData) && usesFeatureRef(apiData, collectionId);
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
    if (complex) {
      return getValues();
    }

    return List.of(AS_KEY, AS_URI);
  }

  @Override
  public String getDefaultValue(boolean complex, boolean humanReadable) {
    if (complex) {
      return AS_LINK;
    }

    return AS_KEY;
  }

  @Override
  public void addPropertyTransformations(
      String value, FeatureSchema schema, String mediaType, Builder builder) {
    if (!getValues().contains(value)) {
      return;
    }

    schema.getAllNestedProperties().stream()
        .filter(SchemaBase::isFeatureRef)
        .forEach(
            property -> {
              switch (value) {
                case AS_KEY:
                  reduceToKey(property, builder);
                  break;
                case AS_URI:
                  reduceToUri(property, builder);
                  break;
                case AS_LINK:
                  if (mediaType.equals(MediaType.TEXT_HTML)) {
                    reduceToLink(property, builder);
                  } else {
                    mapToLink(property, builder);
                  }
                  break;
              }
            });
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
                    .map(ProfileSetRel::usesFeatureRef))
        .orElse(false);
  }
}
