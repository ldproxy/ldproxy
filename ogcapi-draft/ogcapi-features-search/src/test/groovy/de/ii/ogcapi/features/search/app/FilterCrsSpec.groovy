/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app

import de.ii.xtraplatform.cql.app.CqlImpl
import de.ii.xtraplatform.cql.domain.BinarySpatialOperation
import de.ii.xtraplatform.cql.domain.Cql
import de.ii.xtraplatform.cql.domain.Cql2Expression
import de.ii.xtraplatform.cql.domain.GeometryNode
import de.ii.xtraplatform.cql.domain.InResultSet
import de.ii.xtraplatform.cql.domain.SpatialLiteral
import de.ii.xtraplatform.crs.domain.EpsgCrs
import de.ii.xtraplatform.crs.domain.OgcCrs
import spock.lang.Shared
import spock.lang.Specification

// The Search handler applies the query expression's filterCrs to an inline filter by routing it
// through Cql.read(): cql.read(cql.write(filter, JSON), JSON, filterCrs, true). These tests lock
// the two assumptions that depends on — the filter CRS is attached to geometry literals, and the
// inResultSet predicate survives the JSON round-trip (it is rewritten with producer context only
// afterwards).
class FilterCrsSpec extends Specification {

    @Shared
    Cql cql

    @Shared
    EpsgCrs crs25832 = EpsgCrs.of(25832)

    def setupSpec() {
        cql = new CqlImpl()
    }

    private static GeometryNode geometryOf(Cql2Expression expression) {
        SpatialLiteral literal = (SpatialLiteral) ((BinarySpatialOperation) expression).getArgs().get(1)
        return (GeometryNode) literal.getValue()
    }

    Cql2Expression withFilterCrs(Cql2Expression filter, EpsgCrs filterCrs) {
        return cql.read(cql.write(filter, Cql.Format.JSON), Cql.Format.JSON, filterCrs, true)
    }

    def 'cql.read attaches the filter CRS to a JSON geometry'() {
        given: 'a spatial filter with UTM coordinates'
        String json = """
        {
            "op": "s_intersects",
            "args": [
                { "property": "position" },
                { "type": "Polygon", "coordinates": [[[449432,5538008],[449872,5538008],[449872,5538212],[449432,5538212],[449432,5538008]]] }
            ]
        }
        """

        expect: 'without a filter CRS the geometry defaults to CRS84'
        geometryOf(cql.read(json, Cql.Format.JSON)).getGeometry().getCrs() == Optional.of(OgcCrs.CRS84)

        and: 'with a filter CRS the geometry carries it (Operand#getFilterCrs unwraps the injected Optional)'
        geometryOf(cql.read(json, Cql.Format.JSON, crs25832)).getGeometry().getCrs() == Optional.of(crs25832)
    }

    def 'an inline geometry literal has no CRS until the filter CRS is applied'() {
        given: 'a spatial filter with UTM coordinates and no CRS, as the query expression parser produces it'
        String json = """
        {
            "op": "s_intersects",
            "args": [
                { "property": "position" },
                { "type": "Polygon", "coordinates": [[[449432,5538008],[449872,5538008],[449872,5538212],[449432,5538212],[449432,5538008]]] }
            ]
        }
        """
        Cql2Expression parsed = cql.read(json, Cql.Format.JSON)

        expect: 'the geometry defaults to CRS84 (the bug: UTM eastings would be read as lon/lat)'
        geometryOf(parsed).getGeometry().getCrs().orElse(OgcCrs.CRS84) == OgcCrs.CRS84

        when: 'the handler applies the filter CRS'
        Cql2Expression corrected = withFilterCrs(parsed, crs25832)

        then: 'the geometry carries the filter CRS'
        geometryOf(corrected).getGeometry().getCrs() == Optional.of(crs25832)
    }

    def 'the inResultSet predicate survives the filter-CRS round-trip'() {
        given:
        Cql2Expression filter = InResultSet.of("dientZurDarstellungVon", "flst")

        when:
        Cql2Expression result = withFilterCrs(filter, crs25832)

        then:
        result == InResultSet.of("dientZurDarstellungVon", "flst")
    }
}
