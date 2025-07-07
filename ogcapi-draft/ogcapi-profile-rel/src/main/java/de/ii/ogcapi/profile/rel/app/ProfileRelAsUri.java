/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.profile.rel.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.xtraplatform.features.domain.profile.ProfileTransformations;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class ProfileRelAsUri extends ProfileRel {

  @Inject
  ProfileRelAsUri(ExtensionRegistry extensionRegistry) {
    super(extensionRegistry);
  }

  @Override
  public String getId() {
    return ProfileTransformations.REL_AS_URI;
  }
}
