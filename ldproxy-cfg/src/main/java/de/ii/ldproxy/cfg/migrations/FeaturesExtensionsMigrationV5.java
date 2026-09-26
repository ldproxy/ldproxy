/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg.migrations;

import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import java.util.Objects;
import java.util.Optional;

/**
 * Removes the deprecated building block FEATURES_EXTENSIONS. Its only capability, the `intersects`
 * query parameter, is superseded by ad-hoc queries of the building block SEARCH.
 */
public class FeaturesExtensionsMigrationV5
    extends OgcApiBuildingBlockMigration<ExtensionConfiguration> {

  static final String BUILDING_BLOCK = "FEATURES_EXTENSIONS";

  public FeaturesExtensionsMigrationV5(EntityMigrationContext context) {
    super(context, ExtensionConfiguration.class);
  }

  @Override
  public String getSubject() {
    return "building block " + BUILDING_BLOCK;
  }

  @Override
  public String getDescription() {
    return "is deprecated and will be removed, use ad-hoc queries (building block SEARCH) instead";
  }

  @Override
  protected boolean hasDeprecatedOptions(ExtensionConfiguration configuration) {
    return Objects.equals(configuration.getBuildingBlock(), BUILDING_BLOCK);
  }

  @Override
  protected Optional<ExtensionConfiguration> upgrade(ExtensionConfiguration configuration) {
    return Optional.empty();
  }
}
