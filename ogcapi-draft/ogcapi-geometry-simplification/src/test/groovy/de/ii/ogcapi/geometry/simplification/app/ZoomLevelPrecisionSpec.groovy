/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.geometry.simplification.app

import spock.lang.Specification

class ZoomLevelPrecisionSpec extends Specification {

    static double tolerance(int level) {
        559_082_264.028_717 / 4096 / Math.pow(2, level)
    }

    def 'the decimal places of a zoom level keep the rounding below the tolerance'() {
        when:
        def precision = QueryParameterZoomLevelFeatures.precision(tolerance(level))

        then:
        precision == [degree: degree, metre: metre]
        degree == 0 || 0.5 * Math.pow(10, -degree) * 111_319.5 < tolerance(level)
        metre == 0 || 0.5 * Math.pow(10, -metre) < tolerance(level)

        where:
        level || degree | metre
        8     || 3      | 0
        12    || 4      | 0
        13    || 4      | 0
        15    || 5      | 0
        18    || 6      | 1
    }

    def 'the option is off by default'() {
        expect:
        !new ImmutableGeometrySimplificationConfiguration.Builder().build().usesZoomLevelPrecision()
        new ImmutableGeometrySimplificationConfiguration.Builder().zoomLevelPrecision(true).build().usesZoomLevelPrecision()
    }
}
