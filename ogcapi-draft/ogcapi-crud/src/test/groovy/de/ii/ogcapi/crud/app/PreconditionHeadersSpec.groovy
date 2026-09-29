/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crud.app

import jakarta.ws.rs.ClientErrorException
import spock.lang.Specification

class PreconditionHeadersSpec extends Specification {

    static final Date LAST_MODIFIED = Date.from(java.time.Instant.parse('2026-08-23T10:00:00Z'))
    static final Optional<String> ABSENT = Optional.empty()

    def 'a required precondition is missing'() {
        when: 'a collection that requires conditional requests, and no precondition'
        check(true, ABSENT, ABSENT, LAST_MODIFIED)

        then:
        ClientErrorException e = thrown()
        e.response.status == 428
    }

    def 'a required precondition is stated'() {
        when:
        check(true, ABSENT, Optional.of('Sat, 23 Aug 2026 10:00:00 GMT'), LAST_MODIFIED)

        then: 'the value itself is evaluated against the feature, not here'
        notThrown(ClientErrorException)
    }

    def 'a feature without a modification time does not require a precondition'() {
        when: 'the collection requires conditional requests, but the time is unknown'
        check(true, ABSENT, ABSENT, null)

        then: 'the header cannot be evaluated, so it must be ignored (RFC 9110, 13.1.4)'
        notThrown(ClientErrorException)
    }

    def 'a collection without optimistic locking does not require a precondition'() {
        when:
        check(false, ABSENT, ABSENT, LAST_MODIFIED)

        then:
        notThrown(ClientErrorException)
    }

    def 'an entity tag in If-Match can never be met'() {
        when: 'a request that changes a feature states an entity tag'
        check(false, Optional.of('"abc123"'), ABSENT, LAST_MODIFIED)

        then:
        ClientErrorException e = thrown()
        e.response.status == 412
    }

    def 'If-Match with "*" is met for a feature that exists'() {
        when:
        check(false, Optional.of(ifMatch), ABSENT, LAST_MODIFIED)

        then:
        notThrown(ClientErrorException)

        where:
        ifMatch << ['*', ' * ']
    }

    def 'If-Match is evaluated before the required precondition'() {
        when: 'both are violated'
        check(true, Optional.of('"abc123"'), ABSENT, LAST_MODIFIED)

        then: 'the order of RFC 9110, 13.2.2, reports the failed precondition'
        ClientErrorException e = thrown()
        e.response.status == 412
    }

    private static void check(
            boolean preconditionRequired,
            Optional<String> ifMatch,
            Optional<String> ifUnmodifiedSince,
            Date lastModified) {
        CommandHandlerCrudImpl.checkPreconditionHeaders(
                preconditionRequired, ifMatch, ifUnmodifiedSince, lastModified)
    }
}
