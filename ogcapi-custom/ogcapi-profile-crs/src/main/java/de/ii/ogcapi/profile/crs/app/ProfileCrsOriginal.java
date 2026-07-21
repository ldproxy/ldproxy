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
 * Positions of a geometry property with a {@code variants} declaration in the provider schema are
 * represented as recorded: in their original reference system, identified by the stored verbatim
 * CRS identifier, unaffected by the {@code crs} query parameter. Feature encodings that do not
 * support the profile (for example, GeoJSON, which cannot represent other CRSs) ignore it. The
 * profile id is referenced by its literal value in the encoders (for example, {@code
 * GmlWriterPositionVariants}), which must not depend on this module.
 */
@Singleton
@AutoBind
public class ProfileCrsOriginal extends ProfileGeneric {

  public static final String ID = "crs-original";

  @Inject
  ProfileCrsOriginal(ExtensionRegistry extensionRegistry) {
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
