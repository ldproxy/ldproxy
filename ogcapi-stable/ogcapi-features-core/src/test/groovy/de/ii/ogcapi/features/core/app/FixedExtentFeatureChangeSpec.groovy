/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app

import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders
import de.ii.ogcapi.features.core.domain.ImmutableFeaturesCoreConfiguration
import de.ii.ogcapi.foundation.domain.CollectionExtent
import de.ii.ogcapi.foundation.domain.ImmutableCollectionExtent
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApi
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.foundation.domain.TemporalExtent
import de.ii.xtraplatform.entities.domain.maptobuilder.BuildableMap
import de.ii.xtraplatform.crs.domain.BoundingBox
import de.ii.xtraplatform.crs.domain.OgcCrs
import de.ii.xtraplatform.features.domain.FeatureChange
import de.ii.xtraplatform.features.domain.FeatureProviderDataV2
import de.ii.xtraplatform.features.domain.FeatureProviderEntity
import de.ii.xtraplatform.features.domain.FeatureSchema
import de.ii.xtraplatform.features.domain.FeatureTypeExtent
import de.ii.xtraplatform.features.domain.ImmutableFeatureTypeExtent
import de.ii.xtraplatform.features.domain.ImmutableSpatialExtent
import org.threeten.extra.Interval
import spock.lang.Specification

import java.time.Instant

class FixedExtentFeatureChangeSpec extends Specification {

    static final BoundingBox CHANGED_AREA = BoundingBox.of(7.0, 50.0, 7.1, 50.1, OgcCrs.CRS84)
    static final Interval CHANGED_INTERVAL = Interval.of(Instant.parse("2020-06-01T00:00:00Z"), Instant.parse("2020-07-01T00:00:00Z"))

    static final FeatureTypeExtent FIXED_PROVIDER_SPATIAL = ImmutableFeatureTypeExtent.builder()
            .spatial(ImmutableSpatialExtent.builder().xmin(6.0d).ymin(49.0d).xmax(8.0d).ymax(51.0d).build())
            .build()
    static final FeatureTypeExtent COMPUTED_TYPE_SPATIAL = ImmutableFeatureTypeExtent.builder()
            .spatial(ImmutableSpatialExtent.builder().computed(true).build())
            .build()

    OgcApi api = Mock()
    FeaturesCoreProviders providers = Stub()

    def "a computed extent grows with the changed features"() {
        given:
        withApi(null, null)
        withProvider(null, null)

        when:
        listener().onFeatureChange(change())

        then:
        1 * api.updateSpatialExtent("foo", CHANGED_AREA)
        1 * api.updateTemporalExtent("foo", TemporalExtent.of(CHANGED_INTERVAL))
    }

    def "an extent configured in FEATURES_CORE is not changed"() {
        given:
        withApi(fixed(), null)
        withProvider(null, null)

        when:
        listener().onFeatureChange(change())

        then:
        0 * api.updateSpatialExtent(*_)
        0 * api.updateTemporalExtent(*_)
    }

    def "an extent configured in the deprecated defaultExtent is not changed"() {
        given:
        withApi(null, fixed())
        withProvider(null, null)

        when:
        listener().onFeatureChange(change())

        then:
        0 * api.updateSpatialExtent(*_)
        0 * api.updateTemporalExtent(*_)
    }

    def "a fixed provider extent is not changed, the computed part still is"() {
        given:
        withApi(null, null)
        withProvider(null, FIXED_PROVIDER_SPATIAL)

        when:
        listener().onFeatureChange(change())

        then:
        0 * api.updateSpatialExtent(*_)
        1 * api.updateTemporalExtent("foo", _)
    }

    def "a type that asks for a computed extent ignores the fixed provider extent"() {
        given:
        withApi(null, null)
        withProvider(COMPUTED_TYPE_SPATIAL, FIXED_PROVIDER_SPATIAL)

        when:
        listener().onFeatureChange(change())

        then:
        1 * api.updateSpatialExtent("foo", CHANGED_AREA)
    }

    def listener() {
        new FeaturesCoreBuildingBlock(providers, null, null).onFeatureChange(api)
    }

    FeatureChange change() {
        Stub(FeatureChange) {
            getAction() >> FeatureChange.Action.UPDATE
            getFeatureType() >> "foo"
            getFeatureIds() >> ["1"]
            getNewBoundingBox() >> Optional.of(CHANGED_AREA)
            getNewInterval() >> Optional.of(CHANGED_INTERVAL)
            getModified() >> Instant.now()
        }
    }

    static CollectionExtent fixed() {
        new ImmutableCollectionExtent.Builder()
                .spatial(BoundingBox.of(6.0, 49.0, 8.0, 51.0, OgcCrs.CRS84))
                .temporal(TemporalExtent.of("2020-01-01", "2020-12-31"))
                .build()
    }

    void withApi(CollectionExtent featuresCoreExtent, CollectionExtent defaultExtent) {
        OgcApiDataV2 apiData = new ImmutableOgcApiDataV2.Builder()
                .id("api")
                .serviceType("OGC_API")
                .defaultExtent(Optional.ofNullable(defaultExtent))
                .putCollections("foo", new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                        .id("foo")
                        .label("foo")
                        .addExtensions(new ImmutableFeaturesCoreConfiguration.Builder()
                                .enabled(true)
                                .extent(Optional.ofNullable(featuresCoreExtent))
                                .build())
                        .build())
                .build()
        api.getData() >> apiData
    }

    void withProvider(FeatureTypeExtent typeExtent, FeatureTypeExtent providerExtent) {
        FeatureSchema schema = Stub() {
            getExtent() >> Optional.ofNullable(typeExtent)
        }
        FeatureProviderDataV2 providerData = Stub() {
            getTypes() >> Stub(BuildableMap) { get("foo") >> schema }
            getExtent() >> Optional.ofNullable(providerExtent)
        }
        FeatureProviderEntity provider = Stub() {
            getData() >> providerData
        }
        providers.getFeatureProvider(_, _) >> Optional.of(provider)
    }
}
