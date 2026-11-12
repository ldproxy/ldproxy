/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.app

import de.ii.xtraplatform.crs.domain.BoundingBox
import de.ii.xtraplatform.crs.domain.OgcCrs
import spock.lang.Specification

import static de.ii.ogcapi.tiles.app.TileSeedingBackgroundTask.getClipBoundingBox

class SeedingClipBoundingBoxSpec extends Specification {

    static final BoundingBox CHANGED_AREA = BoundingBox.of(7.0, 50.0, 7.1, 50.1, OgcCrs.CRS84)
    static final BoundingBox CONFIGURED = BoundingBox.of(6.0, 49.0, 8.0, 51.0, OgcCrs.CRS84)
    static final BoundingBox TILESET_BOUNDS = BoundingBox.of(6.5, 49.5, 7.5, 50.5, OgcCrs.CRS84)
    static final BoundingBox API_EXTENT = BoundingBox.of(5.0, 47.0, 15.0, 55.0, OgcCrs.CRS84)

    def "the area of a feature change is seeded, whatever else is known"() {
        expect:
        clip(CHANGED_AREA, CONFIGURED, TILESET_BOUNDS, API_EXTENT) == Optional.of(CHANGED_AREA)
    }

    def "a configured tiles extent takes precedence over the bounds of the tileset"() {
        expect:
        clip(null, CONFIGURED, TILESET_BOUNDS, API_EXTENT) == Optional.of(CONFIGURED)
    }

    def "the bounds of the tileset take precedence over the extent of the API"() {
        expect:
        clip(null, null, TILESET_BOUNDS, API_EXTENT) == Optional.of(TILESET_BOUNDS)
    }

    def "the extent of the API is the last resort"() {
        expect:
        clip(null, null, null, API_EXTENT) == Optional.of(API_EXTENT)
        clip(null, null, null, null) == Optional.empty()
    }

    def "the fallbacks are not computed when a configured extent applies"() {
        when:
        getClipBoundingBox(
                Optional.empty(),
                Optional.of(CONFIGURED),
                { throw new AssertionError("tileset bounds were requested") },
                { throw new AssertionError("API extent was requested") })

        then:
        noExceptionThrown()
    }

    static Optional<BoundingBox> clip(BoundingBox changedArea, BoundingBox configured, BoundingBox tilesetBounds, BoundingBox apiExtent) {
        getClipBoundingBox(
                Optional.ofNullable(changedArea),
                Optional.ofNullable(configured),
                { Optional.ofNullable(tilesetBounds) },
                { Optional.ofNullable(apiExtent) })
    }
}
