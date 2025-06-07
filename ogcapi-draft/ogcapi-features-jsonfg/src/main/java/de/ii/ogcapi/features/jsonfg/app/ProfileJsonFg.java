/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.jsonfg.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.geojson.domain.ProfileGeoJson;
import de.ii.ogcapi.features.geojson.domain.ProfileSetGeoJson;
import de.ii.ogcapi.features.jsonfg.domain.JsonFgConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class ProfileJsonFg extends ProfileGeoJson {

  @Inject
  ProfileJsonFg(ExtensionRegistry extensionRegistry, ProfileSetGeoJson profileSet) {
    super(extensionRegistry, profileSet);
  }

  @Override
  public String getId() {
    return "jsonfg";
  }

  @Override
  public boolean writeJsonFgExtensions() {
    return true;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return JsonFgConfiguration.class;
  }
}
