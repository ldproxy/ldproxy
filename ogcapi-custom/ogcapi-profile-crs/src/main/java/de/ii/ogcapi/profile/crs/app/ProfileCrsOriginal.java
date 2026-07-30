/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.profile.crs.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.ProfileResponseCrs;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.ProfileGeneric;
import de.ii.ogcapi.profile.crs.domain.ProfileCrsConfiguration;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.features.domain.FeatureCrs;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;

/**
 * Positions are represented as recorded. Positions of a geometry property with a {@code
 * crsVariants} declaration in the provider schema are returned in their original reference system,
 * identified by the stored verbatim CRS identifier; every other position is returned in the CRS in
 * which the feature provider stores it. The CRS of the provider is a fallback for the default CRS
 * of the API, so a {@code crs} parameter in the request takes precedence; in the HTML
 * representation the profile is ignored, as is the {@code crs} parameter. The profile id is
 * referenced by its literal value in the encoders (for example, {@code GmlWriterPositionVariants}),
 * which must not depend on this module.
 */
@Singleton
@AutoBind
public class ProfileCrsOriginal extends ProfileGeneric implements ProfileResponseCrs {

  public static final String ID = "crs-original";

  private final FeaturesCoreProviders providers;

  @Inject
  ProfileCrsOriginal(ExtensionRegistry extensionRegistry, FeaturesCoreProviders providers) {
    super(extensionRegistry);
    this.providers = providers;
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String getProfileSet() {
    return ProfileSetCrs.ID;
  }

  @Override
  public Optional<EpsgCrs> getResponseCrs(OgcApiDataV2 apiData, String collectionId) {
    return apiData
        .getCollectionData(collectionId)
        .flatMap(
            collectionData ->
                providers.getFeatureProvider(apiData, collectionData, FeatureProvider::crs))
        .map(FeatureCrs::getNativeCrs);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProfileCrsConfiguration.class;
  }
}
