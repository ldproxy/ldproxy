/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.profile.codelist.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class ProfileCodelistsRef extends ProfileCodelist {

  @Inject
  ProfileCodelistsRef(ExtensionRegistry extensionRegistry) {
    super(extensionRegistry);
  }

  @Override
  public String getId() {
    return "codelists-ref";
  }

  // Nothing to process, the codelist URI is always added to the schema, if the codelist building
  // block is enabled.
}
