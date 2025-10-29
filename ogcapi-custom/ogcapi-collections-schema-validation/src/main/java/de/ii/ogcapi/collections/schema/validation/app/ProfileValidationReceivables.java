/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.schema.validation.app;

import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.collections.schema.domain.SchemaConfiguration;
import de.ii.ogcapi.crud.domain.CrudConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.features.domain.SchemaBase.Scope;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaConstant;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaObject;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaString;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;

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
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData, collectionId)
        && apiData
            .getExtension(CrudConfiguration.class, collectionId)
            .map(ExtensionConfiguration::isEnabled)
            .orElse(false)
        && apiData
            .getExtension(SchemaConfiguration.class, collectionId)
            .map(ExtensionConfiguration::isEnabled)
            .orElse(false);
  }

  @Override
  public Scope useScope() {
    return Scope.RECEIVABLE;
  }

  @Override
  public JsonSchema getReference(JsonSchema property) {
    return new ImmutableJsonSchemaObject.Builder()
        .putProperties("id", property)
        .putProperties(
            "type",
            property
                .getRefCollectionId()
                .map(
                    collectionId ->
                        (JsonSchema)
                            new ImmutableJsonSchemaConstant.Builder()
                                .constant(collectionId)
                                .build())
                .orElse((JsonSchema) new ImmutableJsonSchemaString.Builder().build()))
        .putProperties("title", new ImmutableJsonSchemaString.Builder().build())
        .required(
            property.getRefCollectionId().isPresent()
                ? ImmutableList.of("id", "title")
                : ImmutableList.of("id", "type", "title"))
        .build();
  }

  @Override
  public boolean skipProperty(JsonSchema property) {
    return property.isReadOnly();
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return SchemaValidationConfiguration.class;
  }
}
