/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.profile.codelist.app;

import de.ii.ogcapi.features.core.domain.JsonSchema;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.ProfileGeneric;
import de.ii.ogcapi.profile.codelist.domain.ProfileCodelistConfiguration;

public abstract class ProfileCodelist extends ProfileGeneric {

  protected final String profileSet;

  protected ProfileCodelist(ExtensionRegistry extensionRegistry) {
    super(extensionRegistry);
    this.profileSet = ProfileSetCodelist.ID;
  }

  @Override
  public String getProfileSet() {
    return profileSet;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProfileCodelistConfiguration.class;
  }

  public JsonSchema process(JsonSchema schema, String codelistId) {
    return schema;
  }
}
