/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.features.app


import de.ii.xtraplatform.crs.domain.CoordinateTuple
import de.ii.xtraplatform.crs.domain.CrsTransformer
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import spock.lang.Specification

class GridCellsSpec extends Specification {

    static final GeometryFactory FACTORY = new GeometryFactory()

    // a stand-in for a transformation from a projected CRS: scales 1000 m to degrees
    CrsTransformer fromNative() {
        CrsTransformer transformer = Stub(CrsTransformer)
        transformer.transform(_ as Double, _ as Double) >> { double x, double y -> new CoordinateTuple(x / 1000, y / 1000) }
        transformer
    }

    def "a point belongs to the cell it lies in, the key is the center of the cell"() {
        given:
        def cells = new GridCells(250, fromNative())

        when:
        def center = cells.center(FACTORY.createPoint(new Coordinate(1010, 2490)))

        then:
        center == [1.125d, 2.375d]
    }

    def "a polygon belongs to the cell of a point on it, not of its envelope center"() {
        given:
        def cells = new GridCells(100, fromNative())
        // an L shape whose envelope center (150, 150) lies outside of it
        def polygon = FACTORY.createPolygon([
            new Coordinate(0, 0), new Coordinate(300, 0), new Coordinate(300, 50),
            new Coordinate(50, 50), new Coordinate(50, 300), new Coordinate(0, 300),
            new Coordinate(0, 0)] as Coordinate[])

        when:
        def center = cells.center(polygon)

        then:
        center != [0.15d, 0.15d]
    }

    def "negative coordinates are cut at the lower cell boundary"() {
        given:
        def cells = new GridCells(100, fromNative())

        when:
        def center = cells.center(FACTORY.createPoint(new Coordinate(-10, -110)))

        then:
        center == [-0.05d, -0.15d]
    }

    def "an empty geometry has no cell"() {
        expect:
        new GridCells(100, fromNative()).center(FACTORY.createPoint()) == null
    }
}
