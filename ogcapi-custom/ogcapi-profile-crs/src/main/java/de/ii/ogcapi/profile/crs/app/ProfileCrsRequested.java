/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.profile.crs.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.ProfileGeneric;
import de.ii.ogcapi.profile.crs.domain.ProfileCrsConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * The standard behaviour and the default of the profile set: the position of the primary geometry
 * property is returned in the requested CRS in every feature encoding; positions that cannot be
 * represented in the requested CRS (for example, positions in a 1D vertical reference system) are
 * returned without a geometry. The profile exists so that a client can explicitly select the
 * standard behaviour on an API that declares {@code crs-original} as its default profile.
 */
@Singleton
@AutoBind
public class ProfileCrsRequested extends ProfileGeneric {

  public static final String ID = "crs-requested";

  @Inject
  ProfileCrsRequested(ExtensionRegistry extensionRegistry) {
    super(extensionRegistry);
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
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProfileCrsConfiguration.class;
  }
}
