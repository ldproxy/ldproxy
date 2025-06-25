/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.domain;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.foundation.domain.ProfileSet;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;

@Singleton
@AutoBind
public class ProfileSetGeoJson extends ProfileSet {

  public static final String ID = "geojson";

  public static final MediaType MEDIA_TYPE = new MediaType("application", "geo+json");

  @Inject
  public ProfileSetGeoJson(ExtensionRegistry extensionRegistry) {
    super(extensionRegistry, MEDIA_TYPE);
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
  public Optional<Profile> getDefault(
      OgcApiDataV2 apiData, Optional<String> collectionId, FormatExtension outputFormat) {

    return getProfiles(apiData, collectionId).stream()
        .filter(profile -> ((ProfileGeoJson) profile).isDefault())
        .findFirst()
        .or(() -> getProfiles(apiData, collectionId).stream().findFirst());
  }

  @Override
  public boolean includeAlternateLinks() {
    return true;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return GeoJsonConfiguration.class;
  }
}
