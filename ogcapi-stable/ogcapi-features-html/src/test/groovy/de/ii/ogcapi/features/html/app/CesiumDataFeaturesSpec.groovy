/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.html.app

import de.ii.xtraplatform.crs.domain.BoundingBox
import de.ii.xtraplatform.crs.domain.OgcCrs
import spock.lang.Specification

class CesiumDataFeaturesSpec extends Specification {

    static final String URL = "http://localhost:7080/foo/collections/bar/items?f=glb"

    def "a page without features uses the spatial extent of the collection"() {
        given: "no features and a 2D collection extent"
        def data = new CesiumDataFeatures([], URL, Optional.of(BoundingBox.of(6.9, 50.9, 7.0, 51.0, OgcCrs.CRS84)))

        when:
        def extent = data.getFeatureExtent()
        def region = URLDecoder.decode(data.getEncodedRegion(), "UTF-8")

        then: "the extent of the collection, heights 0"
        extent == "{minLon:6.9,minLat:50.9,maxLon:7.0,maxLat:51.0}"
        region == [Math.toRadians(6.9), Math.toRadians(50.9), Math.toRadians(7.0), Math.toRadians(51.0), 0.0d, 0.0d].join(",")
    }

    def "a page without features uses the heights of a 3D collection extent"() {
        given: "no features and a 3D collection extent"
        def bbox = BoundingBox.of(6.9, 50.9, 30.0, 7.0, 51.0, 120.0, OgcCrs.CRS84h)
        def data = new CesiumDataFeatures([], URL, Optional.of(bbox))

        when:
        def region = URLDecoder.decode(data.getEncodedRegion(), "UTF-8")

        then:
        region.endsWith(",30.0,120.0")
    }

    def "a page without features and without a collection extent covers the globe"() {
        given:
        def data = new CesiumDataFeatures([], URL, Optional.empty())

        when:
        def extent = data.getFeatureExtent()

        then:
        extent == "{minLon:-180.0,minLat:-90.0,maxLon:180.0,maxLat:90.0}"
    }
}
