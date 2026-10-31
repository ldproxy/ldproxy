/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import spock.lang.Specification

import java.time.Instant

class TemporalExtentSpec extends Specification {

    def mapper = new ObjectMapper().registerModule(new Jdk8Module())

    def "accepts epoch milliseconds from configurations of earlier versions"() {
        when:
        def extent = mapper.readValue('{"start": 1577836800000, "end": 1609459199000}', TemporalExtent)

        then:
        extent.startInstant == Instant.parse("2020-01-01T00:00:00Z")
        extent.endInstant == Instant.parse("2020-12-31T23:59:59Z")
    }

    def "accepts the formats of the provider extent"() {
        when:
        def extent = TemporalExtent.of(start, end)

        then:
        extent.startInstant == Instant.parse(expectedStart)
        extent.endInstant == Instant.parse(expectedEnd)

        where:
        start                       | end                         || expectedStart          | expectedEnd
        "2020-01-01"                | "2020-12-31"                || "2020-01-01T00:00:00Z" | "2020-12-31T23:59:59Z"
        "2020-01-01T01:00:00+01:00" | "2020-12-31T23:59:59Z"      || "2020-01-01T00:00:00Z" | "2020-12-31T23:59:59Z"
    }

    def "leaves open ends open"() {
        expect:
        TemporalExtent.of((String) null, "2020-12-31").startInstant == null
        TemporalExtent.of("2020-01-01", (String) null).endInstant == null
    }

    def "rejects values that are not a date or date-time"() {
        when:
        TemporalExtent.of("last year", null)

        then:
        thrown(IllegalStateException)
    }
}
