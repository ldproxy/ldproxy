/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.foundation.domain.ProfileExtension.ResourceType;
import de.ii.ogcapi.foundation.domain.ProfileSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public abstract class QueryParameterProfile extends OgcApiQueryParameterBase
    implements TypedQueryParameter<List<Profile>> {

  public static final String PROFILE = "profile";
  protected final ExtensionRegistry extensionRegistry;
  protected final SchemaValidator schemaValidator;

  protected QueryParameterProfile(
      ExtensionRegistry extensionRegistry, SchemaValidator schemaValidator) {
    this.extensionRegistry = extensionRegistry;
    this.schemaValidator = schemaValidator;
  }

  @Override
  public final String getName() {
    return PROFILE;
  }

  @Override
  public String getDescription() {
    return "Select the profiles to be used in the response. If no value is provided, the default profiles will be used.";
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return super.isEnabledForApi(apiData) && !getProfiles(apiData, getResourceType()).isEmpty();
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData, collectionId)
        && !getProfiles(apiData, collectionId, getResourceType()).isEmpty();
  }

  @Override
  public List<Profile> parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    if (value == null) {
      return List.of();
    }

    Builder<Profile> builder = ImmutableList.builder();

    List<Profile> allProfiles =
        optionalCollectionData
            .map(cd -> getProfiles(api.getData(), cd.getId(), getResourceType()))
            .orElseGet(() -> getProfiles(api.getData(), getResourceType()));

    Splitter.on(',')
        .trimResults()
        .split(value)
        .forEach(
            profileId ->
                builder.add(
                    allProfiles.stream()
                        .filter(p -> p.getId().equals(profileId))
                        .findFirst()
                        .orElseThrow(
                            () ->
                                new IllegalArgumentException(
                                    String.format(
                                        "Unknown value for parameter '%s': '%s'. Known values are: [ %s ]",
                                        PROFILE,
                                        profileId,
                                        String.join(
                                            ",",
                                            allProfiles.stream().map(Profile::getId).toList()))))));

    return builder.build();
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return getSchema(apiData, getResourceType());
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    return getSchema(apiData, collectionId, getResourceType());
  }

  public abstract ResourceType getResourceType();

  protected List<Profile> getProfiles(OgcApiDataV2 apiData, ResourceType resourceType) {
    return extensionRegistry.getExtensionsForType(ProfileSet.class).stream()
        .filter(
            profileSet ->
                profileSet.isEnabledForApi(apiData) && profileSet.getResourceType() == resourceType)
        .map(profileSet -> profileSet.getProfiles(apiData, Optional.empty()))
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }

  protected List<Profile> getProfiles(
      OgcApiDataV2 apiData, String collectionId, ResourceType resourceType) {
    return extensionRegistry.getExtensionsForType(ProfileSet.class).stream()
        .filter(
            profileSet ->
                profileSet.isEnabledForApi(apiData, collectionId)
                    && profileSet.getResourceType() == resourceType)
        .filter(profileSet -> profileSet.isEnabledForApi(apiData, collectionId))
        .map(profileSet -> profileSet.getProfiles(apiData, Optional.of(collectionId)))
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }

  protected ConcurrentMap<Integer, ConcurrentMap<String, Schema<?>>> schemaMap =
      new ConcurrentHashMap<>();

  protected Schema<?> getSchema(OgcApiDataV2 apiData, ResourceType resourceType) {
    int apiHashCode = apiData.hashCode();
    if (!schemaMap.containsKey(apiHashCode)) schemaMap.put(apiHashCode, new ConcurrentHashMap<>());
    if (!schemaMap.get(apiHashCode).containsKey("*_" + resourceType.name())) {
      StringSchema schema =
          new StringSchema()
              ._enum(getProfiles(apiData, resourceType).stream().map(Profile::getId).toList());
      schemaMap.get(apiHashCode).put("*_" + resourceType.name(), schema);
    }
    return schemaMap.get(apiHashCode).get("*_" + resourceType.name());
  }

  protected Schema<?> getSchema(
      OgcApiDataV2 apiData, String collectionId, ResourceType resourceType) {
    int apiHashCode = apiData.hashCode();
    if (!schemaMap.containsKey(apiHashCode)) schemaMap.put(apiHashCode, new ConcurrentHashMap<>());
    if (!schemaMap.get(apiHashCode).containsKey(collectionId + "_" + resourceType.name())) {
      ArraySchema schema =
          new ArraySchema()
              .items(
                  new StringSchema()
                      ._enum(
                          getProfiles(apiData, collectionId, resourceType).stream()
                              .map(Profile::getId)
                              .toList()));
      schemaMap.get(apiHashCode).put(collectionId + "_" + resourceType.name(), schema);
    }
    return schemaMap.get(apiHashCode).get(collectionId + "_" + resourceType.name());
  }
}
