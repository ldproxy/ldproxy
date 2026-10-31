/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app

import de.ii.ogcapi.features.core.domain.ImmutableFeaturesCoreConfiguration
import de.ii.ogcapi.foundation.domain.CollectionExtent
import de.ii.ogcapi.foundation.domain.ImmutableCollectionExtent
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.foundation.domain.TemporalExtent
import de.ii.xtraplatform.crs.domain.BoundingBox
import de.ii.xtraplatform.crs.domain.OgcCrs
import spock.lang.Specification

import static de.ii.ogcapi.features.core.app.FeaturesCoreBuildingBlock.getConfiguredExtent

class ConfiguredExtentSpec extends Specification {

    static final BoundingBox COLLECTION_BBOX = BoundingBox.of(7.0, 50.0, 7.1, 50.1, OgcCrs.CRS84)
    static final BoundingBox API_BBOX = BoundingBox.of(6.0, 49.0, 8.0, 51.0, OgcCrs.CRS84)
    static final BoundingBox DEFAULT_BBOX = BoundingBox.of(5.0, 47.0, 15.0, 55.0, OgcCrs.CRS84)
    static final TemporalExtent API_INTERVAL = TemporalExtent.of("2020-01-01", "2020-12-31")
    static final TemporalExtent DEFAULT_INTERVAL = TemporalExtent.of("2000-01-01", null)

    def "the extent of the collection takes precedence"() {
        given:
        def apiData = apiData(extent(COLLECTION_BBOX, null), extent(API_BBOX, null), extent(DEFAULT_BBOX, null))

        expect:
        spatial(apiData) == Optional.of(COLLECTION_BBOX)
    }

    def "the extent of the API applies to collections without one"() {
        given:
        def apiData = apiData(null, extent(API_BBOX, null), extent(DEFAULT_BBOX, null))

        expect:
        spatial(apiData) == Optional.of(API_BBOX)
    }

    def "the deprecated defaultExtent is still honored"() {
        given:
        def apiData = apiData(null, null, extent(DEFAULT_BBOX, DEFAULT_INTERVAL))

        expect:
        spatial(apiData) == Optional.of(DEFAULT_BBOX)
        temporal(apiData) == Optional.of(DEFAULT_INTERVAL)
    }

    def "spatial and temporal extent are resolved independently"() {
        given:
        def apiData = apiData(extent(COLLECTION_BBOX, null), extent(null, API_INTERVAL), null)

        expect:
        spatial(apiData) == Optional.of(COLLECTION_BBOX)
        temporal(apiData) == Optional.of(API_INTERVAL)
    }

    def "nothing is configured, so the extent is left to the provider"() {
        given:
        def apiData = apiData(null, null, null)

        expect:
        spatial(apiData).isEmpty()
        temporal(apiData).isEmpty()
    }

    def "an unknown collection falls back to the API level"() {
        given:
        def apiData = apiData(null, extent(API_BBOX, null), null)

        expect:
        getConfiguredExtent(apiData, null, CollectionExtent::getSpatial) == Optional.of(API_BBOX)
    }

    static Optional<BoundingBox> spatial(OgcApiDataV2 apiData) {
        getConfiguredExtent(apiData, apiData.getCollections().get("foo"), CollectionExtent::getSpatial)
    }

    static Optional<TemporalExtent> temporal(OgcApiDataV2 apiData) {
        getConfiguredExtent(apiData, apiData.getCollections().get("foo"), CollectionExtent::getTemporal)
    }

    static CollectionExtent extent(BoundingBox spatial, TemporalExtent temporal) {
        new ImmutableCollectionExtent.Builder()
                .spatial(Optional.ofNullable(spatial))
                .temporal(Optional.ofNullable(temporal))
                .build()
    }

    static OgcApiDataV2 apiData(CollectionExtent collectionExtent, CollectionExtent apiExtent, CollectionExtent defaultExtent) {
        new ImmutableOgcApiDataV2.Builder()
                .id("api")
                .serviceType("OGC_API")
                .defaultExtent(Optional.ofNullable(defaultExtent))
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
