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
import de.ii.ogcapi.transactions.domain.TxAction
import de.ii.ogcapi.versioned.features.domain.ImmutableVersionedFeaturesConfiguration
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration
import jakarta.ws.rs.BadRequestException
import spock.lang.Specification

import java.time.Instant

class VersionedMutationStrategySpec extends Specification {

    VersionedMutationStrategy subject = new VersionedMutationStrategy()

    def 'not enabled when VERSIONED_FEATURES is not configured on the collection'() {
        given:
        OgcApiDataV2 apiData = api([collection('a', [])])

        expect:
        !subject.isEnabledForApi(apiData, 'a')
    }

    def 'not enabled when VERSIONED_FEATURES is configured but disabled'() {
        given:
        OgcApiDataV2 apiData = api([collection('a', [
                new ImmutableVersionedFeaturesConfiguration.Builder()
                        .enabled(false)
                        .timeAxis(VersionedFeaturesConfiguration.TimeAxis.VALIDITY_TIME)
                        .build()])])

        expect:
        !subject.isEnabledForApi(apiData, 'a')
    }

    def 'enabled when VERSIONED_FEATURES is enabled on the collection'() {
        given:
        OgcApiDataV2 apiData = api([collection('a', [
                new ImmutableVersionedFeaturesConfiguration.Builder()
                        .enabled(true)
                        .timeAxis(VersionedFeaturesConfiguration.TimeAxis.VALIDITY_TIME)
                        .build()])])

        expect:
        subject.isEnabledForApi(apiData, 'a')
    }

    def 'priority is higher than the default plain strategy'() {
        expect:
        subject.priority() > 0
    }

    def 'building-block configuration type is VersionedFeaturesConfiguration'() {
        expect:
        subject.getBuildingBlockConfigurationType() == VersionedFeaturesConfiguration
    }

    def 'server mutationTime: returns the scope timestamp regardless of header'() {
        given:
        OgcApiDataV2 apiData = apiWithMode(VersionedFeaturesConfiguration.MutationTime.SERVER)
        TxAction action = Stub(TxAction) { getCollectionId() >> 'a' }
        Instant scope = Instant.parse('2026-06-06T10:00:00Z')

        expect:
        subject.resolveMutationTimestamp(apiData, action, scope, Optional.empty()) == scope
        subject.resolveMutationTimestamp(
                apiData, action, scope, Optional.of(Instant.parse('2030-01-01T00:00:00Z'))) == scope
    }

    def 'client mutationTime: returns the header value when supplied'() {
        given:
        OgcApiDataV2 apiData = apiWithMode(VersionedFeaturesConfiguration.MutationTime.CLIENT)
        TxAction action = Stub(TxAction) { getCollectionId() >> 'a' }
        Instant scope = Instant.parse('2026-06-06T10:00:00Z')
        Instant header = Instant.parse('2025-01-01T00:00:00Z')

        expect:
        subject.resolveMutationTimestamp(apiData, action, scope, Optional.of(header)) == header
    }

    def 'client mutationTime: 400 when neither body nor header supply a timestamp'() {
        given:
        OgcApiDataV2 apiData = apiWithMode(VersionedFeaturesConfiguration.MutationTime.CLIENT)
        TxAction action = Stub(TxAction) { getCollectionId() >> 'a' }
        Instant scope = Instant.parse('2026-06-06T10:00:00Z')

        when:
        subject.resolveMutationTimestamp(apiData, action, scope, Optional.empty())

        then:
        thrown(BadRequestException)
    }

    def 'no VERSIONED_FEATURES config on collection: returns the scope timestamp (defensive fallback)'() {
        given:
        OgcApiDataV2 apiData = api([collection('a', [])])
        TxAction action = Stub(TxAction) { getCollectionId() >> 'a' }
        Instant scope = Instant.parse('2026-06-06T10:00:00Z')

        expect:
        subject.resolveMutationTimestamp(apiData, action, scope, Optional.empty()) == scope
    }

    private static OgcApiDataV2 apiWithMode(VersionedFeaturesConfiguration.MutationTime mode) {
        api([collection('a', [new ImmutableVersionedFeaturesConfiguration.Builder()
                .enabled(true)
                .timeAxis(VersionedFeaturesConfiguration.TimeAxis.VALIDITY_TIME)
                .mutationTime(mode)
                .build()])])
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
