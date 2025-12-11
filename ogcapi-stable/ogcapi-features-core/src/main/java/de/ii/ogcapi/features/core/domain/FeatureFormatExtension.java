/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.foundation.domain.ProfilesConfiguration;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.FeatureTokenEncoder;
import de.ii.xtraplatform.features.domain.profile.ImmutableProfileTransformations;
import de.ii.xtraplatform.features.domain.profile.ProfileTransformations;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformations;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoMultiBind
public abstract class FeatureFormatExtension implements FormatExtension {

  private static final Logger LOGGER = LoggerFactory.getLogger(FeatureFormatExtension.class);

  protected final ExtensionRegistry extensionRegistry;
  protected final FeaturesCoreProviders providers;
  private final Set<String> warned;

  protected FeatureFormatExtension(
      ExtensionRegistry extensionRegistry, FeaturesCoreProviders providers) {
    this.extensionRegistry = extensionRegistry;
    this.providers = providers;
    this.warned = new HashSet<>();
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    boolean enabled = FormatExtension.super.isEnabledForApi(apiData, collectionId);

    if (!enabled) {
      return false;
    }

    return checkRootConcat(apiData);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    boolean enabled = FormatExtension.super.isEnabledForApi(apiData);

    if (!enabled) {
      return false;
    }

    return checkRootConcat(apiData);
  }

  private boolean checkRootConcat(OgcApiDataV2 apiData) {
    if (supportsRootConcat()) {
      return true;
    }

    if (providers.hasAnyRootConcat(apiData)) {
      if (warned.add(this.getClass() + apiData.getId())) {
        LOGGER.warn(
            "{} does not support root concatenation in the feature schema, the format will be disabled.",
            getMediaType().label());
      }
      return false;
    }
    return true;
  }

  public abstract ApiMediaType getCollectionMediaType();

  public EpsgCrs getContentCrs(EpsgCrs targetCrs) {
    return targetCrs;
  }

  public ApiMediaTypeContent getFeatureContent(
      OgcApiDataV2 apiData, Optional<String> collectionId, boolean featureCollection) {
    return getContent();
  }

  public boolean isRestrictedToSimpleFeaturesGeometries(List<Profile> profiles) {
    return true;
  }

  public boolean canPassThroughFeatures() {
    return false;
  }

  public boolean canEncodeFeatures() {
    return false;
  }

  public boolean supportsEmbedding() {
    return false;
  }

  public Optional<FeatureTokenEncoder<?>> getFeatureEncoderPassThrough(
      FeatureTransformationContext transformationContext, Optional<Locale> language) {
    return Optional.empty();
  }

  public Optional<FeatureTokenEncoder<?>> getFeatureEncoder(
      FeatureTransformationContext transformationContext, Optional<Locale> language) {
    return Optional.empty();
  }

  public Optional<PropertyTransformations> getPropertyTransformations(
      FeatureTypeConfigurationOgcApi collectionData) {

    Optional<PropertyTransformations> coreTransformations =
        collectionData
            .getExtension(FeaturesCoreConfiguration.class)
            .map(featuresCoreConfiguration -> featuresCoreConfiguration);

    Optional<PropertyTransformations> formatTransformations =
        collectionData
            .getExtension(this.getBuildingBlockConfigurationType())
            .filter(
                buildingBlockConfiguration ->
                    buildingBlockConfiguration instanceof PropertyTransformations)
            .map(
                buildingBlockConfiguration ->
                    ((PropertyTransformations) buildingBlockConfiguration));

    return formatTransformations
        .map(ft -> coreTransformations.map(ft::mergeInto).orElse(ft))
        .or(() -> coreTransformations);
  }

  public Optional<PropertyTransformations> getPropertyTransformations(
      FeatureTypeConfigurationOgcApi collectionData,
      Optional<FeatureSchema> schema,
      List<Profile> profiles) {
    if (profiles.isEmpty() || schema.isEmpty()) {
      return getPropertyTransformations(collectionData);
    }

    ImmutableProfileTransformations.Builder builder = new ImmutableProfileTransformations.Builder();

    schema.ifPresent(
        s ->
            profiles.forEach(
                profile ->
                    profile.addPropertyTransformations(
                        s, getMediaType().type().toString(), builder)));

    ProfileTransformations profileTransformations = builder.build();

    return Optional.of(
        getPropertyTransformations(collectionData)
            .map(pts -> pts.mergeInto(profileTransformations))
            .orElse(profileTransformations));
  }

  public boolean supportsHitsOnly() {
    return false;
  }

  public boolean isComplex() {
    return false;
  }

  public boolean isForHumans() {
    return false;
  }

  public boolean supportsRootConcat() {
    return false;
  }

  public boolean requiresPropertiesInSequence(FeatureSchema schema) {
    return false;
  }

  public boolean supportsSecondaryGeometry() {
    return false;
  }

  public boolean supportsNullVsMissing() {
    return false;
  }

  public Map<String, String> getDefaultProfiles(OgcApiDataV2 apiData, String collectionId) {
    return apiData
        .getExtension(getBuildingBlockConfigurationType(), collectionId)
        .filter(
            buildingBlockConfiguration ->
                buildingBlockConfiguration instanceof ProfilesConfiguration)
        .map(
            buildingBlockConfiguration ->
                ((ProfilesConfiguration) buildingBlockConfiguration).getDefaultProfiles())
        .orElse(Map.of());
  }

  public final Optional<String> getDefaultProfile(
      String prefix, OgcApiDataV2 apiData, String collectionId) {
    return Optional.ofNullable(getDefaultProfiles(apiData, collectionId).get(prefix));
  }
}
