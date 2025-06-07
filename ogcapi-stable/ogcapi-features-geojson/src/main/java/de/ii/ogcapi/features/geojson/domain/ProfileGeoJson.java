/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.domain;

import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.ProfileGeneric;
import de.ii.ogcapi.foundation.domain.ProfileSet;

public abstract class ProfileGeoJson extends ProfileGeneric {

  protected final ProfileSet profileSet;

  protected ProfileGeoJson(ExtensionRegistry extensionRegistry, ProfileSetGeoJson profileSet) {
    super(extensionRegistry);
    this.profileSet = profileSet;
  }

  @Override
  public ProfileSet getProfileSet() {
    return profileSet;
  }

  public boolean isDefault() {
    return false;
  }

  public boolean writeJsonFgExtensions() {
    return false;
  }
}
