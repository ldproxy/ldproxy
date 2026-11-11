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
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaDocument;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaDocument.VERSION;
import de.ii.xtraplatform.values.domain.Values;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public abstract class JsonSchemaCache {

  private final ConcurrentMap<
          Integer,
          ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<VERSION, JsonSchemaDocument>>>>
      cache;
  private final Optional<Values<Codelist>> codelists;
  private final AtomicLong codelistsRevision;

  protected JsonSchemaCache() {
    this(Optional.empty());
  }

  /**
   * For schemas derived with codelists. The cache is cleared when a codelist is added, changed or
   * removed in the store.
   */
  protected JsonSchemaCache(Values<Codelist> codelists) {
    this(Optional.of(codelists));
  }

  private JsonSchemaCache(Optional<Values<Codelist>> codelists) {
    this.cache = new ConcurrentHashMap<>();
    this.codelists = codelists;
    this.codelistsRevision = new AtomicLong(getCodelistsRevision());
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
    long currentCodelistsRevision = getCodelistsRevision();
    if (codelistsRevision.getAndSet(currentCodelistsRevision) != currentCodelistsRevision) {
      cache.clear();
    }

    String profileKey =
        profiles.stream().map(Profile::getId).sorted().collect(Collectors.joining("#"));
    return cache
        .computeIfAbsent(apiData.hashCode(), ignore -> new ConcurrentHashMap<>())
        .computeIfAbsent(collectionData.getId(), ignore -> new ConcurrentHashMap<>())
        .computeIfAbsent(profileKey, ignore -> new ConcurrentHashMap<>())
        .computeIfAbsent(
            version,
            ignore -> {
              JsonSchemaDocument schema =
                  deriveSchema(
                      featureSchema, apiData, collectionData, profiles, schemaUri, version);

              for (JsonSchemaExtension extension : jsonSchemaExtensions) {
                schema =
                    (JsonSchemaDocument)
                        extension.process(
                            schema, featureSchema, apiData, collectionData.getId(), profiles);
              }

              return schema;
            });
  }

  protected final Map<String, Codelist> getCodelists() {
    return codelists.map(Values::asMap).orElse(Map.of());
  }

  private long getCodelistsRevision() {
    return codelists.map(Values::getRevision).orElse(0L);
  }

  protected abstract JsonSchemaDocument deriveSchema(
      FeatureSchema schema,
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData,
      List<Profile> profiles,
      Optional<String> schemaUri,
      VERSION version);
}
