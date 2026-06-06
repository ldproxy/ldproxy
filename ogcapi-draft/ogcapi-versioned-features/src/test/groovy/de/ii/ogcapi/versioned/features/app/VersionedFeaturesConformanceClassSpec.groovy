/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app

import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.versioned.features.domain.ImmutableVersionedFeaturesConfiguration
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration
import spock.lang.Specification

class VersionedFeaturesConformanceClassSpec extends Specification {

    VersionedFeaturesConformanceClass subject = new VersionedFeaturesConformanceClass()

    def 'not enabled when no collection has VERSIONED_FEATURES'() {
        given:
        OgcApiDataV2 apiData = api([collection('a', [])])

        expect:
        !subject.isEnabledForApi(apiData)
    }

    def 'not enabled when the only collection that configures VERSIONED_FEATURES is disabled'() {
        given:
        OgcApiDataV2 apiData = api([collection('a', [
                new ImmutableVersionedFeaturesConfiguration.Builder()
                        .enabled(false)
                        .timeAxis(VersionedFeaturesConfiguration.TimeAxis.VALIDITY_TIME)
                        .build()])])

        expect:
        !subject.isEnabledForApi(apiData)
    }

    def 'enabled when at least one collection has VERSIONED_FEATURES enabled'() {
        given:
        OgcApiDataV2 apiData = api([
                collection('a', []),
                collection('b', [new ImmutableVersionedFeaturesConfiguration.Builder()
                        .enabled(true)
                        .timeAxis(VersionedFeaturesConfiguration.TimeAxis.VALIDITY_TIME)
                        .build()])])

        expect:
        subject.isEnabledForApi(apiData)
    }

    def 'advertises the Core conformance URI when enabled'() {
        given:
        OgcApiDataV2 apiData = api([])

        expect:
        subject.getConformanceClassUris(apiData) == [VersionedFeaturesConformanceClass.CORE]
    }

    private static OgcApiDataV2 api(List collections) {
        ImmutableOgcApiDataV2.Builder builder = new ImmutableOgcApiDataV2.Builder().id('api')
        collections.each { builder.putCollections(it.id, it) }
        builder.build()
    }

    private static ImmutableFeatureTypeConfigurationOgcApi collection(String id, List extensions) {
        new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                .id(id)
                .label(id)
                .extensions(extensions)
                .build()
    }
}
