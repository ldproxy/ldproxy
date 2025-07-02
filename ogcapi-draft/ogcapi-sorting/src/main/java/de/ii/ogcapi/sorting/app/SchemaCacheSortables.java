/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.sorting.app;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.collections.queryables.domain.QueryablesConfiguration.PathSeparator;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.JsonSchemaCache;
import de.ii.ogcapi.features.core.domain.JsonSchemaDocument;
import de.ii.ogcapi.features.core.domain.JsonSchemaDocument.VERSION;
import de.ii.ogcapi.features.core.domain.SchemaDeriverCollectionProperties;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.sorting.domain.SortingConfiguration;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.transform.ImmutablePropertyTransformation.Builder;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformations;
import de.ii.xtraplatform.features.domain.transform.WithTransformationsApplied;
import java.util.List;
import java.util.Optional;

class SchemaCacheSortables extends JsonSchemaCache {

  private static final String DEFAULT_FLATTENING_SEPARATOR = ".";
  private final FeaturesCoreProviders featuresCoreProviders;

  SchemaCacheSortables(FeaturesCoreProviders featuresCoreProviders) {
    this.featuresCoreProviders = featuresCoreProviders;
  }

  @Override
  protected JsonSchemaDocument deriveSchema(
      FeatureSchema schema,
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData,
      List<Profile> profiles,
      Optional<String> schemaUri,
      VERSION version) {

    FeatureSchema sortablesSchema =
        collectionData
            .getExtension(SortingConfiguration.class)
            .map(c -> c.getSortablesSchema(apiData, collectionData, schema, featuresCoreProviders))
            .orElse(schema);

    String flatteningSeparator =
        collectionData
            .getExtension(SortingConfiguration.class)
            .map(SortingConfiguration::getPathSeparator)
            .map(PathSeparator::toString)
            .orElse(DEFAULT_FLATTENING_SEPARATOR);

    WithTransformationsApplied schemaFlattener =
        new WithTransformationsApplied(
            ImmutableMap.of(
                PropertyTransformations.WILDCARD,
                new Builder().flatten(flatteningSeparator).build()));

    SchemaDeriverCollectionProperties schemaDeriverCollectionProperties =
        new SchemaDeriverCollectionProperties(
            version,
            schemaUri,
            collectionData.getLabel(),
            Optional.empty(),
            ImmutableMap.of(),
            ImmutableList.of("*"));

    return (JsonSchemaDocument)
        sortablesSchema.accept(schemaFlattener).accept(schemaDeriverCollectionProperties);
  }
}
