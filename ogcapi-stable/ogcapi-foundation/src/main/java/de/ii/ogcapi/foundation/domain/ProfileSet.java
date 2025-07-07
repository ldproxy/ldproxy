/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.MediaType;

/**
 * The following types of profile extensions are distinguished:
 *
 * <ul>
 *   <li>Profile extensions with a set of profiles which result in variations in the representation
 *       of features that are not media-type-specific and where each profile is mapped to property
 *       transformations that are applied in the feature pipeline. These profiles are defined in a
 *       single module.
 *   <li>Profile extensions that are used in format negotiation. All profiles of such a profile
 *       extension apply to the same media type / output format class. These profiles may be defined
 *       in multiple modules.
 * </ul>
 */
public abstract class ProfileSet implements ProfileExtension {

  protected final ExtensionRegistry extensionRegistry;
  protected final MediaType mediaType;

  protected ProfileSet(ExtensionRegistry extensionRegistry, MediaType mediaType) {
    this.extensionRegistry = extensionRegistry;
    this.mediaType = mediaType;
  }

  public MediaType getMediaType() {
    return mediaType;
  }

  @Override
  public List<Profile> getProfiles(OgcApiDataV2 apiData, Optional<String> collectionId) {
    return extensionRegistry.getExtensionsForType(Profile.class).stream()
        .filter(
            profile ->
                getId().equals(profile.getProfileSet())
                    && collectionId
                        .map(cid -> profile.isEnabledForApi(apiData, cid))
                        .orElse(profile.isEnabledForApi(apiData)))
        .toList();
  }

  public Optional<Profile> negotiateProfile(
      @NotNull List<Profile> requestedProfiles,
      @NotNull List<Profile> defaultProfilesResource,
      FormatExtension outputFormat,
      ResourceType resourceType,
      OgcApiDataV2 apiData,
      Optional<String> collectionId) {
    MediaType responseMediaType =
        outputFormat != null ? outputFormat.getMediaType().type() : MediaType.WILDCARD_TYPE;
    if ((Objects.nonNull(mediaType) && !mediaType.isCompatible(responseMediaType))
        || !getResourceType().equals(resourceType)
        || !collectionId
            .map(cid -> isEnabledForApi(apiData, cid))
            .orElse(isEnabledForApi(apiData))) {
      return Optional.empty();
    }

    Optional<Profile> selectedProfile =
        requestedProfiles.stream()
            .filter(profile -> profile.getProfileSet().equals(getId()))
            .findFirst();

    if (selectedProfile.isEmpty()) {
      final Map<String, String> defaultsFromConfig;
      if (outputFormat != null) {
        Optional<? extends ExtensionConfiguration> formatConfiguration =
            collectionId.flatMap(
                cid -> apiData.getExtension(outputFormat.getBuildingBlockConfigurationType(), cid));
        if (formatConfiguration.isEmpty()) {
          formatConfiguration =
              apiData.getExtension(outputFormat.getBuildingBlockConfigurationType());
        }
        defaultsFromConfig = getDefaultProfiles(formatConfiguration);
      } else {
        defaultsFromConfig = Map.of();
      }

      List<Profile> defaultProfilesFormat =
          extensionRegistry.getExtensionsForType(Profile.class).stream()
              .filter(
                  profile ->
                      defaultsFromConfig.containsKey(profile.getProfileSet())
                          && profile
                              .getId()
                              .equals(defaultsFromConfig.get(profile.getProfileSet())))
              .toList();

      selectedProfile =
          defaultProfilesFormat.stream()
              .filter(profile -> profile.getProfileSet().equals(getId()))
              .findFirst()
              .or(
                  () ->
                      defaultProfilesResource.stream()
                          .filter(profile -> profile.getProfileSet().equals(getId()))
                          .findFirst());
    }

    return selectedProfile;
  }

  private static Map<String, String> getDefaultProfiles(
      Optional<? extends ExtensionConfiguration> cfg) {
    // TODO avoid using reflection
    return cfg.map(
            cfg2 -> {
              try {
                Method method = cfg2.getClass().getMethod("getDefaultProfiles");
                if (Map.class.isAssignableFrom(method.getReturnType())) {
                  Type genericReturnType = method.getGenericReturnType();
                  if (genericReturnType instanceof ParameterizedType parameterizedType) {
                    Type[] typeArguments = parameterizedType.getActualTypeArguments();
                    if (typeArguments.length == 2
                        && typeArguments[0].equals(String.class)
                        && typeArguments[1].equals(String.class)) {
                      return (Map<String, String>) method.invoke(cfg2);
                    }
                  }
                }
              } catch (Exception ignore) {
                // not found, return empty map
              }
              return Map.<String, String>of();
            })
        .orElse(Map.of());
  }
}
