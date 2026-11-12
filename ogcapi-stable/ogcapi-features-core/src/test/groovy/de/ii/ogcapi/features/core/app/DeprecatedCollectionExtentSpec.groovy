/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app

import dagger.Lazy
import de.ii.ogcapi.features.core.domain.DeprecatedExtents
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration
import de.ii.ogcapi.features.core.domain.ImmutableFeaturesCoreConfiguration
import de.ii.ogcapi.foundation.domain.CollectionExtent
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableCollectionExtent
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.foundation.domain.TemporalExtent
import de.ii.xtraplatform.base.domain.JacksonProvider
import de.ii.xtraplatform.base.domain.util.JacksonModules
import de.ii.xtraplatform.crs.domain.BoundingBox
import de.ii.xtraplatform.crs.domain.OgcCrs
import de.ii.xtraplatform.features.domain.Extent
import de.ii.xtraplatform.features.domain.ImmutableExtent
import de.ii.xtraplatform.features.domain.ImmutableSpatialExtent
import de.ii.xtraplatform.features.domain.SpatialExtent
import spock.lang.Specification

class DeprecatedCollectionExtentSpec extends Specification {

    static final BoundingBox LEGACY_BBOX = BoundingBox.of(5.76, 50.31, 9.47, 52.53, OgcCrs.CRS84)
    static final SpatialExtent LEGACY_SPATIAL = ImmutableSpatialExtent.builder().xmin(5.76d).ymin(50.31d).xmax(9.47d).ymax(52.53d).build()
    static final SpatialExtent CORE_SPATIAL = ImmutableSpatialExtent.builder().xmin(7.0d).ymin(50.0d).xmax(7.1d).ymax(50.1d).build()

    def "a collection extent in the format of earlier versions can still be loaded"() {

        given: 'a collection with epoch milliseconds, an explicit CRS and the computed flags'
        def json = '''{
          "id": "foo",
          "label": "foo",
          "extent": {
            "spatial": {"xmin": 5.76, "ymin": 50.31, "xmax": 9.47, "ymax": 52.53,
                        "epsgCrs": {"code": 4326, "forceAxisOrder": "NONE"}},
            "spatialComputed": false,
            "temporal": {"start": 1262304000000, "end": null},
            "temporalComputed": false
          }
        }'''

        when:
        FeatureTypeConfigurationOgcApi collection = new JacksonProvider({ -> Set.of() } as Lazy, false)
                .getDefaultObjectMapper()
                .copy()
                .registerModule(JacksonModules.DESERIALIZE_IMMUTABLE_BUILDER_NESTED)
                .readValue(json, FeatureTypeConfigurationOgcApi)

        then: 'the values are kept as they are'
        collection.getExtent().flatMap(CollectionExtent::getSpatial).map(BoundingBox::getXmin) == Optional.of(5.76d)
        collection.getExtent().flatMap(CollectionExtent::getTemporal).map(TemporalExtent::getStartInstant).map(Object::toString) == Optional.of("2010-01-01T00:00:00Z")
    }

    def "the deprecated extent of a collection is used for FEATURES_CORE and kept at runtime"() {

        given: 'a collection without a FEATURES_CORE configuration of its own'
        def data = apiData(collection(legacy(LEGACY_BBOX), null))

        when:
        def applied = DeprecatedExtents.apply(data, false)

        then:
        coreExtent(applied).flatMap(Extent::getSpatial) == Optional.of(LEGACY_SPATIAL)
        applied.getCollections().get("foo").getExtent().isPresent()
    }

    def "other FEATURES_CORE options of the collection are kept"() {

        given:
        def core = new ImmutableFeaturesCoreConfiguration.Builder().featureType("bar").build()
        def data = apiData(collection(legacy(LEGACY_BBOX), core))

        when:
        def applied = DeprecatedExtents.apply(data, false)

        then:
        coreConfig(applied).getFeatureType() == Optional.of("bar")
        coreExtent(applied).flatMap(Extent::getSpatial) == Optional.of(LEGACY_SPATIAL)
    }

    def "an extent in FEATURES_CORE of the collection takes precedence"() {

        given:
        def core = new ImmutableFeaturesCoreConfiguration.Builder()
                .extent(ImmutableExtent.builder().spatial(CORE_SPATIAL).build())
                .build()
        def data = apiData(collection(legacy(LEGACY_BBOX), core))

        when:
        def applied = DeprecatedExtents.apply(data, false)

        then:
        coreExtent(applied).flatMap(Extent::getSpatial) == Optional.of(CORE_SPATIAL)
    }

    def "a deprecated temporal extent in milliseconds becomes a date-time"() {

        given:
        def legacy = new ImmutableCollectionExtent.Builder()
                .temporal(TemporalExtent.of("1262304000000", null))
                .build()
        def data = apiData(collection(legacy, null))

        when:
        def applied = DeprecatedExtents.apply(data, false)

        then: 'an open end stays open'
        coreExtent(applied).flatMap(Extent::getTemporal).map { it.start } == Optional.of("2010-01-01T00:00:00Z")
        coreExtent(applied).flatMap(Extent::getTemporal).map { it.end } == Optional.empty()
    }

    def "the deprecated defaultExtent is used for FEATURES_CORE of the API"() {

        given:
        def data = new ImmutableOgcApiDataV2.Builder()
                .id("api")
                .serviceType("OGC_API")
                .defaultExtent(legacy(LEGACY_BBOX))
                .addExtensions(new ImmutableFeaturesCoreConfiguration.Builder().enabled(true).build())
                .build()

        when:
        def applied = DeprecatedExtents.apply(data, false)

        then: 'other options of FEATURES_CORE are kept'
        def core = applied.getExtensions().find { it instanceof FeaturesCoreConfiguration } as FeaturesCoreConfiguration
        core.getEnabled()
        core.getExtent().flatMap(Extent::getSpatial) == Optional.of(LEGACY_SPATIAL)
        applied.getDefaultExtent().isPresent()
    }

    def "an invalid deprecated extent is ignored instead of failing the API"() {

        given: 'an end before the start, which earlier versions only reported in STRICT mode'
        def data = new ImmutableOgcApiDataV2.Builder()
                .id("api")
                .serviceType("OGC_API")
                .defaultExtent(new ImmutableCollectionExtent.Builder()
                        .temporal(TemporalExtent.of("2020-12-31", "2020-01-01"))
                        .build())
                .build()

        expect:
        DeprecatedExtents.apply(data, false) == data
    }

    def "collections without the deprecated extent are not touched"() {

        given:
        def data = apiData(collection(null, null))

        expect:
        DeprecatedExtents.apply(data, false) == data
        !DeprecatedExtents.isPresent(data)
    }

    static CollectionExtent legacy(BoundingBox bbox) {
        new ImmutableCollectionExtent.Builder().spatial(bbox).spatialComputed(false).build()
    }

    static FeaturesCoreConfiguration coreConfig(OgcApiDataV2 data) {
        data.getCollections().get("foo").getExtensions().find { it instanceof FeaturesCoreConfiguration } as FeaturesCoreConfiguration
    }

    static Optional<Extent> coreExtent(OgcApiDataV2 data) {
        coreConfig(data).getExtent()
    }

    static FeatureTypeConfigurationOgcApi collection(CollectionExtent extent, FeaturesCoreConfiguration core) {
        def builder = new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                .id("foo")
                .label("foo")
                .extent(Optional.ofNullable(extent))
        if (core != null) {
            builder.addExtensions(core)
        }
        builder.build()
    }

    static OgcApiDataV2 apiData(FeatureTypeConfigurationOgcApi collection) {
        new ImmutableOgcApiDataV2.Builder()
                .id("api")
                .serviceType("OGC_API")
                .putCollections("foo", collection)
                .build()
    }
}
