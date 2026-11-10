/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.foundation.domain.CollectionExtent;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.features.domain.Extent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The deprecated options `defaultExtent` of an API and `extent` of a collection are used for the
 * option `extent` of FEATURES_CORE of the API or the collection, unless that is set.
 */
@SuppressWarnings({"deprecation", "removal"})
public final class DeprecatedExtents {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeprecatedExtents.class);

  private DeprecatedExtents() {}

  /**
   * @param apiData the API configuration
   * @return the collections that use the deprecated option `extent`
   */
  public static List<String> getCollectionsWithDeprecatedExtent(OgcApiDataV2 apiData) {
    return apiData.getCollections().entrySet().stream()
        .filter(entry -> entry.getValue().getExtent().isPresent())
        .map(Map.Entry::getKey)
        .toList();
  }

  /**
   * @param apiData the API configuration
   * @return true, if the API or a collection uses a deprecated extent option
   */
  public static boolean isPresent(OgcApiDataV2 apiData) {
    return apiData.getDefaultExtent().isPresent()
        || !getCollectionsWithDeprecatedExtent(apiData).isEmpty();
  }

  /**
   * Uses the values of the deprecated options for the option `extent` of FEATURES_CORE. A value
   * that cannot be converted, e.g. with an end before the start, is logged and ignored.
   *
   * @param apiData the API configuration
   * @param removeDeprecated whether the deprecated options are removed (upgrade of a configuration)
   *     or kept (at runtime, so that the upgrade can still detect them)
   * @return the API configuration with the converted extents
   */
  public static OgcApiDataV2 apply(OgcApiDataV2 apiData, boolean removeDeprecated) {
    ImmutableOgcApiDataV2.Builder builder = new ImmutableOgcApiDataV2.Builder().from(apiData);

    if (apiData.getDefaultExtent().isPresent()) {
      Optional<List<ExtensionConfiguration>> extensions =
          withExtent(apiData.getExtensions(), apiData.getDefaultExtent().get(), "the API");
      extensions.ifPresent(builder::extensions);
      if (removeDeprecated && extensions.isPresent()) {
        builder.defaultExtent(Optional.empty());
      }
    }

    builder.collections(
        apiData.getCollections().entrySet().stream()
            .map(
                entry ->
                    Map.entry(
                        entry.getKey(), apply(entry.getKey(), entry.getValue(), removeDeprecated)))
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue)));

    return builder.build();
  }

  private static FeatureTypeConfigurationOgcApi apply(
      String collectionId, FeatureTypeConfigurationOgcApi collection, boolean removeDeprecated) {
    if (collection.getExtent().isEmpty()) {
      return collection;
    }

    Optional<List<ExtensionConfiguration>> extensions =
        withExtent(
            collection.getExtensions(),
            collection.getExtent().get(),
            String.format("collection '%s'", collectionId));
    if (extensions.isEmpty()) {
      return collection;
    }

    ImmutableFeatureTypeConfigurationOgcApi.Builder builder =
        new ImmutableFeatureTypeConfigurationOgcApi.Builder()
            .from(collection)
            .extensions(extensions.get());
    if (removeDeprecated) {
      builder.extent(Optional.empty());
    }
    return builder.build();
  }

  /**
   * @return the extensions with the converted extent in FEATURES_CORE, or empty if the deprecated
   *     extent cannot be converted
   */
  private static Optional<List<ExtensionConfiguration>> withExtent(
      List<ExtensionConfiguration> extensions, CollectionExtent deprecated, String context) {
    Optional<FeaturesCoreConfiguration> coreConfig =
        extensions.stream()
            .filter(FeaturesCoreConfiguration.class::isInstance)
            .map(FeaturesCoreConfiguration.class::cast)
            .findFirst();

    if (coreConfig.flatMap(FeaturesCoreConfiguration::getExtent).isPresent()) {
      return Optional.of(extensions);
    }

    Extent extent;
    try {
      extent = deprecated.toExtent();
    } catch (IllegalStateException e) {
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error("The deprecated extent of {} is ignored: {}", context, e.getMessage());
      }
      return Optional.empty();
    }

    if (extent.getSpatial().isEmpty() && extent.getTemporal().isEmpty()) {
      return Optional.of(extensions);
    }

    ImmutableFeaturesCoreConfiguration.Builder builder =
        new ImmutableFeaturesCoreConfiguration.Builder();
    coreConfig.ifPresent(builder::from);
    FeaturesCoreConfiguration withExtent = builder.extent(extent).build();

    if (coreConfig.isEmpty()) {
      return Optional.of(
          new ImmutableList.Builder<ExtensionConfiguration>()
              .addAll(extensions)
              .add(withExtent)
              .build());
    }

    return Optional.of(
        extensions.stream()
            .map(
                extension ->
                    extension instanceof FeaturesCoreConfiguration
                        ? (ExtensionConfiguration) withExtent
                        : extension)
            .toList());
  }
}
