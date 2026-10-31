/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.domain

import de.ii.ogcapi.foundation.domain.ExtensionConfiguration
import de.ii.xtraplatform.crs.domain.BoundingBox
import de.ii.xtraplatform.crs.domain.OgcCrs
import spock.lang.Specification

class TilesExtentConfigurationSpec extends Specification {

    static final BoundingBox BBOX = BoundingBox.of(7.0, 50.0, 7.1, 50.1, OgcCrs.CRS84)

    def "a collection inherits the extent of the API"() {
        given:
        def api = new ImmutableTilesConfiguration.Builder().extent(BBOX).build()
        def collection = new ImmutableTilesConfiguration.Builder().build()

        expect:
        ((TilesConfiguration) collection.mergeInto((ExtensionConfiguration) api)).extent == Optional.of(BBOX)
    }

    def "the extent of a collection replaces the extent of the API"() {
        given:
        def api = new ImmutableTilesConfiguration.Builder().extent(BBOX).build()
        def own = BoundingBox.of(6.0, 49.0, 8.0, 51.0, OgcCrs.CRS84)
        def collection = new ImmutableTilesConfiguration.Builder().extent(own).build()

        expect:
        ((TilesConfiguration) collection.mergeInto((ExtensionConfiguration) api)).extent == Optional.of(own)
    }
}
