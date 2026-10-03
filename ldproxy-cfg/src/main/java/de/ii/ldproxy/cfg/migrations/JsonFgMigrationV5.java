/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg.migrations;

import de.ii.ogcapi.features.jsonfg.domain.ImmutableJsonFgConfiguration;
import de.ii.ogcapi.features.jsonfg.domain.JsonFgConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Replaces the deprecated JSON-FG options `geojsonCompatibility` and `featureType` with
 * `supportPlusProfile` and `featureTypeV1`.
 */
@SuppressWarnings("removal")
public class JsonFgMigrationV5 extends OgcApiBuildingBlockMigration<JsonFgConfiguration> {

  public JsonFgMigrationV5(EntityMigrationContext context) {
    super(context, JsonFgConfiguration.class);
  }

  @Override
  public String getSubject() {
    return "building block JSON_FG";
  }

  @Override
  public String getDescription() {
    return "uses deprecated options that will be replaced: geojsonCompatibility by supportPlusProfile, featureType by featureTypeV1";
  }

  @Override
  protected boolean hasDeprecatedOptions(JsonFgConfiguration configuration) {
    return Objects.nonNull(configuration.getGeojsonCompatibility())
        || hasFeatureType(configuration);
  }

  @Override
  protected Optional<ExtensionConfiguration> upgrade(JsonFgConfiguration configuration) {
    return Optional.of(
        new ImmutableJsonFgConfiguration.Builder()
            .from(configuration)
            .supportPlusProfile(
                Objects.nonNull(configuration.getSupportPlusProfile())
                    ? configuration.getSupportPlusProfile()
                    : configuration.getGeojsonCompatibility())
            .geojsonCompatibility(null)
            .featureTypeV1(
                Objects.nonNull(configuration.getFeatureTypeV1())
                    ? configuration.getFeatureTypeV1()
                    : hasFeatureType(configuration) ? configuration.getFeatureType().get(0) : null)
            .featureType((List<String>) null)
            .build());
  }

  private static boolean hasFeatureType(JsonFgConfiguration configuration) {
    return Objects.nonNull(configuration.getFeatureType())
        && !configuration.getFeatureType().isEmpty();
  }
}
