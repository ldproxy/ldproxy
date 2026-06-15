/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app

import de.ii.ogcapi.collections.domain.ImmutableOgcApiCollection
import de.ii.ogcapi.collections.domain.OgcApiCollection
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi
import de.ii.ogcapi.versioned.features.domain.ImmutableVersionedFeaturesConfiguration
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration
import spock.lang.Specification

class VersioningOnCollectionSpec extends Specification {

    VersioningOnCollection subject = new VersioningOnCollection()

    def 'no versioning object when extension is absent'() {
        given:
        ImmutableOgcApiCollection.Builder builder = new ImmutableOgcApiCollection.Builder().id('c')

        when:
        OgcApiCollection result = subject
                .process(builder, collection([]), null, null, false, null, [], Optional.empty())
                .build()

        then:
        !result.extensions.containsKey('versioning')
    }

    def 'no versioning object when nested rendering'() {
        given:
        ImmutableOgcApiCollection.Builder builder = new ImmutableOgcApiCollection.Builder().id('c')
        def config = new ImmutableVersionedFeaturesConfiguration.Builder()
                .enabled(true)
                .timeAxis(VersionedFeaturesConfiguration.TimeAxis.VALIDITY_TIME)
                .build()

        when:
        OgcApiCollection result = subject
                .process(builder, collection([config]), null, null, true /*isNested*/, null, [], Optional.empty())
                .build()

        then:
        !result.extensions.containsKey('versioning')
    }

    def 'emits timeAxis only when mutationTime is unset'() {
        given:
        ImmutableOgcApiCollection.Builder builder = new ImmutableOgcApiCollection.Builder().id('c')
        def config = new ImmutableVersionedFeaturesConfiguration.Builder()
                .enabled(true)
                .timeAxis(VersionedFeaturesConfiguration.TimeAxis.VALIDITY_TIME)
                .build()

        when:
        Map<String, Object> versioning = (Map) subject
                .process(builder, collection([config]), null, null, false, null, [], Optional.empty())
                .build()
                .extensions['versioning']

        then:
        versioning == [timeAxis: 'validity-time']
    }

    def 'emits both timeAxis and mutationTime with lower-hyphen wire form'() {
        given:
        ImmutableOgcApiCollection.Builder builder = new ImmutableOgcApiCollection.Builder().id('c')
        def config = new ImmutableVersionedFeaturesConfiguration.Builder()
                .enabled(true)
                .timeAxis(VersionedFeaturesConfiguration.TimeAxis.TRANSACTION_TIME)
                .mutationTime(VersionedFeaturesConfiguration.MutationTime.SERVER)
                .build()

        when:
        Map<String, Object> versioning = (Map) subject
                .process(builder, collection([config]), null, null, false, null, [], Optional.empty())
                .build()
                .extensions['versioning']

        then:
        versioning == [timeAxis: 'transaction-time', mutationTime: 'server']
    }

    private static FeatureTypeConfigurationOgcApi collection(List extensions) {
        new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                .id('c')
                .label('c')
                .extensions(extensions)
                .build()
    }
}
