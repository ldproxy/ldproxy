/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app

import de.ii.ogcapi.foundation.domain.ExtensionRegistry
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.versioned.features.domain.ImmutableVersionedFeaturesConfiguration
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration
import de.ii.xtraplatform.cql.domain.Interval
import de.ii.xtraplatform.cql.domain.Operand
import de.ii.xtraplatform.cql.domain.Property
import de.ii.xtraplatform.cql.domain.TIntersects
import de.ii.xtraplatform.cql.domain.TemporalLiteral
import de.ii.xtraplatform.features.domain.FeatureQuery
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery
import de.ii.xtraplatform.features.domain.ImmutableMultiFeatureQuery
import de.ii.xtraplatform.features.domain.ImmutableSubQuery
import de.ii.xtraplatform.features.domain.MultiFeatureQuery
import spock.lang.Specification

class ProfileVersionsAsFeaturesUniqueIdsSpec extends Specification {

    FeatureQuery query(TemporalLiteral literal) {
        List<Operand> interval = [Property.of('lzi_beg'), Property.of('lzi_end')]
        return ImmutableFeatureQuery.builder()
                .type('test')
                .addFilters(TIntersects.of(Interval.of(interval), literal))
                .build()
    }

    def 'a timestamp interval is detected'() {
        expect: 'both bounds are timestamps or open, the literal resolves to a threeten Interval'
        ProfileVersionsAsFeaturesUniqueIds.hasIntervalDatetime(
                query(TemporalLiteral.of('2015-01-01T00:00:00Z', '..')))
        ProfileVersionsAsFeaturesUniqueIds.hasIntervalDatetime(
                query(TemporalLiteral.of('2015-01-01T00:00:00Z', '2026-01-01T00:00:00Z')))
    }

    def 'a date interval is detected'() {
        expect: 'a date bound keeps the literal as a CQL2 INTERVAL node'
        ProfileVersionsAsFeaturesUniqueIds.hasIntervalDatetime(
                query(TemporalLiteral.of('2015-01-01', '..')))
        ProfileVersionsAsFeaturesUniqueIds.hasIntervalDatetime(
                query(TemporalLiteral.of('2015-01-01', '2026-01-01')))
    }

    def 'an instant is not an interval'() {
        expect:
        !ProfileVersionsAsFeaturesUniqueIds.hasIntervalDatetime(
                query(TemporalLiteral.of('2015-01-01T00:00:00Z')))
    }

    def 'a query without a temporal filter has no interval'() {
        expect:
        !ProfileVersionsAsFeaturesUniqueIds.hasIntervalDatetime(
                ImmutableFeatureQuery.builder().type('test').build())
    }

    def 'a query expression on a versioned collection gets the composite-id extension'() {
        given: 'a collection with a composite-id pattern'
        def profile = new ProfileVersionsAsFeaturesUniqueIds(Stub(ExtensionRegistry))
        OgcApiDataV2 apiData = apiData(new ImmutableVersionedFeaturesConfiguration.Builder()
                .enabled(true)
                .compositeIdPattern('^(?<id>DE[A-Za-z0-9]{14})(?<start>\\d{8}T\\d{6}Z)$')
                .build())

        when: 'the multi-query is transformed, without any datetime filter'
        MultiFeatureQuery transformed = profile.transformMultiFeatureQuery(multiQuery(), apiData)

        then:
        transformed.getExtensions().size() == 1
        transformed.getExtensions().get(0) instanceof CompositeIdExtension
    }

    def 'a query expression without a composite-id pattern is unchanged'() {
        given:
        def profile = new ProfileVersionsAsFeaturesUniqueIds(Stub(ExtensionRegistry))
        OgcApiDataV2 apiData = apiData(new ImmutableVersionedFeaturesConfiguration.Builder()
                .enabled(true)
                .build())

        when:
        MultiFeatureQuery transformed = profile.transformMultiFeatureQuery(multiQuery(), apiData)

        then:
        transformed.getExtensions().isEmpty()
    }

    private static OgcApiDataV2 apiData(VersionedFeaturesConfiguration cfg) {
        new ImmutableOgcApiDataV2.Builder()
                .id('api')
                .serviceType('OGC_API')
                .putCollections('c', new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                        .id('c')
                        .label('c')
                        .addExtensions(cfg)
                        .build())
                .build()
    }

    private static MultiFeatureQuery multiQuery() {
        ImmutableMultiFeatureQuery.builder()
                .addQueries(ImmutableSubQuery.builder()
                        .collectionId('c')
                        .type('t')
                        .build())
                .build()
    }
}
