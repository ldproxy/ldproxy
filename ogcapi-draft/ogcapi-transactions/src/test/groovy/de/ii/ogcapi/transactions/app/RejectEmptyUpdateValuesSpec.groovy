/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app

import com.fasterxml.jackson.databind.ObjectMapper
import de.ii.xtraplatform.features.domain.FeatureTransactions
import de.ii.xtraplatform.features.domain.ImmutablePropertyUpdate
import spock.lang.Specification

/**
 * A partial update carries the new values rather than a feature payload, so it never passes through
 * a decoder and gets its own empty-value check under {@code Prefer: handling=strict}. The rejection
 * is the platform one, so an update reads the same as a request body rejected while decoding.
 */
class RejectEmptyUpdateValuesSpec extends Specification {

    private static final ObjectMapper MAPPER = new ObjectMapper()

    private static FeatureTransactions.PropertyUpdate update(List<String> path, String json) {
        return ImmutablePropertyUpdate.builder()
                .path(path)
                .value(Optional.of(MAPPER.readTree(json)))
                .build()
    }

    private static FeatureTransactions.PropertyUpdate deletion(List<String> path) {
        return ImmutablePropertyUpdate.builder().path(path).value(Optional.empty()).build()
    }

    def 'an empty scalar value is rejected and named by its canonical path'() {
        when:
        TransactionExecutorImpl.rejectEmptyValues([update(['lifetime', 'end'], '""')])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("The property 'lifetime.end' has an empty value")
    }

    def 'a value of only whitespace is rejected'() {
        when:
        TransactionExecutorImpl.rejectEmptyValues([update(['name'], '"   "')])

        then:
        thrown(IllegalArgumentException)
    }

    def 'an empty entry of a multi-valued property is named by its index'() {
        when:
        TransactionExecutorImpl.rejectEmptyValues([update(['tags'], '["a","","c"]')])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("The property 'tags[1]' has an empty value")
    }

    def 'an empty member of an object-array entry is named by the full path'() {
        when:
        TransactionExecutorImpl.rejectEmptyValues([update(['addresses'], '[{"street":"Main"},{"street":""}]')])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("The property 'addresses[1].street' has an empty value")
    }

    def 'an explicit delete sets null, which is not an empty value'() {
        when:
        TransactionExecutorImpl.rejectEmptyValues([deletion(['name'])])

        then:
        noExceptionThrown()
    }

    def 'values that are not strings cannot be empty'() {
        when:
        TransactionExecutorImpl.rejectEmptyValues([
                update(['name'], '"Bonn"'),
                update(['count'], '0'),
                update(['flag'], 'false'),
                update(['note'], 'null'),
                update(['geometry'], '{"type":"Point","coordinates":[7.1,50.7]}')
        ])

        then:
        noExceptionThrown()
    }

    def 'an empty list of updates passes'() {
        when:
        TransactionExecutorImpl.rejectEmptyValues([])

        then:
        noExceptionThrown()
    }
}
