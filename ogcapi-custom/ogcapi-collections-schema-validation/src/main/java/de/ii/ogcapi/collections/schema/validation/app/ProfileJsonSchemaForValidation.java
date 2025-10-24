/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.schema.validation.app;

import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.ProfileGeneric;
import de.ii.xtraplatform.features.domain.SchemaBase.Scope;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import javax.validation.constraints.NotNull;

public abstract class ProfileJsonSchemaForValidation extends ProfileGeneric {

  protected final String profileSet;
  protected final FeaturesCoreProviders providers;

  protected ProfileJsonSchemaForValidation(
      ExtensionRegistry extensionRegistry, FeaturesCoreProviders providers) {
    super(extensionRegistry);
    this.providers = providers;
    this.profileSet = ProfileSetJsonSchemaForValidation.ID;
  }

  @Override
  public String getProfileSet() {
    return profileSet;
  }

  public abstract Scope useScope();

  public abstract JsonSchema getReference(@NotNull JsonSchema property);

  public abstract boolean skipProperty(@NotNull JsonSchema property);

  public boolean supportJsonFgExtensions() {
    return false;
  }
}
