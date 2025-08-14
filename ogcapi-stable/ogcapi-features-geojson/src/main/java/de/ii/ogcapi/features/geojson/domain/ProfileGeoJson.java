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

public abstract class ProfileGeoJson extends ProfileGeneric {

  protected final String profileSet;

  protected ProfileGeoJson(ExtensionRegistry extensionRegistry) {
    super(extensionRegistry);
    this.profileSet = ProfileSetGeoJson.ID;
  }

  @Override
  public String getProfileSet() {
    return profileSet;
  }

  public boolean writeJsonFgExtensions() {
    return false;
  }

  public boolean writeSecondaryGeometry() {
    return false;
  }
}
