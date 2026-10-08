/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg.migrations

import de.ii.ogcapi.features.custom.extensions.domain.ImmutableFeaturesExtensionsConfiguration
import de.ii.ogcapi.features.html.domain.FeaturesHtmlConfiguration
import de.ii.ogcapi.features.html.domain.ImmutableFeaturesHtmlConfiguration
import de.ii.ogcapi.features.jsonfg.domain.ImmutableJsonFgConfiguration
import de.ii.ogcapi.features.jsonfg.domain.JsonFgConfiguration
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.xtraplatform.entities.domain.EntityMigration.EntityMigrationContext
import spock.lang.Specification

class OgcApiMigrationsV5Spec extends Specification {

    static final EntityMigrationContext CONTEXT = { matcher -> false } as EntityMigrationContext

    static OgcApiDataV2 api(List<ExtensionConfiguration> apiExtensions,
                            Map<String, List<ExtensionConfiguration>> collections) {
        Map<String, FeatureTypeConfigurationOgcApi> cols = collections.collectEntries { id, extensions ->
            [(id): new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                    .id(id)
                    .label(id)
                    .extensions(extensions)
                    .build()]
        }
        return new ImmutableOgcApiDataV2.Builder()
                .id("api")
                .extensions(apiExtensions)
                .collections(cols)
                .build()
    }

    static <T extends ExtensionConfiguration> T extension(List<ExtensionConfiguration> extensions, Class<T> type) {
        return extensions.find { type.isInstance(it) } as T
    }

    def "JSON_FG: deprecated options are replaced at API and collection level"() {
        given:
        OgcApiDataV2 data = api(
                [new ImmutableJsonFgConfiguration.Builder()
                         .enabled(true)
                         .geojsonCompatibility(false)
                         .addFeatureType("a", "b")
                         .build(),
                 new ImmutableFeaturesHtmlConfiguration.Builder().enabled(true).build()],
                ["c1": [new ImmutableJsonFgConfiguration.Builder().addFeatureType("c").build()],
                 "c2": [new ImmutableFeaturesHtmlConfiguration.Builder().enabled(false).build()]])
        JsonFgMigrationV5 migration = new JsonFgMigrationV5(CONTEXT)

        expect:
        migration.isApplicable(data, Optional.empty())

        when:
        OgcApiDataV2 migrated = migration.migrate(data, Optional.empty())
        JsonFgConfiguration apiCfg = extension(migrated.getExtensions(), JsonFgConfiguration)
        JsonFgConfiguration c1Cfg = extension(migrated.getCollections().get("c1").getExtensions(), JsonFgConfiguration)

        then:
        apiCfg.getEnabled() == true
        apiCfg.getGeojsonCompatibility() == null
        apiCfg.getSupportPlusProfile() == false
        apiCfg.getFeatureTypeV1() == "a"
        !apiCfg.getFeatureType()
        c1Cfg.getFeatureTypeV1() == "c"
        !c1Cfg.getFeatureType()
        migrated.getExtensions().size() == 2
        extension(migrated.getExtensions(), FeaturesHtmlConfiguration).getEnabled() == true
        migrated.getCollections().get("c2").getExtensions().size() == 1
        !migration.isApplicable(migrated, Optional.empty())
    }

    def "JSON_FG: explicit replacements win over deprecated options"() {
        given:
        OgcApiDataV2 data = api(
                [new ImmutableJsonFgConfiguration.Builder()
                         .geojsonCompatibility(false)
                         .supportPlusProfile(true)
                         .addFeatureType("a")
                         .featureTypeV1("x")
                         .build()],
                [:])

        when:
        OgcApiDataV2 migrated = new JsonFgMigrationV5(CONTEXT).migrate(data, Optional.empty())
        JsonFgConfiguration apiCfg = extension(migrated.getExtensions(), JsonFgConfiguration)

        then:
        apiCfg.getSupportPlusProfile() == true
        apiCfg.getFeatureTypeV1() == "x"
        apiCfg.getGeojsonCompatibility() == null
        !apiCfg.getFeatureType()
    }

    def "JSON_FG: a configuration without deprecated options is not applicable"() {
        given:
        OgcApiDataV2 data = api(
                [new ImmutableJsonFgConfiguration.Builder().enabled(true).featureTypeV1("x").build()],
                ["c1": [new ImmutableJsonFgConfiguration.Builder().supportPlusProfile(false).build()]])

        expect:
        !new JsonFgMigrationV5(CONTEXT).isApplicable(data, Optional.empty())
    }

    def "FEATURES_HTML: the deprecated geometryProperties are removed"() {
        given:
        OgcApiDataV2 data = api(
                [new ImmutableFeaturesHtmlConfiguration.Builder().enabled(true).build()],
                ["c1": [new ImmutableFeaturesHtmlConfiguration.Builder()
                                .addGeometryProperties("lod1Solid", "lod1GroundSurface")
                                .style("NONE")
                                .build()]])
        FeaturesHtmlMigrationV5 migration = new FeaturesHtmlMigrationV5(CONTEXT)

        expect:
        migration.isApplicable(data, Optional.empty())

        when:
        OgcApiDataV2 migrated = migration.migrate(data, Optional.empty())
        FeaturesHtmlConfiguration c1Cfg = extension(migrated.getCollections().get("c1").getExtensions(), FeaturesHtmlConfiguration)

        then:
        !c1Cfg.getGeometryProperties()
        c1Cfg.getStyle() == "NONE"
        !migration.isApplicable(migrated, Optional.empty())
    }

    def "FEATURES_HTML: a configuration without geometryProperties is not applicable"() {
        given:
        OgcApiDataV2 data = api([new ImmutableFeaturesHtmlConfiguration.Builder().enabled(true).build()], [:])

        expect:
        !new FeaturesHtmlMigrationV5(CONTEXT).isApplicable(data, Optional.empty())
    }

    def "FEATURES_EXTENSIONS: the building block is removed at API and collection level"() {
        given:
        OgcApiDataV2 data = api(
                [new ImmutableFeaturesExtensionsConfiguration.Builder().enabled(true).intersectsParameter(true).build(),
                 new ImmutableJsonFgConfiguration.Builder().enabled(true).build()],
                ["c1": [new ImmutableFeaturesExtensionsConfiguration.Builder().enabled(false).build(),
                        new ImmutableFeaturesHtmlConfiguration.Builder().enabled(true).build()]])
        FeaturesExtensionsMigrationV5 migration = new FeaturesExtensionsMigrationV5(CONTEXT)

        expect:
        migration.isApplicable(data, Optional.empty())

        when:
        OgcApiDataV2 migrated = migration.migrate(data, Optional.empty())

        then:
        migrated.getExtensions()*.getBuildingBlock() == ["JSON_FG"]
        migrated.getCollections().get("c1").getExtensions()*.getBuildingBlock() == ["FEATURES_HTML"]
        !migration.isApplicable(migrated, Optional.empty())
    }

    def "FEATURES_EXTENSIONS: an API without the building block is not applicable"() {
        given:
        OgcApiDataV2 data = api([new ImmutableJsonFgConfiguration.Builder().enabled(true).build()], [:])

        expect:
        !new FeaturesExtensionsMigrationV5(CONTEXT).isApplicable(data, Optional.empty())
    }
}
