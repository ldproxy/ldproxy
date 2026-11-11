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
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.GeometryFactory
import spock.lang.Specification

class GeometryInputsSpec extends Specification {

    // a stand-in for a transformation into a projected CRS: scales degrees to 1000 m
    CrsTransformer toNative() {
        CrsTransformer transformer = Stub(CrsTransformer)
        transformer.transform(_ as Double, _ as Double) >> { double x, double y -> new CoordinateTuple(x * 1000, y * 1000) }
        transformer
    }

    def "a point with a distance is a circle in the native CRS"() {
        when:
        def clip = GeometryInputs.clip([point: [5.0, 52.0], distance: 250], toNative())

        then:
        Math.abs(clip.area - Math.PI * 250 * 250) / (Math.PI * 250 * 250) < 0.0005
        Math.abs(clip.centroid.x - 5000d) < 0.000001
        Math.abs(clip.centroid.y - 52000d) < 0.000001
    }

    def "a GeoJSON polygon is transformed into the native CRS"() {
        when:
        def clip = GeometryInputs.clip([geometry: [type: "Polygon", coordinates: [[[0, 0], [1, 0], [1, 1], [0, 1], [0, 0]]]]], toNative())

        then:
        clip.area == 1000000d
    }

    def "the clipped areas of a partition add up to the area of the clip"() {
        given:
        def factory = new GeometryFactory()
        def clip = GeometryInputs.clip([point: [0.05, 0.05], distance: 30], toNative())
        def quarters = [new Envelope(0, 50, 0, 50), new Envelope(50, 100, 0, 50), new Envelope(0, 50, 50, 100), new Envelope(50, 100, 50, 100)]
                .collect { factory.toGeometry(it) }

        when:
        def sum = quarters.sum { GeometryInputs.virtualValues(it, Optional.of(clip))[Aggregation.AREA] }

        then:
        Math.abs(sum - clip.area) < 0.000001
    }

    def "without a clip the values are those of the geometry"() {
        given:
        def square = new GeometryFactory().toGeometry(new Envelope(0, 10, 0, 20))

        when:
        def values = GeometryInputs.virtualValues(square, Optional.empty())

        then:
        values[Aggregation.AREA] == 200d
        values[Aggregation.LENGTH] == 60d
    }

    def "invalid geometry inputs are rejected"() {
        when:
        GeometryInputs.clip(input, toNative())

        then:
        IllegalArgumentException e = thrown()
        e.message.contains(message)

        where:
        input                                                                         || message
        [point: [5.0, 52.0]]                                                          || "clip.distance"
        [point: [5.0, 52.0], distance: -1]                                            || "positive number"
        [point: [500.0, 52.0], distance: 10]                                          || "[lon, lat]"
        [geometry: [type: "Point", coordinates: [0, 0]]]                              || "Polygon or MultiPolygon"
        [geometry: [type: "Polygon", coordinates: [[[0, 0], [1, 0], [1, 1], [0, 1]]]]] || "end with its first position"
        "POINT(0 0)"                                                                  || "must be an object"
    }
}
