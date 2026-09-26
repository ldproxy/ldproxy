/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg.migrations;

import de.ii.xtraplatform.entities.domain.EntityData;
import de.ii.xtraplatform.entities.domain.EntityMigration;
import de.ii.xtraplatform.tiles.domain.ImmutableSeedingOptions;
import de.ii.xtraplatform.tiles.domain.ImmutableTileProviderFeaturesData;
import de.ii.xtraplatform.tiles.domain.SeedingOptions;
import de.ii.xtraplatform.tiles.domain.TileProviderFeaturesData;
import java.util.Objects;
import java.util.Optional;

/**
 * Removes the deprecated tile provider option `seeding.maxThreads`, which has no effect anymore.
 * The deprecated cache storage types `PLAIN` and `MBTILES` are converted when the configuration is
 * loaded and need no migration.
 */
@SuppressWarnings("removal")
public class TileProviderFeaturesMigrationV5
    extends EntityMigration<TileProviderFeaturesData, TileProviderFeaturesData> {

  public TileProviderFeaturesMigrationV5(EntityMigrationContext context) {
    super(context);
  }

  @Override
  public String getSubject() {
    return "tile provider option seeding.maxThreads";
  }

  @Override
  public String getDescription() {
    return "is deprecated, has no effect anymore and will be removed";
  }

  @Override
  public boolean isApplicable(EntityData entityData, Optional<EntityData> defaults) {
    return entityData instanceof TileProviderFeaturesData
        && ((TileProviderFeaturesData) entityData)
            .getSeeding()
            .map(seeding -> Objects.nonNull(seeding.getMaxThreads()))
            .orElse(false);
  }

  @Override
  public TileProviderFeaturesData migrate(
      TileProviderFeaturesData entityData, Optional<TileProviderFeaturesData> defaults) {
    Optional<SeedingOptions> seeding =
        entityData
            .getSeeding()
            .map(
                options ->
                    new ImmutableSeedingOptions.Builder().from(options).maxThreads(null).build());

    return new ImmutableTileProviderFeaturesData.Builder()
        .from(entityData)
        .seeding(seeding)
        .build();
  }
}
