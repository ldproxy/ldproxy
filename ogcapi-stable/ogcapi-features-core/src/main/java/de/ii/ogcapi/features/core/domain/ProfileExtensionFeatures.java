/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.ProfileExtension;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.profile.ImmutableProfileTransformations;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;

public abstract class ProfileExtensionFeatures implements ProfileExtension {

  protected final ExtensionRegistry extensionRegistry;
  protected final FeaturesCoreProviders providers;

  protected ProfileExtensionFeatures(
      ExtensionRegistry extensionRegistry, FeaturesCoreProviders providers) {
    this.extensionRegistry = extensionRegistry;
    this.providers = providers;
  }

  public Optional<String> negotiateProfile(
      @NotNull List<String> requestedProfiles,
      FeatureFormatExtension featureFormat,
      OgcApiDataV2 apiData,
      String collectionId) {
    List<String> supportedProfiles =
        getSupportedValues(featureFormat.isComplex(), featureFormat.isForHumans());

    for (String requestedProfile : requestedProfiles) {
      if (requestedProfile.startsWith(getPrefix())
          && supportedProfiles.contains(requestedProfile)) {
        return Optional.of(requestedProfile);
      }
    }

    return featureFormat
        .getDefaultProfile(getPrefix(), apiData, collectionId)
        .filter(supportedProfiles::contains)
        .or(
            () ->
                Optional.of(
                    getDefaultValue(featureFormat.isComplex(), featureFormat.isForHumans())));
  }

  public abstract List<String> getSupportedValues(boolean complex, boolean humanReadable);

  public abstract String getDefaultValue(boolean complex, boolean humanReadable);

  public abstract void addPropertyTransformations(
      @NotNull String value,
      @NotNull FeatureSchema schema,
      @NotNull String mediaType,
      @NotNull ImmutableProfileTransformations.Builder builder);
}
