/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.profile.val.app;

import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.ProfileGeneric;
import de.ii.ogcapi.profile.val.domain.ProfileSetVal;
import de.ii.ogcapi.profile.val.domain.ProfileValConfiguration;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.profile.ImmutableProfileTransformations.Builder;
import de.ii.xtraplatform.features.domain.profile.ProfileTransformations;

public abstract class ProfileVal extends ProfileGeneric {

  protected final String profileSet;

  ProfileVal(ExtensionRegistry extensionRegistry) {
    super(extensionRegistry);
    this.profileSet = ProfileSetVal.ID;
  }

  public boolean isDefault() {
    return false;
  }

  public boolean isDefaultForHumanReadable() {
    return false;
  }

  @Override
  public void addPropertyTransformations(FeatureSchema schema, String mediaType, Builder builder) {
    ProfileTransformations.addPredefined(getId(), schema, mediaType, builder);
  }

  @Override
  public String getProfileSet() {
    return profileSet;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProfileValConfiguration.class;
  }
}
