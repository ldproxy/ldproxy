/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.jsonfg.domain

import de.ii.ogcapi.foundation.domain.AbstractExtensionConfigurationSpec
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration
import de.ii.ogcapi.foundation.domain.MergeBase
import de.ii.ogcapi.foundation.domain.MergeMap
import de.ii.ogcapi.foundation.domain.MergeMinimal
import de.ii.ogcapi.foundation.domain.MergeNested
import de.ii.ogcapi.foundation.domain.MergeSimple

@SuppressWarnings('ClashingTraitMethods')
class JsonFgConfigurationSpec extends AbstractExtensionConfigurationSpec implements MergeBase<JsonFgConfiguration>, MergeMinimal<JsonFgConfiguration>, MergeSimple<JsonFgConfiguration>, MergeMap<JsonFgConfiguration>, MergeNested<JsonFgConfiguration> {

    @Override
    JsonFgConfiguration getFull() {
        return new ImmutableJsonFgConfiguration.Builder()
                .enabled(true)
                .featureTypeV1("foo")
                .supportPlusProfile(true)
                .build()
    }

    @Override
    JsonFgConfiguration getMinimal() {
        return new ImmutableJsonFgConfiguration.Builder()
                .build()
    }

    @Override
    JsonFgConfiguration getMinimalFullMerged() {
        return getFull()
    }

    @Override
    JsonFgConfiguration getSimple() {
        return new ImmutableJsonFgConfiguration.Builder()
                .enabled(true)
                .featureTypeV1("bar")
                .build()
    }

    @Override
    JsonFgConfiguration getSimpleFullMerged() {
        return new ImmutableJsonFgConfiguration.Builder()
                .enabled(true)
                .featureTypeV1("bar")
                .supportPlusProfile(true)
                .build()
    }

    @Override
    JsonFgConfiguration getMap() {
        return new ImmutableJsonFgConfiguration.Builder()
                .build()
    }

    @Override
    JsonFgConfiguration getMapFullMerged() {
        return getFull()
    }

    @Override
    JsonFgConfiguration getNested() {
        return new ImmutableJsonFgConfiguration.Builder()
                .supportPlusProfile(false)
                .build()
    }

    @Override
    JsonFgConfiguration getNestedFullMerged() {
        return new ImmutableJsonFgConfiguration.Builder()
                .enabled(true)
                .featureTypeV1("foo")
                .supportPlusProfile(false)
                .build()
    }

    def "the deprecated geojsonCompatibility derives supportPlusProfile and is kept for the upgrade"() {
        when:
        JsonFgConfiguration cfg = new ImmutableJsonFgConfiguration.Builder()
                .geojsonCompatibility(false)
                .build()

        then:
        cfg.getSupportPlusProfile() == false
        cfg.getGeojsonCompatibility() == false
    }

    def "an explicit supportPlusProfile wins over the deprecated geojsonCompatibility"() {
        when:
        JsonFgConfiguration cfg = new ImmutableJsonFgConfiguration.Builder()
                .geojsonCompatibility(false)
                .supportPlusProfile(true)
                .build()

        then:
        cfg.getSupportPlusProfile() == true
    }

    def "the deprecated featureType derives featureTypeV1 from its first value and is kept for the upgrade"() {
        when:
        JsonFgConfiguration cfg = new ImmutableJsonFgConfiguration.Builder()
                .addFeatureType("a", "b")
                .build()

        then:
        cfg.getFeatureTypeV1() == "a"
        cfg.getFeatureType() == ["a", "b"]
    }

    def "an explicit featureTypeV1 wins over the deprecated featureType"() {
        when:
        JsonFgConfiguration cfg = new ImmutableJsonFgConfiguration.Builder()
                .featureTypeV1("x")
                .addFeatureType("a")
                .build()

        then:
        cfg.getFeatureTypeV1() == "x"
    }

    def "a deprecated featureType on the collection level wins over featureTypeV1 on the API level"() {
        given:
        JsonFgConfiguration api = new ImmutableJsonFgConfiguration.Builder().featureTypeV1("api").build()
        JsonFgConfiguration collection = new ImmutableJsonFgConfiguration.Builder().addFeatureType("collection").build()

        when:
        JsonFgConfiguration merged = collection.mergeInto((ExtensionConfiguration) api) as JsonFgConfiguration

        then:
        merged.getFeatureTypeV1() == "collection"
    }
}
