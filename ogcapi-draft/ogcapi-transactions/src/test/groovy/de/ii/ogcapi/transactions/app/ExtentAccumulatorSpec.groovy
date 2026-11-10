/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app

import de.ii.xtraplatform.features.domain.Tuple
import spock.lang.Specification

import java.time.Instant

class ExtentAccumulatorSpec extends Specification {

    static final long JAN = Instant.parse('2024-01-01T00:00:00Z').toEpochMilli()
    static final long MAR = Instant.parse('2024-03-01T00:00:00Z').toEpochMilli()
    static final long APR = Instant.parse('2024-04-01T00:00:00Z').toEpochMilli()

    def 'an open end of one result stays open when a later result has an end'() {

        given:
        def accumulator = new TransactionExecutorImpl.ExtentAccumulator()

        when:
        accumulator.merge(Optional.empty(), Optional.of(Tuple.of(JAN, null)))
        accumulator.merge(Optional.empty(), Optional.of(Tuple.of(MAR, APR)))

        then:
        accumulator.intervalMillis().get().start == Instant.ofEpochMilli(JAN)
        accumulator.intervalMillis().get().isUnboundedEnd()
    }

    def 'an open start of one result stays open when another result has a start'() {

        given:
        def accumulator = new TransactionExecutorImpl.ExtentAccumulator()

        when:
        accumulator.merge(Optional.empty(), Optional.of(Tuple.of(MAR, APR)))
        accumulator.merge(Optional.empty(), Optional.of(Tuple.of(null, MAR)))

        then:
        accumulator.intervalMillis().get().isUnboundedStart()
        accumulator.intervalMillis().get().end == Instant.ofEpochMilli(APR)
    }

    def 'a result that is unbounded on both sides is kept, no result gives no interval'() {

        given:
        def open = new TransactionExecutorImpl.ExtentAccumulator()
        def none = new TransactionExecutorImpl.ExtentAccumulator()

        when:
        open.merge(Optional.empty(), Optional.of(Tuple.of(null, null)))
        none.merge(Optional.empty(), Optional.empty())

        then:
        open.intervalMillis().get().isUnboundedStart()
        open.intervalMillis().get().isUnboundedEnd()
        none.intervalMillis().isEmpty()
    }
}
