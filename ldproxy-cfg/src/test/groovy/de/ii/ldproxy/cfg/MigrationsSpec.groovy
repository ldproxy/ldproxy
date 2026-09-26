/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg

import de.ii.ldproxy.cfg.migrations.FeatureProviderSqlMigrationV5
import de.ii.ldproxy.cfg.migrations.FeaturesExtensionsMigrationV5
import de.ii.ldproxy.cfg.migrations.FeaturesHtmlMigrationV5
import de.ii.ldproxy.cfg.ValueMigration.ValueMigrationContext
import de.ii.ldproxy.cfg.migrations.JsonFgMigrationV5
import de.ii.ldproxy.cfg.migrations.StoredQueryMigrationV5
import de.ii.ldproxy.cfg.migrations.TileProviderFeaturesMigrationV5
import de.ii.ogcapi.tiles3d.domain.Tiles3dMigrationV5
import de.ii.xtraplatform.entities.domain.EntityDataStore
import spock.lang.Specification

class MigrationsSpec extends Specification {

    static final ValueMigrationContext VALUE_CONTEXT = { type, name -> false } as ValueMigrationContext

    def "all v5 entity migrations are registered"() {
        given:
        EntityDataStore store = Stub(EntityDataStore)

        when:
        List<Class> registered = Migrations.create(store, VALUE_CONTEXT).entity()*.getClass()

        then:
        registered.containsAll([
                Tiles3dMigrationV5,
                JsonFgMigrationV5,
                FeaturesHtmlMigrationV5,
                FeaturesExtensionsMigrationV5,
                FeatureProviderSqlMigrationV5,
                TileProviderFeaturesMigrationV5
        ])
        registered.size() == 6
    }

    def "all v5 value migrations are registered"() {
        given:
        EntityDataStore store = Stub(EntityDataStore)

        when:
        List<ValueMigration> registered = Migrations.create(store, VALUE_CONTEXT).values()

        then:
        registered*.getClass() == [StoredQueryMigrationV5]
        registered*.getValueType() == ["queries"]
    }
}
