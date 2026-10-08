/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg.migrations;

import de.ii.ogcapi.features.html.domain.FeaturesHtmlConfiguration;
import de.ii.ogcapi.features.html.domain.ImmutableFeaturesHtmlConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Removes the deprecated FEATURES_HTML option `geometryProperties`, which has no effect anymore.
 */
@SuppressWarnings("removal")
public class FeaturesHtmlMigrationV5
    extends OgcApiBuildingBlockMigration<FeaturesHtmlConfiguration> {

  public FeaturesHtmlMigrationV5(EntityMigrationContext context) {
    super(context, FeaturesHtmlConfiguration.class);
  }

  @Override
  public String getSubject() {
    return "building block FEATURES_HTML";
  }

  @Override
  public String getDescription() {
    return "uses the deprecated option geometryProperties, which has no effect anymore and will be removed";
  }

  @Override
  protected boolean hasDeprecatedOptions(FeaturesHtmlConfiguration configuration) {
    return Objects.nonNull(configuration.getGeometryProperties())
        && !configuration.getGeometryProperties().isEmpty();
  }

  @Override
  protected Optional<ExtensionConfiguration> upgrade(FeaturesHtmlConfiguration configuration) {
    return Optional.of(
        new ImmutableFeaturesHtmlConfiguration.Builder()
            .from(configuration)
            .geometryProperties((List<String>) null)
            .build());
  }
}
