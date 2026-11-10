/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app

import de.ii.ogcapi.features.core.domain.ImmutableFeaturesCoreConfiguration
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.foundation.domain.TemporalExtent
import de.ii.xtraplatform.crs.domain.BoundingBox
import de.ii.xtraplatform.crs.domain.OgcCrs
import de.ii.xtraplatform.features.domain.Extent
import de.ii.xtraplatform.features.domain.ImmutableExtent
import de.ii.xtraplatform.features.domain.ImmutableSpatialExtent
import de.ii.xtraplatform.features.domain.ImmutableTemporalExtent
import de.ii.xtraplatform.features.domain.SpatialExtent
import spock.lang.Specification

import static de.ii.ogcapi.features.core.app.FeaturesCoreBuildingBlock.configuredSpatialExtent
import static de.ii.ogcapi.features.core.app.FeaturesCoreBuildingBlock.configuredTemporalExtent

class ConfiguredExtentSpec extends Specification {

    static final SpatialExtent COLLECTION_SPATIAL = spatial(7.0, 50.0, 7.1, 50.1)
    static final SpatialExtent API_SPATIAL = spatial(6.0, 49.0, 8.0, 51.0)
    static final SpatialExtent COMPUTED = ImmutableSpatialExtent.builder().computed(true).build()
    static final de.ii.xtraplatform.features.domain.TemporalExtent API_INTERVAL =
            ImmutableTemporalExtent.builder().start("2020-01-01").end("2020-12-31").build()

    def "the extent of the collection takes precedence"() {
        given:
        def apiData = apiData(extent(COLLECTION_SPATIAL, null), extent(API_SPATIAL, null))

        expect: 'the configured extent in CRS84'
        configuredSpatialExtent(apiData, apiData.getCollections().get("foo")) == Optional.of(BoundingBox.of(7.0, 50.0, 7.1, 50.1, OgcCrs.CRS84))
    }

    def "the extent of the API applies to collections without one"() {
        given:
        def apiData = apiData(null, extent(API_SPATIAL, null))

        expect:
        configuredSpatialExtent(apiData, apiData.getCollections().get("foo")) == Optional.of(BoundingBox.of(6.0, 49.0, 8.0, 51.0, OgcCrs.CRS84))
    }

    def "spatial and temporal extent are resolved independently"() {
        given: 'a collection with a spatial extent only, an API with a temporal extent only'
        def apiData = apiData(extent(COLLECTION_SPATIAL, null), extent(null, API_INTERVAL))

        expect:
        configuredSpatialExtent(apiData, apiData.getCollections().get("foo")) == Optional.of(BoundingBox.of(7.0, 50.0, 7.1, 50.1, OgcCrs.CRS84))
        configuredTemporalExtent(apiData, apiData.getCollections().get("foo")) == Optional.of(TemporalExtent.of("2020-01-01T00:00:00Z", "2020-12-31T23:59:59Z"))
    }

    def "computed on a collection discards the extent of the API"() {
        given:
        def apiData = apiData(extent(COMPUTED, null), extent(API_SPATIAL, null))

        expect: 'no fixed extent, it is computed from the data'
        configuredSpatialExtent(apiData, apiData.getCollections().get("foo")).isEmpty()
    }

    def "nothing is configured, so the extent is left to the provider"() {
        given:
        def apiData = apiData(null, null)

        expect:
        configuredSpatialExtent(apiData, apiData.getCollections().get("foo")).isEmpty()
        configuredTemporalExtent(apiData, apiData.getCollections().get("foo")).isEmpty()
    }

    def "an unknown collection falls back to the API level"() {
        given:
        def apiData = apiData(null, extent(API_SPATIAL, null))

        expect:
        configuredSpatialExtent(apiData, null) == Optional.of(BoundingBox.of(6.0, 49.0, 8.0, 51.0, OgcCrs.CRS84))
    }

    static SpatialExtent spatial(double xmin, double ymin, double xmax, double ymax) {
        ImmutableSpatialExtent.builder().xmin(xmin).ymin(ymin).xmax(xmax).ymax(ymax).build()
    }

    static Extent extent(SpatialExtent spatial, de.ii.xtraplatform.features.domain.TemporalExtent temporal) {
        ImmutableExtent.builder()
                .spatial(Optional.ofNullable(spatial))
                .temporal(Optional.ofNullable(temporal))
                .build()
    }

    static OgcApiDataV2 apiData(Extent collectionExtent, Extent apiExtent) {
        new ImmutableOgcApiDataV2.Builder()
                .id("api")
                .serviceType("OGC_API")
                .addExtensions(new ImmutableFeaturesCoreConfiguration.Builder()
                        .enabled(true)
                        .extent(Optional.ofNullable(apiExtent))
                        .build())
                .putCollections("foo", new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                        .id("foo")
                        .label("foo")
                        .addExtensions(new ImmutableFeaturesCoreConfiguration.Builder()
                                .enabled(true)
                                .extent(Optional.ofNullable(collectionExtent))
                                .build())
                        .build())
                .build()
    }
}
