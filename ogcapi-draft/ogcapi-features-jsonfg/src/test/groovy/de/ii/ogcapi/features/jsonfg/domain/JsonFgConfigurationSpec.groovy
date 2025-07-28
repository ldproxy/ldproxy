/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.jsonfg.domain

import de.ii.ogcapi.foundation.domain.AbstractExtensionConfigurationSpec
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
                .featureType("foo")
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
                .featureType("bar")
                .build()
    }

    @Override
    JsonFgConfiguration getSimpleFullMerged() {
        return new ImmutableJsonFgConfiguration.Builder()
                .enabled(true)
                .featureType("bar")
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
                .featureType("foo")
                .supportPlusProfile(false)
                .build()
    }
}
