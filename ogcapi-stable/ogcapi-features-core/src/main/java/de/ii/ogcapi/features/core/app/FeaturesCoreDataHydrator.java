/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.ImmutableApiMetadata;
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiDataHydratorExtension;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.Metadata;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class FeaturesCoreDataHydrator implements OgcApiDataHydratorExtension {

  private static final Logger LOGGER = LoggerFactory.getLogger(FeaturesCoreDataHydrator.class);

  private final FeaturesCoreProviders providers;

  @Inject
  public FeaturesCoreDataHydrator(FeaturesCoreProviders providers) {
    this.providers = providers;
  }

  @Override
  public int getSortPriority() {
    // this must be processed before any other hydrator that works with collections
    return 100;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return FeaturesCoreConfiguration.class;
  }

  @Override
  public OgcApiDataV2 getHydratedData(OgcApiDataV2 apiData) {
    FeatureProvider featureProvider = providers.getFeatureProviderOrThrow(apiData);

    OgcApiDataV2 data = apiData;
    if (data.isAuto() && data.getCollections().isEmpty()) {
      data =
          new ImmutableOgcApiDataV2.Builder()
              .from(data)
              .collections(generateCollections(featureProvider))
              .build();
    }

    Map<String, FeatureSchema> featureSchemas = providers.getFeatureSchemas(data);
    MODE apiValidation = data.getApiValidation();

    // The behaviour depends on the requested validation approach
    // NONE: no validation during hydration
    // LAX: try to remove invalid options, but start the service with the valid options, if possible
    // STRICT: no validation during hydration, validation will be done in onStartup() and startup
    // will fail in case of an error

    // get Features Core configurations to process, normalize property names unless in STRICT mode
    Map<String, FeaturesCoreConfiguration> coreConfigs =
        data.getCollections().entrySet().stream()
            .map(
                entry -> {
                  // normalize the property references in queryables and transformations
                  // by removing all parts in square brackets unless in STRICT mode

                  final FeatureTypeConfigurationOgcApi collectionData = entry.getValue();
                  // cannot use collectionData.getExtension(FeaturesCoreConfiguration.class), it
                  // merges in the defaults, we don't want that in auto mode
                  FeaturesCoreConfiguration config =
                      collectionData.getExtensions().stream()
                          .filter(
                              extensionConfiguration ->
                                  extensionConfiguration instanceof FeaturesCoreConfiguration)
                          .map(
                              extensionConfiguration ->
                                  (FeaturesCoreConfiguration) extensionConfiguration)
                          .findFirst()
                          .orElse(null);

                  if (Objects.isNull(config)) return null;

                  return new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), config);
                })
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    // update data with changes
    // also disable invalid collections where either the feature type is not found or has no
    // property with role id
    // also update label and description, if we have information in the provider
    data =
        new ImmutableOgcApiDataV2.Builder()
            .from(data)
            .collections(
                data.getCollections().entrySet().stream()
                    .map(
                        entry -> {
                          final String collectionId = entry.getKey();
                          final Optional<FeaturesCoreConfiguration> coreConfiguration =
                              Optional.ofNullable(coreConfigs.get(collectionId));
                          final FeatureTypeConfigurationOgcApi collectionData = entry.getValue();
                          final FeatureSchema schema = featureSchemas.get(collectionId);

                          final boolean featureTypeNotFound = Objects.isNull(schema);
                          final boolean idNotFound =
                              !featureTypeNotFound
                                  && schema.getAllNestedProperties().stream()
                                      .noneMatch(FeatureSchema::isId);
                          final boolean disabled = featureTypeNotFound || idNotFound;

                          if (featureTypeNotFound) {
                            LOGGER.error(
                                "Collection '{}' has been disabled because its feature type was not found in the provider schema.",
                                collectionId);
                          }
                          if (idNotFound) {
                            LOGGER.error(
                                "Collection '{}' has been disabled because its feature type has no property with role ID in the provider schema.",
                                collectionId);
                          }

                          return new AbstractMap.SimpleImmutableEntry<
                              String, FeatureTypeConfigurationOgcApi>(
                              collectionId,
                              new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                                  .from(entry.getValue())
                                  // disable if feature type or id not found
                                  .enabled(collectionData.getEnabled() && !disabled)
                                  // use the type label from the provider, if the service
                                  // configuration has just the default label
                                  .label(
                                      collectionData.getLabel().equals(collectionId)
                                              && Objects.nonNull(schema)
                                              && schema.getLabel().isPresent()
                                          ? schema.getLabel().get()
                                          : collectionData.getLabel())
                                  // use the type description from the provider, if the service
                                  // configuration does not have one
                                  .description(
                                      Optional.ofNullable(
                                          collectionData
                                              .getDescription()
                                              .orElse(
                                                  Objects.nonNull(schema)
                                                      ? schema.getDescription().orElse(null)
                                                      : null)))
                                  .extensions(
                                      coreConfiguration.isPresent()
                                          ? new ImmutableList.Builder<ExtensionConfiguration>()
                                              // do not touch any other extension
                                              .addAll(
                                                  entry.getValue().getExtensions().stream()
                                                      .filter(
                                                          ext ->
                                                              !ext.getBuildingBlock()
                                                                  .equals(
                                                                      coreConfiguration
                                                                          .get()
                                                                          .getBuildingBlock()))
                                                      .collect(Collectors.toUnmodifiableList()))
                                              // add the modified Features Core configuration
                                              .add(coreConfiguration.get())
                                              .build()
                                          : entry.getValue().getExtensions())
                                  .build());
                        })
                    .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue)))
            .build();

    if (data.isAuto()) {
      data = new ImmutableOgcApiDataV2.Builder().from(data).auto(Optional.empty()).build();
    }

    if (!data.getMetadata().isPresent() && featureProvider.metadata().isAvailable()) {
      Optional<Metadata> providerMetadata = featureProvider.metadata().get().getMetadata();
      if (providerMetadata.isPresent()) {
        Optional<String> license =
            providerMetadata.flatMap(Metadata::getAccessConstraints).isPresent()
                ? providerMetadata.flatMap(Metadata::getAccessConstraints)
                : providerMetadata.flatMap(Metadata::getFees);

        ImmutableApiMetadata metadata =
            new ImmutableApiMetadata.Builder()
                .addAllKeywords(providerMetadata.get().getKeywords())
                .contactName(providerMetadata.get().getContactName())
                .contactUrl(providerMetadata.get().getContactUrl())
                .contactEmail(providerMetadata.get().getContactEmail())
                .license(license)
                .build();

        String label =
            Objects.equals(data.getId(), data.getLabel())
                    && providerMetadata.flatMap(Metadata::getLabel).isPresent()
                ? providerMetadata.flatMap(Metadata::getLabel).get()
                : data.getLabel();

        Optional<String> description =
            data.getDescription().isPresent()
                ? data.getDescription()
                : providerMetadata.flatMap(Metadata::getDescription);

        data =
            new ImmutableOgcApiDataV2.Builder()
                .from(data)
                .label(label)
                .description(description)
                .metadata(metadata)
                .build();
      }
    }

    return data;
  }

  private Map<String, FeatureTypeConfigurationOgcApi> generateCollections(
      FeatureProvider featureProvider) {
    return featureProvider.info().getSchemas().stream()
        .map(
            type -> {
              ImmutableFeatureTypeConfigurationOgcApi collection =
                  new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                      .id(type.getName())
                      .label(type.getName())
                      .build();

              return new AbstractMap.SimpleImmutableEntry<>(type.getName(), collection);
            })
        .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
