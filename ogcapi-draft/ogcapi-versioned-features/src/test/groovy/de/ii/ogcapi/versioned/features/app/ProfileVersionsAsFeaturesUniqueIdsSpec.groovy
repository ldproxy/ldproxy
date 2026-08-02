/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app

import de.ii.xtraplatform.cql.domain.Interval
import de.ii.xtraplatform.cql.domain.Operand
import de.ii.xtraplatform.cql.domain.Property
import de.ii.xtraplatform.cql.domain.TIntersects
import de.ii.xtraplatform.cql.domain.TemporalLiteral
import de.ii.xtraplatform.features.domain.FeatureQuery
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery
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
}
