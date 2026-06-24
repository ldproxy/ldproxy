/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app

import jakarta.ws.rs.BadRequestException
import spock.lang.Specification

import java.time.Instant

class HeaderOgcMutationDatetimeSpec extends Specification {

    def 'empty result when header is absent or blank'() {
        expect:
        HeaderOgcMutationDatetime.parse(value) == Optional.empty()

        where:
        value << [null, '', '   ']
    }

    def 'parses a valid RFC 3339 instant'() {
        expect:
        HeaderOgcMutationDatetime.parse('2026-06-06T12:34:56Z') == Optional.of(Instant.parse('2026-06-06T12:34:56Z'))
    }

    def 'rejects a malformed value with BadRequest'() {
        when:
        HeaderOgcMutationDatetime.parse('not-a-timestamp')

        then:
        thrown(BadRequestException)
    }
}
