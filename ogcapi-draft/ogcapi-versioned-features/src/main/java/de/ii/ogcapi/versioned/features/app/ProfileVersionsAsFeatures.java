/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.ProfileFeatureQuery;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Profile {@code versions-as-features}: each matching version of a feature is rendered as a
 * separate feature in the response collection. The default behaviour on a versioned collection
 * already returns each row as a feature, so the transform is a no-op; the profile mainly signals
 * intent (the response can carry multiple features with the same canonical id when the request
 * spans multiple versions). The profile URI is {@code
 * http://www.opengis.net/def/profile/ogc/0/versions-as-features}.
 */
@Singleton
@AutoBind
public class ProfileVersionsAsFeatures extends ProfileFeatureQuery {

  public static final String ID = "versions-as-features";

  @Inject
  ProfileVersionsAsFeatures(ExtensionRegistry extensionRegistry) {
    super(extensionRegistry);
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String getProfileSet() {
    return ProfileSetVersions.ID;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return VersionedFeaturesConfiguration.class;
  }

  @Override
  public FeatureQuery transformFeatureQuery(FeatureQuery query) {
    // Each matching version is already returned as a separate feature by default. No query
    // transformation needed — the profile only documents the response shape.
    return query;
  }
}
