/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg.migrations

import de.ii.xtraplatform.entities.domain.EntityMigration.EntityMigrationContext
import de.ii.xtraplatform.features.sql.domain.FeatureProviderSqlData
import de.ii.xtraplatform.features.sql.domain.FeatureProviderSqlData.DatasetChangeMode
import de.ii.xtraplatform.features.sql.domain.ImmutableConnectionInfoSql
import de.ii.xtraplatform.features.sql.domain.ImmutableDatasetChangeSettings
import de.ii.xtraplatform.features.sql.domain.ImmutableFeatureProviderSqlData
import de.ii.xtraplatform.tiles.domain.ImmutableSeedingOptions
import de.ii.xtraplatform.tiles.domain.ImmutableTileProviderFeaturesData
import de.ii.xtraplatform.tiles.domain.TileProviderFeaturesData
import spock.lang.Specification

class ProviderMigrationsV5Spec extends Specification {

    static final EntityMigrationContext CONTEXT = { matcher -> false } as EntityMigrationContext

    static ImmutableFeatureProviderSqlData.Builder sqlProvider(boolean assumeExternalChanges) {
        return new ImmutableFeatureProviderSqlData.Builder()
                .id("provider")
                .providerType("FEATURE")
                .providerSubType("SQL")
                .connectionInfo(new ImmutableConnectionInfoSql.Builder()
                        .database("db")
                        .assumeExternalChanges(assumeExternalChanges)
                        .build())
    }

    def "SQL: assumeExternalChanges is replaced by datasetChanges.mode EXTERNAL"() {
        given:
        FeatureProviderSqlData data = sqlProvider(true).build()
        FeatureProviderSqlMigrationV5 migration = new FeatureProviderSqlMigrationV5(CONTEXT)

        expect:
        data.getDatasetChanges().getMode() == DatasetChangeMode.EXTERNAL
        data.getConnectionInfo().getAssumeExternalChanges()
        migration.isApplicable(data, Optional.empty())

        when:
        FeatureProviderSqlData migrated = migration.migrate(data, Optional.empty())

        then:
        !migrated.getConnectionInfo().getAssumeExternalChanges()
        migrated.getConnectionInfo().getDatabase() == "db"
        migrated.getDatasetChanges().getMode() == DatasetChangeMode.EXTERNAL
        !migration.isApplicable(migrated, Optional.empty())
    }

    def "SQL: an explicit datasetChanges.mode is kept"() {
        given:
        FeatureProviderSqlData data = sqlProvider(true)
                .datasetChanges(new ImmutableDatasetChangeSettings.Builder().mode(DatasetChangeMode.CRUD).build())
                .build()

        when:
        FeatureProviderSqlData migrated = new FeatureProviderSqlMigrationV5(CONTEXT).migrate(data, Optional.empty())

        then:
        !migrated.getConnectionInfo().getAssumeExternalChanges()
        migrated.getDatasetChanges().getMode() == DatasetChangeMode.CRUD
    }

    def "SQL: a provider without assumeExternalChanges is not applicable"() {
        expect:
        !new FeatureProviderSqlMigrationV5(CONTEXT).isApplicable(sqlProvider(false).build(), Optional.empty())
    }

    def "TILE: seeding.maxThreads is removed"() {
        given:
        TileProviderFeaturesData data = new ImmutableTileProviderFeaturesData.Builder()
                .id("tiles")
                .providerType("TILE")
                .providerSubType("FEATURES")
                .seeding(new ImmutableSeedingOptions.Builder().maxThreads(4).runOnStartup(false).build())
                .build()
        TileProviderFeaturesMigrationV5 migration = new TileProviderFeaturesMigrationV5(CONTEXT)

        expect:
        migration.isApplicable(data, Optional.empty())

        when:
        TileProviderFeaturesData migrated = migration.migrate(data, Optional.empty())

        then:
        migrated.getSeeding().isPresent()
        migrated.getSeeding().get().getMaxThreads() == null
        migrated.getSeeding().get().getRunOnStartup() == false
        !migration.isApplicable(migrated, Optional.empty())
    }

    def "TILE: a provider without seeding.maxThreads is not applicable"() {
        given:
        TileProviderFeaturesMigrationV5 migration = new TileProviderFeaturesMigrationV5(CONTEXT)

        expect:
        !migration.isApplicable(new ImmutableTileProviderFeaturesData.Builder().id("tiles").providerType("TILE").providerSubType("FEATURES").build(), Optional.empty())
        !migration.isApplicable(new ImmutableTileProviderFeaturesData.Builder()
                .id("tiles")
                .providerType("TILE")
                .providerSubType("FEATURES")
                .seeding(new ImmutableSeedingOptions.Builder().runOnStartup(false).build())
                .build(), Optional.empty())
    }
}
