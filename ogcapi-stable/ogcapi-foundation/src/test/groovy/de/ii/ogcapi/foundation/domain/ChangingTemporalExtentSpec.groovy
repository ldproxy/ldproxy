/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain

import spock.lang.Specification

class ChangingTemporalExtentSpec extends Specification {

    static ChangingTemporalExtent extent(String start, String end) {
        ChangingTemporalExtent.of(TemporalExtent.of(start, end))
    }

    def "a delta within the current extent changes nothing"() {
        expect:
        extent("2020-01-01", "2020-12-31").updateWith(extent(deltaStart, deltaEnd)).isEmpty()

        where:
        deltaStart   | deltaEnd
        "2020-03-01" | "2020-04-01"
        "2020-01-01" | "2020-12-31"
    }

    def "a delta reaching beyond the current extent widens it"() {
        when:
        def updated = extent("2020-01-01", "2020-12-31").updateWith(extent(deltaStart, deltaEnd))

        then:
        updated.get().value == TemporalExtent.of(expectedStart, expectedEnd)

        where:
        deltaStart   | deltaEnd     || expectedStart          | expectedEnd
        "2019-06-01" | "2020-03-01" || "2019-06-01T00:00:00Z" | "2020-12-31T23:59:59Z"
        "2020-06-01" | "2021-06-01" || "2020-01-01T00:00:00Z" | "2021-06-01T23:59:59Z"
        "2019-06-01" | "2021-06-01" || "2019-06-01T00:00:00Z" | "2021-06-01T23:59:59Z"
    }

    def "an open end of the current extent contains every delta on that side"() {
        expect:
        extent(null, "2020-12-31").updateWith(extent("1900-01-01", "2020-06-01")).isEmpty()
        extent("2020-01-01", null).updateWith(extent("2020-06-01", "2100-01-01")).isEmpty()
    }

    def "an open end of the delta opens the current extent on that side"() {
        when:
        def updated = extent("2020-01-01", "2020-12-31").updateWith(extent(null, "2020-06-01"))

        then:
        updated.get().value.startInstant == null
        updated.get().value.endInstant == TemporalExtent.of(null, "2020-12-31").endInstant
    }
}
