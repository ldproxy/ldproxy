/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app

import spock.lang.Specification

class CoordinatePrecisionSpec extends Specification {

    def 'per axis the smaller number of decimal places applies, 0 means no rounding'() {
        expect:
        FeaturesQueryImpl.coarser(precision, configured) == result

        where:
        precision | configured || result
        [0, 0, 0] | [8, 8, 3]  || [8, 8, 3]
        [4, 4, 0] | [8, 8, 3]  || [4, 4, 3]
        [4, 4, 4] | [3, 3, 3]  || [3, 3, 3]
        [5, 5, 5] | [0, 0, 0]  || [5, 5, 5]
        [4, 4]    | [8, 8, 3]  || [4, 4, 3]
    }
}
