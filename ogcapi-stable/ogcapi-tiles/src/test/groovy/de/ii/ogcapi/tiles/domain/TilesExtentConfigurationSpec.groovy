/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.domain

import de.ii.ogcapi.foundation.domain.ExtensionConfiguration
import de.ii.xtraplatform.features.domain.Extent
import de.ii.xtraplatform.features.domain.ImmutableExtent
import de.ii.xtraplatform.features.domain.ImmutableSpatialExtent
import de.ii.xtraplatform.features.domain.ImmutableTemporalExtent
import spock.lang.Specification

class TilesExtentConfigurationSpec extends Specification {

    static final Extent EXTENT = extent(7.0, 50.0, 7.1, 50.1)

    def "a collection inherits the extent of the API"() {
        given:
        def api = new ImmutableTilesConfiguration.Builder().extent(EXTENT).build()
        def collection = new ImmutableTilesConfiguration.Builder().build()

        expect:
        ((TilesConfiguration) collection.mergeInto((ExtensionConfiguration) api)).extent == Optional.of(EXTENT)
    }

    def "the extent of a collection replaces the extent of the API"() {
        given:
        def api = new ImmutableTilesConfiguration.Builder().extent(EXTENT).build()
        def own = extent(6.0, 49.0, 8.0, 51.0)
        def collection = new ImmutableTilesConfiguration.Builder().extent(own).build()

        expect:
        ((TilesConfiguration) collection.mergeInto((ExtensionConfiguration) api)).extent == Optional.of(own)
    }

    def "a temporal extent is rejected for tiles"() {
        when:
        new ImmutableTilesConfiguration.Builder()
                .extent(ImmutableExtent.builder()
                        .temporal(ImmutableTemporalExtent.builder().start("2020-01-01").build())
                        .build())
                .build()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Extent: 'temporal' is not supported for tiles, only 'spatial'."
    }

    static Extent extent(double xmin, double ymin, double xmax, double ymax) {
        ImmutableExtent.builder()
                .spatial(ImmutableSpatialExtent.builder().xmin(xmin).ymin(ymin).xmax(xmax).ymax(ymax).build())
                .build()
    }
}
