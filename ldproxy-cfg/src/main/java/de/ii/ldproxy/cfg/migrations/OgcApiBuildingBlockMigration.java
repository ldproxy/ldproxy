/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg.migrations;

import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.entities.domain.EntityData;
import de.ii.xtraplatform.entities.domain.EntityMigration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Base class for migrations of deprecated options in a building block configuration. The
 * configuration is inspected and upgraded at the API level and at the collection level. Defaults
 * are not considered, only the options that are set in the entity itself are migrated.
 *
 * @param <T> the building block configuration type
 */
public abstract class OgcApiBuildingBlockMigration<T extends ExtensionConfiguration>
    extends EntityMigration<OgcApiDataV2, OgcApiDataV2> {

  private final Class<T> configurationType;

  protected OgcApiBuildingBlockMigration(
      EntityMigrationContext context, Class<T> configurationType) {
    super(context);
    this.configurationType = configurationType;
  }

  /**
   * @param configuration a configuration of the building block
   * @return true, if the configuration uses deprecated options
   */
  protected abstract boolean hasDeprecatedOptions(T configuration);

  /**
   * @param configuration a configuration of the building block that uses deprecated options
   * @return the upgraded configuration, or empty if the building block should be removed
   */
  protected abstract Optional<ExtensionConfiguration> upgrade(T configuration);

  @Override
  public boolean isApplicable(EntityData entityData, Optional<EntityData> defaults) {
    if (!(entityData instanceof OgcApiDataV2)) {
      return false;
    }

    OgcApiDataV2 apiData = (OgcApiDataV2) entityData;

    return hasDeprecatedExtensions(apiData.getExtensions())
        || apiData.getCollections().values().stream()
            .anyMatch(collection -> hasDeprecatedExtensions(collection.getExtensions()));
  }

  @Override
  public OgcApiDataV2 migrate(OgcApiDataV2 entityData, Optional<OgcApiDataV2> defaults) {
    return new ImmutableOgcApiDataV2.Builder()
        .from(entityData)
        .extensions(upgradeExtensions(entityData.getExtensions()))
        .collections(
            entityData.getCollections().entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), upgradeCollection(entry.getValue())))
                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue)))
        .build();
  }

  private boolean hasDeprecatedExtensions(List<ExtensionConfiguration> extensions) {
    return extensions.stream().anyMatch(this::isDeprecated);
  }

  private boolean isDeprecated(ExtensionConfiguration extension) {
    return configurationType.isInstance(extension)
        && hasDeprecatedOptions(configurationType.cast(extension));
  }

  private List<ExtensionConfiguration> upgradeExtensions(List<ExtensionConfiguration> extensions) {
    return extensions.stream()
        .flatMap(
            extension ->
                isDeprecated(extension)
                    ? upgrade(configurationType.cast(extension)).stream()
                    : Stream.of(extension))
        .toList();
  }

  private FeatureTypeConfigurationOgcApi upgradeCollection(
      FeatureTypeConfigurationOgcApi collection) {
    if (!hasDeprecatedExtensions(collection.getExtensions())) {
      return collection;
    }

    return new ImmutableFeatureTypeConfigurationOgcApi.Builder()
        .from(collection)
        .extensions(upgradeExtensions(collection.getExtensions()))
        .build();
  }
}
