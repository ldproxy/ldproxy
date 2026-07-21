/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import de.ii.ogcapi.foundation.domain.AliasConfiguration;
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
import de.ii.xtraplatform.features.domain.FeatureSchemaAliases;
import de.ii.xtraplatform.features.domain.FeatureTokenEncoder;
import de.ii.xtraplatform.features.domain.SchemaMapping;
import de.ii.xtraplatform.features.domain.pipeline.FeatureEventHandlerSimple.ModifiableContext;
import de.ii.xtraplatform.features.domain.pipeline.FeatureTokenDecoderSimple;
import de.ii.xtraplatform.features.domain.profile.ImmutableProfileTransformations;
import de.ii.xtraplatform.features.domain.profile.ProfileTransformations;
import de.ii.xtraplatform.features.domain.transform.ImmutablePropertyTransformation;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformation;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformations;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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

  public Optional<
          FeatureTokenDecoderSimple<
              byte[],
              FeatureSchema,
              SchemaMapping,
              ModifiableContext<FeatureSchema, SchemaMapping>>>
      getFeatureDecoder(DecoderContext decoderContext) {
    return Optional.empty();
  }

  public void validate(String content, ValidatorContext ctx) {}

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
    Optional<PropertyTransformations> result;
    if (profiles.isEmpty() || schema.isEmpty()) {
      result = getPropertyTransformations(collectionData);
    } else {
      ImmutableProfileTransformations.Builder builder =
          new ImmutableProfileTransformations.Builder();

      schema.ifPresent(
          s ->
              profiles.forEach(
                  profile ->
                      profile.addPropertyTransformations(
                          s, getMediaType().type().toString(), builder)));

      ProfileTransformations profileTransformations = builder.build();

      result =
          Optional.of(
              getPropertyTransformations(collectionData)
                  .map(pts -> pts.mergeInto(profileTransformations))
                  .orElse(profileTransformations));
    }

    if (schema.isPresent() && !supportsInternalProperties(profiles)) {
      Optional<PropertyTransformations> internalRemoves = internalPropertyRemoves(schema.get());
      if (internalRemoves.isPresent()) {
        result = result.map(pts -> internalRemoves.get().mergeInto(pts)).or(() -> internalRemoves);
      }
    }

    if (schema.isPresent() && isUseAlias(collectionData)) {
      FeatureSchema s = schema.get();
      result = result.map(pts -> FeatureSchemaAliases.injectAliasRenames(pts, s));
    }

    return result;
  }

  /**
   * Whether the format handles {@code internal} properties itself for the negotiated profiles (for
   * example, GML, which encodes position variants from internal properties and suppresses them in
   * its writers; or GeoJSON with the JSON-FG extensions and the {@code crs-original} profile).
   * Otherwise an implicit {@code remove: ALWAYS} transformation is added for every internal
   * property, so internal properties never appear in feature representations.
   */
  public boolean supportsInternalProperties(List<Profile> profiles) {
    return false;
  }

  private static Optional<PropertyTransformations> internalPropertyRemoves(FeatureSchema schema) {
    Map<String, List<PropertyTransformation>> removes =
        schema.getAllNestedProperties().stream()
            .filter(FeatureSchema::isInternal)
            .collect(
                Collectors.toMap(
                    FeatureSchema::getFullPathAsString,
                    property ->
                        List.of(
                            new ImmutablePropertyTransformation.Builder().remove("ALWAYS").build()),
                    (first, second) -> first));

    if (removes.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(() -> removes);
  }

  private boolean isUseAlias(FeatureTypeConfigurationOgcApi collectionData) {
    return collectionData
        .getExtension(getBuildingBlockConfigurationType())
        .filter(c -> c instanceof AliasConfiguration)
        .map(c -> ((AliasConfiguration) c).isUseAlias())
        .orElse(false);
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

  /**
   * Whether this format can represent a feature collection that mixes features of more than one
   * feature type / collection in a single response (as produced by the <em>Search</em> building
   * block). Formats with a fixed, single-schema output (e.g. CSV, FlatGeobuf, CityJSON, glTF)
   * cannot and return {@code false} (the default); they are excluded from endpoints that produce
   * heterogeneous responses. Formats whose structure carries the type per feature (GeoJSON, GML,
   * HTML) return {@code true}.
   */
  public boolean supportsHeterogeneousFeatureCollections() {
    return false;
  }

  public boolean requiresPropertiesInSequence(FeatureSchema schema) {
    return false;
  }

  public boolean supportsSecondaryGeometry(List<Profile> profiles) {
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
