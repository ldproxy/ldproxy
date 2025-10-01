/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crud.domain;

import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.collections.schema.domain.ProfileJsonSchemaForValidation;
import de.ii.ogcapi.crud.app.CrudConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaObject;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaString;
import de.ii.ogcapi.features.core.domain.JsonSchema;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.features.domain.SchemaBase.Scope;

public abstract class ProfileValidationReceivables extends ProfileJsonSchemaForValidation {

  protected ProfileValidationReceivables(
      ExtensionRegistry extensionRegistry, FeaturesCoreProviders providers) {
    super(extensionRegistry, providers);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return super.isEnabledForApi(apiData)
        && apiData.getCollections().keySet().stream()
            .anyMatch(collectionId -> isEnabledForApi(apiData, collectionId));
  }

  @Override
  public Scope useScope() {
    return Scope.RECEIVABLE;
  }

  @Override
  public JsonSchema getReference(JsonSchema property) {
    return new ImmutableJsonSchemaObject.Builder()
        .putProperties("id", property)
        .putProperties("type", new ImmutableJsonSchemaString.Builder().build())
        .putProperties("title", new ImmutableJsonSchemaString.Builder().build())
        .required(ImmutableList.of("id", "type", "title"))
        .build();
  }

  @Override
  public boolean skipProperty(JsonSchema property) {
    return property.isReadOnly();
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CrudConfiguration.class;
  }
}
