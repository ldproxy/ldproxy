/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.features.app

import java.nio.charset.StandardCharsets
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.io.WKBWriter
import spock.lang.Specification

class FeatureRecordReaderSpec extends Specification {

    def "parses the lines while they are written, also when a line is split"() {
        given:
        def point = new GeometryFactory().createPoint(new Coordinate(155000.5, 463000.25))
        def wkb = Base64.encoder.encodeToString(new WKBWriter(2).write(point))
        def text = '{"collection":"pand","id":"1","properties":{"a":["x","y"],"n":[3],"f":[1.5],"b":[true]},"geometry":"' + wkb + '"}\n' +
                '{"collection":"pand","id":"2","properties":{}}\n'
        def bytes = text.getBytes(StandardCharsets.UTF_8)
        def records = []
        def reader = new FeatureRecordReader({ records << it })

        when:
        reader.write(bytes, 0, 10)
        reader.write(bytes, 10, bytes.length - 10)
        reader.close()

        then:
        records.size() == 2
        records[0].collectionId() == "pand"
        records[0].featureId() == "1"
        records[0].values("a") == ["x", "y"]
        records[0].values("n") == [3L]
        records[0].values("f") == [1.5d]
        records[0].values("b") == [true]
        records[0].values("missing") == []
        records[0].geometry().get().equalsExact(point)
        records[1].featureId() == "2"
        records[1].geometry().isEmpty()
    }

    def "a last line without a newline is parsed on close"() {
        given:
        def records = []
        def reader = new FeatureRecordReader({ records << it })

        when:
        reader.write('{"collection":"c","id":"9","properties":{}}'.getBytes(StandardCharsets.UTF_8))

        then:
        records.isEmpty()

        when:
        reader.close()

        then:
        records*.featureId() == ["9"]
    }

    def "the line of a query with hits only gives the number of matching features"() {
        given:
        def records = []
        long matched = -2
        def reader = new FeatureRecordReader({ records << it }, { matched = it })

        when:
        reader.write('{"numberMatched":837273}\n'.getBytes(StandardCharsets.UTF_8))
        reader.close()

        then:
        matched == 837273L
        records.isEmpty()
    }
}
