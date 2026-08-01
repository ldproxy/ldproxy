/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app

import spock.lang.Specification

class TemporalSuffixSpec extends Specification {

    def 'a UTC timestamp maps to a Zulu suffix'() {
        expect:
        GmlWriterId.formatTemporalSuffix('2017-06-13T08:03:16Z') == '20170613T080316Z'
    }

    def 'an offset timestamp is converted to UTC'() {
        expect:
        GmlWriterId.formatTemporalSuffix('2017-06-13T10:03:16+02:00') == '20170613T080316Z'
    }

    def 'a local timestamp is assumed to be UTC'() {
        expect:
        GmlWriterId.formatTemporalSuffix('2017-06-13T08:03:16') == '20170613T080316Z'
    }

    def 'a date maps to the start of the day'() {
        expect:
        GmlWriterId.formatTemporalSuffix('2017-06-13') == '20170613T000000Z'
    }

    def 'an unparseable value yields no suffix'() {
        expect:
        GmlWriterId.formatTemporalSuffix('not-a-date') == null
    }
}
