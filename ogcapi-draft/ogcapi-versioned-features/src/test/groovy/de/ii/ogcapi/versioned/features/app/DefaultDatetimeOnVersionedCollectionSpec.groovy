/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app

import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi
import de.ii.ogcapi.versioned.features.domain.ImmutableVersionedFeaturesConfiguration
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration
import spock.lang.Specification

class DefaultDatetimeOnVersionedCollectionSpec extends Specification {

    DefaultDatetimeOnVersionedCollection subject = new DefaultDatetimeOnVersionedCollection()

    def 'no default when no VERSIONED_FEATURES extension is present'() {
        given:
        FeatureTypeConfigurationOgcApi collection = collection([])

        expect:
        subject.getDefault(null, collection).isEmpty()
    }

    def 'no default when VERSIONED_FEATURES is disabled'() {
        given:
        FeatureTypeConfigurationOgcApi collection = collection([
                new ImmutableVersionedFeaturesConfiguration.Builder()
                        .enabled(false)
                        .timeAxis(VersionedFeaturesConfiguration.TimeAxis.VALIDITY_TIME)
                        .build()])

        expect:
        subject.getDefault(null, collection).isEmpty()
    }

    def 'returns "now" when VERSIONED_FEATURES is enabled'() {
        given:
        FeatureTypeConfigurationOgcApi collection = collection([
                new ImmutableVersionedFeaturesConfiguration.Builder()
                        .enabled(true)
                        .timeAxis(VersionedFeaturesConfiguration.TimeAxis.VALIDITY_TIME)
                        .build()])

        expect:
        subject.getDefault(null, collection).get() == 'now'
    }

    def 'building-block configuration type is VersionedFeaturesConfiguration'() {
        expect:
        subject.getBuildingBlockConfigurationType() == VersionedFeaturesConfiguration
    }

    private static FeatureTypeConfigurationOgcApi collection(List extensions) {
        new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                .id('c')
                .label('c')
                .extensions(extensions)
                .build()
    }
}
