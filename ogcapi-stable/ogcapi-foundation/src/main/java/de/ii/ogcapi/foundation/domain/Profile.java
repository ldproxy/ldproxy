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
import java.util.Arrays;
import java.util.List;
import javax.validation.constraints.NotNull;

@AutoMultiBind
public interface Profile extends ApiExtension {

  static List<Profile> of(ExtensionRegistry extensionRegistry, String... profileIds) {
    return extensionRegistry.getExtensionsForType(Profile.class).stream()
        .filter(profile -> Arrays.stream(profileIds).anyMatch(id -> id.equals(profile.getId())))
        .toList();
  }

  ProfileSet getProfileSet();

  String getId();

  default String getLabel() {
    return getId();
  }

  default boolean includeAlternateLinks() {
    return false;
  }

  default void addPropertyTransformations(
      @NotNull FeatureSchema schema,
      @NotNull String mediaType,
      @NotNull ImmutableProfileTransformations.Builder builder) {}
}
