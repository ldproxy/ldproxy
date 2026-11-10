/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain

import de.ii.ogcapi.foundation.domain.ExtensionConfiguration
import de.ii.xtraplatform.features.domain.Extent
import de.ii.xtraplatform.features.domain.ImmutableExtent
import de.ii.xtraplatform.features.domain.ImmutableSpatialExtent
import de.ii.xtraplatform.features.domain.ImmutableTemporalExtent
import spock.lang.Specification

class FeaturesCoreExtentConfigurationSpec extends Specification {

    static final Extent API_EXTENT = ImmutableExtent.builder()
            .spatial(ImmutableSpatialExtent.builder().xmin(7.0d).ymin(50.0d).xmax(7.1d).ymax(50.1d).build())
            .temporal(ImmutableTemporalExtent.builder().start("2020-01-01").end("2020-12-31").build())
            .build()

    def "a collection inherits the extent of the API"() {
        given:
        def api = new ImmutableFeaturesCoreConfiguration.Builder().extent(API_EXTENT).build()
        def collection = new ImmutableFeaturesCoreConfiguration.Builder().build()

        when:
        def merged = (FeaturesCoreConfiguration) collection.mergeInto((ExtensionConfiguration) api)

        then:
        merged.extent == Optional.of(API_EXTENT)
    }

    def "the extent of a collection replaces the extent of the API"() {
        given:
        def own = ImmutableExtent.builder()
                .spatial(ImmutableSpatialExtent.builder().xmin(6.0d).ymin(49.0d).xmax(8.0d).ymax(51.0d).build())
                .build()
        def api = new ImmutableFeaturesCoreConfiguration.Builder().extent(API_EXTENT).build()
        def collection = new ImmutableFeaturesCoreConfiguration.Builder().extent(own).build()

        when:
        def merged = (FeaturesCoreConfiguration) collection.mergeInto((ExtensionConfiguration) api)

        then:
        merged.extent == Optional.of(own)
    }
}
