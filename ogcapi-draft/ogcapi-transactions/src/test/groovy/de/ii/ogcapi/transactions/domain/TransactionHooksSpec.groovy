/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain

import de.ii.ogcapi.foundation.domain.HeaderPrefer
import spock.lang.Specification

class TransactionHooksSpec extends Specification {

    def "effective() appends the strict list to always under STRICT handling"() {
        given:
        def hooks = new ImmutableTransactionHooks.Builder()
                .addAlways("a1")
                .addStrict("s1")
                .addLenient("l1")
                .build()

        when:
        def result = hooks.effective(HeaderPrefer.Handling.STRICT)

        then:
        result == ["a1", "s1"]
    }

    def "effective() appends the lenient list to always under LENIENT handling"() {
        given:
        def hooks = new ImmutableTransactionHooks.Builder()
                .addAlways("a1")
                .addStrict("s1")
                .addLenient("l1")
                .build()

        when:
        def result = hooks.effective(HeaderPrefer.Handling.LENIENT)

        then:
        result == ["a1", "l1"]
    }

    def "effective() preserves order and returns only always when no handling-specific entries"() {
        given:
        def hooks = new ImmutableTransactionHooks.Builder()
                .addAlways("a1")
                .addAlways("a2")
                .build()

        expect:
        hooks.effective(HeaderPrefer.Handling.STRICT) == ["a1", "a2"]
        hooks.effective(HeaderPrefer.Handling.LENIENT) == ["a1", "a2"]
    }

    def "effective() is empty for an empty hooks object"() {
        given:
        def hooks = new ImmutableTransactionHooks.Builder().build()

        expect:
        hooks.effective(HeaderPrefer.Handling.STRICT).isEmpty()
        hooks.effective(HeaderPrefer.Handling.LENIENT).isEmpty()
    }

    def "mergeInto replaces the hook objects and concatenates updatableProperties"() {
        given:
        def apiLevel = new ImmutableTransactionsConfiguration.Builder()
                .addUpdatableProperties("name")
                .transactionSetup(new ImmutableTransactionHooks.Builder().addAlways("SET LOCAL a = 1").build())
                .build()
        def collectionLevel = new ImmutableTransactionsConfiguration.Builder()
                .addUpdatableProperties("tags")
                .preCommit(new ImmutableTransactionHooks.Builder().addStrict("SELECT check()").build())
                .build()

        when:
        def merged = (TransactionsConfiguration) collectionLevel.mergeInto(apiLevel)

        then:
        merged.updatableProperties as Set == ["name", "tags"] as Set
        merged.transactionSetup == apiLevel.transactionSetup
        merged.preCommit == collectionLevel.preCommit
    }
}
