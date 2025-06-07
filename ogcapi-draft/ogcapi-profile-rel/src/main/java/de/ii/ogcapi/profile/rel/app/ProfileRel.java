/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.profile.rel.app;

import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.ProfileGeneric;
import de.ii.ogcapi.foundation.domain.ProfileSet;
import de.ii.ogcapi.profile.rel.domain.ProfileRelConfiguration;
import de.ii.ogcapi.profile.rel.domain.ProfileSetRel;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.profile.ImmutableProfileTransformations.Builder;
import de.ii.xtraplatform.features.domain.profile.ProfileTransformations;

public abstract class ProfileRel extends ProfileGeneric {

  protected final ProfileSet profileSet;

  ProfileRel(ExtensionRegistry extensionRegistry, ProfileSetRel profileSet) {
    super(extensionRegistry);
    this.profileSet = profileSet;
  }

  public boolean isDefault() {
    return false;
  }

  public boolean isDefaultForComplex() {
    return false;
  }

  @Override
  public void addPropertyTransformations(FeatureSchema schema, String mediaType, Builder builder) {
    ProfileTransformations.addPredefined(getId(), schema, mediaType, builder);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProfileRelConfiguration.class;
  }

  @Override
  public ProfileSet getProfileSet() {
    return profileSet;
  }
}
