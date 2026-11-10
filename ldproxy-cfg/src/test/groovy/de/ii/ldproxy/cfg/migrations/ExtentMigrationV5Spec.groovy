/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg.migrations

import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration
import de.ii.ogcapi.features.core.domain.ImmutableFeaturesCoreConfiguration
import de.ii.ogcapi.foundation.domain.CollectionExtent
import de.ii.ogcapi.foundation.domain.ImmutableCollectionExtent
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.foundation.domain.TemporalExtent
import de.ii.xtraplatform.crs.domain.BoundingBox
import de.ii.xtraplatform.crs.domain.OgcCrs
import de.ii.xtraplatform.entities.domain.EntityMigration.EntityMigrationContext
import de.ii.xtraplatform.features.domain.Extent
import de.ii.xtraplatform.features.domain.ImmutableExtent
import de.ii.xtraplatform.features.domain.ImmutableSpatialExtent
import spock.lang.Specification

class ExtentMigrationV5Spec extends Specification {

    static final EntityMigrationContext CONTEXT = { matcher -> false } as EntityMigrationContext

    def "defaultExtent and the extent of a collection are moved to FEATURES_CORE"() {

        given:
        OgcApiDataV2 data = new ImmutableOgcApiDataV2.Builder()
                .id("api")
                .defaultExtent(new ImmutableCollectionExtent.Builder()
                        .spatial(BoundingBox.of(5.0, 47.0, 15.0, 55.0, OgcCrs.CRS84))
                        .temporal(TemporalExtent.of("1262304000000", null))
                        .build())
                .addExtensions(new ImmutableFeaturesCoreConfiguration.Builder().enabled(true).build())
                .putCollections("c1", collection("c1", new ImmutableCollectionExtent.Builder()
                        .spatial(BoundingBox.of(7.0, 50.0, 7.1, 50.1, OgcCrs.CRS84))
                        .spatialComputed(false)
                        .build()))
                .putCollections("c2", collection("c2", new ImmutableCollectionExtent.Builder()
                        .spatialComputed(false)
                        .build()))
                .build()
        def migration = new ExtentMigrationV5(CONTEXT)

        expect:
        migration.isApplicable(data, Optional.empty())

        when:
        OgcApiDataV2 migrated = migration.migrate(data, Optional.empty())

        then: 'the API keeps its other FEATURES_CORE options'
        def apiCore = core(migrated.getExtensions())
        apiCore.getEnabled()
        apiCore.getExtent().flatMap(Extent::getSpatial) == Optional.of(spatial(5.0, 47.0, 15.0, 55.0))
        apiCore.getExtent().flatMap(Extent::getTemporal).map { it.start } == Optional.of("2010-01-01T00:00:00Z")
        migrated.getDefaultExtent().isEmpty()

        and: 'the collection gets a FEATURES_CORE configuration with the extent'
        core(migrated.getCollections().get("c1").getExtensions()).getExtent().flatMap(Extent::getSpatial) == Optional.of(spatial(7.0, 50.0, 7.1, 50.1))
        migrated.getCollections().get("c1").getExtent().isEmpty()

        and: 'an extent with just the computed flags is removed without a replacement'
        migrated.getCollections().get("c2").getExtensions().isEmpty()
        migrated.getCollections().get("c2").getExtent().isEmpty()

        and:
        !migration.isApplicable(migrated, Optional.empty())
    }

    def "an extent in FEATURES_CORE wins over the deprecated option"() {

        given:
        def own = ImmutableExtent.builder().spatial(spatial(6.0, 49.0, 8.0, 51.0)).build()
        OgcApiDataV2 data = new ImmutableOgcApiDataV2.Builder()
                .id("api")
                .defaultExtent(new ImmutableCollectionExtent.Builder()
                        .spatial(BoundingBox.of(5.0, 47.0, 15.0, 55.0, OgcCrs.CRS84))
                        .build())
                .addExtensions(new ImmutableFeaturesCoreConfiguration.Builder().extent(own).build())
                .build()

        when:
        OgcApiDataV2 migrated = new ExtentMigrationV5(CONTEXT).migrate(data, Optional.empty())

        then:
        core(migrated.getExtensions()).getExtent() == Optional.of(own)
        migrated.getDefaultExtent().isEmpty()
    }

    def "an API without deprecated options is not affected"() {

        given:
        OgcApiDataV2 data = new ImmutableOgcApiDataV2.Builder().id("api").build()

        expect:
        !new ExtentMigrationV5(CONTEXT).isApplicable(data, Optional.empty())
    }

    static ImmutableFeatureTypeConfigurationOgcApi collection(String id, CollectionExtent extent) {
        new ImmutableFeatureTypeConfigurationOgcApi.Builder().id(id).label(id).extent(extent).build()
    }

    static FeaturesCoreConfiguration core(List extensions) {
        extensions.find { it instanceof FeaturesCoreConfiguration } as FeaturesCoreConfiguration
    }

    static def spatial(double xmin, double ymin, double xmax, double ymax) {
        ImmutableSpatialExtent.builder().xmin(xmin).ymin(ymin).xmax(xmax).ymax(ymax).build()
    }
}
