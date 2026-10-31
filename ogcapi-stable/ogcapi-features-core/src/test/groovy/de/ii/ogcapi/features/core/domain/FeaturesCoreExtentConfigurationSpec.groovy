/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain

import de.ii.ogcapi.foundation.domain.ExtensionConfiguration
import de.ii.ogcapi.foundation.domain.ImmutableCollectionExtent
import de.ii.ogcapi.foundation.domain.TemporalExtent
import de.ii.xtraplatform.crs.domain.BoundingBox
import de.ii.xtraplatform.crs.domain.OgcCrs
import spock.lang.Specification

class FeaturesCoreExtentConfigurationSpec extends Specification {

    static final BoundingBox BBOX = BoundingBox.of(7.0, 50.0, 7.1, 50.1, OgcCrs.CRS84)
    static final TemporalExtent INTERVAL = TemporalExtent.of("2020-01-01", "2020-12-31")

    def "a collection inherits the extent of the API"() {
        given:
        def api = withExtent(BBOX, INTERVAL)
        def collection = new ImmutableFeaturesCoreConfiguration.Builder().build()

        when:
        def merged = (FeaturesCoreConfiguration) collection.mergeInto((ExtensionConfiguration) api)

        then:
        merged.extent == api.extent
    }

    def "the extent of a collection replaces the extent of the API"() {
        given:
        def api = withExtent(BBOX, INTERVAL)
        def collection = withExtent(BoundingBox.of(6.0, 49.0, 8.0, 51.0, OgcCrs.CRS84), null)

        when:
        def merged = (FeaturesCoreConfiguration) collection.mergeInto((ExtensionConfiguration) api)

        then:
        merged.extent == collection.extent
    }

    static FeaturesCoreConfiguration withExtent(BoundingBox spatial, TemporalExtent temporal) {
        new ImmutableFeaturesCoreConfiguration.Builder()
                .extent(new ImmutableCollectionExtent.Builder()
                        .spatial(Optional.ofNullable(spatial))
                        .temporal(Optional.ofNullable(temporal))
                        .build())
                .build()
    }
}
