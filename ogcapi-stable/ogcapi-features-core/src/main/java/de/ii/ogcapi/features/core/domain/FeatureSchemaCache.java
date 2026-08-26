/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformations;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class FeatureSchemaCache {

  private final ConcurrentMap<Integer, ConcurrentMap<String, FeatureSchema>> cache;

  protected FeatureSchemaCache() {
    this.cache = new ConcurrentHashMap<>();
  }

  public final FeatureSchema getSchema(
      FeatureSchema featureSchema,
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData,
      ExtensionConfiguration configuration,
      PropertyTransformations transformations) {
    return cache
        .computeIfAbsent(apiData.hashCode(), ignore -> new ConcurrentHashMap<>())
        .computeIfAbsent(
            collectionData.getId(),
            ignore ->
                deriveSchema(
                    featureSchema, apiData, collectionData, configuration, transformations));
  }

  protected abstract FeatureSchema deriveSchema(
      FeatureSchema schema,
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData,
      ExtensionConfiguration configuration,
      PropertyTransformations transformations);
}
