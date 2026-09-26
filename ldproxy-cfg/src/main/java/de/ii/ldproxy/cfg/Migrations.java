/*
 * Copyright 2023 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg;

import de.ii.ldproxy.cfg.ValueMigration.ValueMigrationContext;
import de.ii.ldproxy.cfg.migrations.FeatureProviderSqlMigrationV5;
import de.ii.ldproxy.cfg.migrations.FeaturesExtensionsMigrationV5;
import de.ii.ldproxy.cfg.migrations.FeaturesHtmlMigrationV5;
import de.ii.ldproxy.cfg.migrations.JsonFgMigrationV5;
import de.ii.ldproxy.cfg.migrations.StoredQueryMigrationV5;
import de.ii.ldproxy.cfg.migrations.TileProviderFeaturesMigrationV5;
import de.ii.ogcapi.tiles3d.domain.Tiles3dMigrationV5;
import de.ii.xtraplatform.entities.domain.EntityDataStore;
import de.ii.xtraplatform.entities.domain.EntityMigration;
import de.ii.xtraplatform.entities.domain.EntityMigration.EntityMigrationContext;
import java.util.List;

public interface Migrations {

  static Migrations create(
      EntityDataStore<?> entityDataStore, ValueMigrationContext valueMigrationContext) {
    EntityMigrationContext context = entityDataStore::has;

    List<EntityMigration<?, ?>> entityMigrations =
        List.of(
            new Tiles3dMigrationV5(context),
            new JsonFgMigrationV5(context),
            new FeaturesHtmlMigrationV5(context),
            new FeaturesExtensionsMigrationV5(context),
            new FeatureProviderSqlMigrationV5(context),
            new TileProviderFeaturesMigrationV5(context));
    List<ValueMigration> valueMigrations =
        List.of(new StoredQueryMigrationV5(valueMigrationContext));

    return new Migrations() {
      @Override
      public List<EntityMigration<?, ?>> entity() {
        return entityMigrations;
      }

      @Override
      public List<ValueMigration> values() {
        return valueMigrations;
      }
    };
  }

  /**
   * @return the migrations of entities (providers, services)
   */
  List<EntityMigration<?, ?>> entity();

  /**
   * @return the migrations of values (e.g. stored queries), see {@link
   *     ValueMigration#getValueType()} for the value type a migration applies to
   */
  List<ValueMigration> values();
}
