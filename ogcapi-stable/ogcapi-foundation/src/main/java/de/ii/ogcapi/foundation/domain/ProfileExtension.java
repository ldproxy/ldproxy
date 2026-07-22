/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.profile.ImmutableProfileTransformations;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The following types of profile extensions are distinguished:
 *
 * <ul>
 *   <li>Profile sets with a collection of profiles which result in variations in the representation
 *       of features that are not media-type-specific and where each profile is mapped to property
 *       transformations that are applied in the feature pipeline. These profiles are defined in a
 *       single module.
 *   <li>Profile sets that are used in format negotiation. All profiles of such a profile set apply
 *       to the same media type / output format class. These profiles may be defined in multiple
 *       modules.
 * </ul>
 */
@AutoMultiBind
public interface ProfileExtension extends ApiExtension {

  enum ResourceType {
    FEATURE,
    SCHEMA_RETURNABLES_AND_RECEIVABLES,
    SCHEMA_QUERYABLES,
    SCHEMA_SORTABLES;

    public static final Set<ResourceType> ALL_SCHEMAS =
        Set.of(SCHEMA_RETURNABLES_AND_RECEIVABLES, SCHEMA_QUERYABLES, SCHEMA_SORTABLES);
  }

  /**
   * @return the URI of the profile
   */
  static String getUri(Profile profile) {
    if (profile.getId().startsWith("val-")
        || profile.getId().startsWith("crs-")
        || profile.getId().startsWith("all-")
        || profile.getId().startsWith("validation-")) {
      return String.format("https://def.ldproxy.net/profile/%s", profile.getId());
    }
    return String.format("http://www.opengis.net/def/profile/OGC/0/%s", profile.getId());
  }

  /**
   * @return the resource types to which the profiles of the profile extension apply
   */
  Set<ResourceType> getResourceTypes();

  /**
   * @return the id of the profile extension
   */
  String getId();

  /**
   * @return the profiles of the profile extension
   */
  List<Profile> getProfiles(OgcApiDataV2 apiData, Optional<String> collectionId);

  default boolean includeAlternateLinks() {
    return false;
  }

  default void addPropertyTransformations(
      @NotNull String value,
      @NotNull FeatureSchema schema,
      @NotNull String mediaType,
      @NotNull ImmutableProfileTransformations.Builder builder) {}
}
