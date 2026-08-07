/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app

import de.ii.xtraplatform.geometries.domain.CompoundCurve
import de.ii.xtraplatform.geometries.domain.GeometryType
import de.ii.xtraplatform.geometries.domain.LineString
import de.ii.xtraplatform.geometries.domain.MultiLineString
import spock.lang.Specification

class ForceCompositeCurveSpec extends Specification {

    def 'a single curve is boxed into a CompoundCurve when the flag is set'() {
        given: 'a single curve'
        def line = LineString.of(0d, 0d, 1d, 1d)

        when: 'the option forces a composite curve'
        def result = GmlWriterGeometry.forceCompositeCurve(line, true)

        then: 'the curve is wrapped in a CompoundCurve of one segment'
        result instanceof CompoundCurve
        result.getType() == GeometryType.COMPOUND_CURVE
        ((CompoundCurve) result).getValue().size() == 1
    }

    def 'a single curve is left untouched when the flag is not set'() {
        given: 'a single curve'
        def line = LineString.of(0d, 0d, 1d, 1d)

        expect: 'the very same geometry is returned'
        GmlWriterGeometry.forceCompositeCurve(line, false).is(line)
    }

    def 'a geometry that is already composite or not a single curve is left untouched'() {
        given: 'a composite curve and a multi curve'
        def compound = CompoundCurve.of([LineString.of(0d, 0d, 1d, 1d)])
        def multi = MultiLineString.of([LineString.of(0d, 0d, 1d, 1d), LineString.of(2d, 2d, 3d, 3d)])

        expect: 'neither is boxed again'
        GmlWriterGeometry.forceCompositeCurve(compound, true).is(compound)
        GmlWriterGeometry.forceCompositeCurve(multi, true).is(multi)
    }
}
