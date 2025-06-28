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
      FormatExtension outputFormat,
      ResourceType resourceType,
      OgcApiDataV2 apiData,
      Optional<String> collectionId) {
    if ((Objects.nonNull(mediaType) && !mediaType.isCompatible(outputFormat.getMediaType().type()))
        || !getResourceType().equals(resourceType)
        || !collectionId
            .map(cid -> isEnabledForApi(apiData, cid))
            .orElse(isEnabledForApi(apiData))) {
      return Optional.empty();
    }

    if (requestedProfiles.isEmpty()) {
      // TODO find a way to avoid using reflection
      Optional<? extends ExtensionConfiguration> cfg =
          collectionId.flatMap(
              cid -> apiData.getExtension(outputFormat.getBuildingBlockConfigurationType(), cid));
      if (cfg.isEmpty()) {
        cfg = apiData.getExtension(outputFormat.getBuildingBlockConfigurationType());
      }

      Map<String, String> defaultsFromConfig =
          cfg.map(
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

      if (defaultsFromConfig.containsKey(getId())) {
        Optional<Profile> fromConfig =
            getProfiles(apiData, collectionId).stream()
                .filter(profile -> profile.getId().equals(defaultsFromConfig.get(getId())))
                .findFirst();
        if (fromConfig.isPresent()) {
          return fromConfig;
        }
      }
    }

    return getProfiles(apiData, collectionId).stream()
        .filter(requestedProfiles::contains)
        .findFirst()
        .or(() -> getDefault(apiData, collectionId, outputFormat));
  }
}
