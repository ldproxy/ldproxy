/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.schema.validation.app;

import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaObject;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaOneOf;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaString;
import de.ii.ogcapi.features.core.domain.JsonSchema;
import de.ii.ogcapi.features.core.domain.JsonSchemaInteger;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.features.domain.SchemaBase.Scope;

public abstract class ProfileValidationReturnables extends ProfileJsonSchemaForValidation {

  protected ProfileValidationReturnables(
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
    return Scope.RETURNABLE;
  }

  @Override
  public JsonSchema getReference(JsonSchema property) {
    ImmutableJsonSchemaOneOf.Builder builder =
        new ImmutableJsonSchemaOneOf.Builder().addOneOf(property);

    if (property instanceof JsonSchemaInteger) {
      // only for integer IDs, otherwise this is already covered by the previous oneOf value
      builder.addOneOf(new ImmutableJsonSchemaString.Builder().format("uri-reference").build());
    }

    return builder
        .addOneOf(
            new ImmutableJsonSchemaObject.Builder()
                .putProperties(
                    "href", new ImmutableJsonSchemaString.Builder().format("uri-reference").build())
                .putProperties("title", new ImmutableJsonSchemaString.Builder().build())
                .addRequired("href")
                .build())
        .build();
  }

  @Override
  public boolean skipProperty(JsonSchema property) {
    return property.isWriteOnly();
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return SchemaValidationConfiguration.class;
  }
}
