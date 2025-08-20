/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.ProfileGeneric;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import javax.validation.constraints.NotNull;

public abstract class ProfileFeatureQuery extends ProfileGeneric {

  protected ProfileFeatureQuery(ExtensionRegistry extensionRegistry) {
    super(extensionRegistry);
  }

  public abstract FeatureQuery transformFeatureQuery(@NotNull FeatureQuery query);
}
