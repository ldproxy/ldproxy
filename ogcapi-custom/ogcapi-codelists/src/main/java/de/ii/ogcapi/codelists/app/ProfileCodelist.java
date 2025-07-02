/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.codelists.app;

import de.ii.ogcapi.features.core.domain.JsonSchema;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.ProfileGeneric;

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

  public abstract JsonSchema process(JsonSchema jsonSchema, String codelistId, String codelistUri);
}
