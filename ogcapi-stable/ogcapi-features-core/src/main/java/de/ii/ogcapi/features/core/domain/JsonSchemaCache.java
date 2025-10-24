/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaDocument;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaDocument.VERSION;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public abstract class JsonSchemaCache {

  private final ConcurrentMap<
          Integer,
          ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<VERSION, JsonSchemaDocument>>>>
      cache;

  protected JsonSchemaCache() {
    this.cache = new ConcurrentHashMap<>();
  }

  public final JsonSchemaDocument getSchema(
      FeatureSchema featureSchema,
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData,
      List<Profile> profiles,
      Optional<String> schemaUri,
      List<JsonSchemaExtension> jsonSchemaExtensions) {
    return getSchema(
        featureSchema,
        apiData,
        collectionData,
        profiles,
        schemaUri,
        jsonSchemaExtensions,
        VERSION.current());
  }

  public final JsonSchemaDocument getSchema(
      FeatureSchema featureSchema,
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData,
      List<Profile> profiles,
      Optional<String> schemaUri,
      List<JsonSchemaExtension> jsonSchemaExtensions,
      VERSION version) {
    int apiHashCode = apiData.hashCode();
    if (!cache.containsKey(apiHashCode)) {
      cache.put(apiHashCode, new ConcurrentHashMap<>());
    }
    if (!cache.get(apiHashCode).containsKey(collectionData.getId())) {
      cache.get(apiHashCode).put(collectionData.getId(), new ConcurrentHashMap<>());
    }
    String profileKey =
        profiles.stream().map(Profile::getId).sorted().collect(Collectors.joining("#"));
    if (!cache.get(apiHashCode).get(collectionData.getId()).containsKey(profileKey)) {
      cache.get(apiHashCode).get(collectionData.getId()).put(profileKey, new ConcurrentHashMap<>());
    }
    if (!cache.get(apiHashCode).get(collectionData.getId()).get(profileKey).containsKey(version)) {
      JsonSchemaDocument schema =
          deriveSchema(featureSchema, apiData, collectionData, profiles, schemaUri, version);

      for (JsonSchemaExtension extension : jsonSchemaExtensions) {
        schema =
            (JsonSchemaDocument)
                extension.process(schema, featureSchema, apiData, collectionData.getId(), profiles);
      }

      cache.get(apiHashCode).get(collectionData.getId()).get(profileKey).put(version, schema);
    }

    return cache.get(apiHashCode).get(collectionData.getId()).get(profileKey).get(version);
  }

  protected abstract JsonSchemaDocument deriveSchema(
      FeatureSchema schema,
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData,
      List<Profile> profiles,
      Optional<String> schemaUri,
      VERSION version);
}
