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
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.ProfileSet;
import de.ii.ogcapi.profile.crs.domain.ProfileCrsConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;
import java.util.Set;

/**
 * The profile set is available for every collection with features, not only for collections with a
 * {@code crsVariants} declaration in the provider schema: both profiles also determine the CRS of
 * the positions that are stored in the CRS of the provider. A collection-specific availability
 * would make the representation of the positions in a query over multiple collections depend on the
 * collections in the query.
 */
@Singleton
@AutoBind
public class ProfileSetCrs extends ProfileSet {

  public static final String ID = "crs";

  @Inject
  public ProfileSetCrs(ExtensionRegistry extensionRegistry) {
    super(extensionRegistry, MediaType.WILDCARD_TYPE);
  }

  @Override
  public Set<ResourceType> getResourceTypes() {
    return Set.of(ResourceType.FEATURE);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return super.isEnabledForApi(apiData)
        && apiData
            .getExtension(FeaturesCoreConfiguration.class)
            .map(ExtensionConfiguration::isEnabled)
            .orElse(true);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData, collectionId)
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
}
