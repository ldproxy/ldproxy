/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain

import spock.lang.Specification

class ListParameterValuesSpec extends Specification {

    def 'only an unencoded comma separates the items'() {
        expect:
        ListParameterValues.split(ListParameterValues.decode(raw)) == items

        where:
        raw                                       || items
        "Yemen%2C%20People%27s%20Republic%20of"   || ["Yemen, People's Republic of"]
        "a,b%2Cc"                                 || ["a", "b,c"]
        "a%2cb,c"                                 || ["a,b", "c"]
        "a,b"                                     || ["a", "b"]
        "a%20b,c+d"                               || ["a b", "c d"]
        "a,,b"                                    || ["a", "", "b"]
        ""                                        || []
    }

    def 'a value that was decoded without the marker is split at every comma'() {
        expect:
        ListParameterValues.split("a, b ,c") == ["a", "b", "c"]
    }
}
