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
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE
import de.ii.xtraplatform.features.domain.Extent
import de.ii.xtraplatform.features.domain.ImmutableExtent
import de.ii.xtraplatform.features.domain.ImmutableSpatialExtent
import spock.lang.Specification

class ConfiguredExtentValidationSpec extends Specification {

    def "the extent in FEATURES_CORE of the API is validated for each collection"() {

        given: 'a latitude out of range'
        def buildingBlock = new FeaturesCoreBuildingBlock(null, null, null)

        when:
        def result = buildingBlock.validateConfiguredExtents(apiData(extent(5.0, 40.0, 10.0, 95.0)), MODE.STRICT)

        then:
        result.getStrictErrors() == [
                "The spatial extent in collection 'foo' has a latitude value that is not between -90 and 90. Found: '95.0'."
        ] as Set
    }

    def "a minimum latitude above the maximum is an error"() {

        given:
        def buildingBlock = new FeaturesCoreBuildingBlock(null, null, null)

        when:
        def result = buildingBlock.validateConfiguredExtents(apiData(extent(5.0, 52.0, 10.0, 50.0)), MODE.STRICT)

        then:
        result.getStrictErrors() == [
                "The spatial extent in collection 'foo' has a maxmimum latitude value '50.0' that is lower than the minimum value '52.0'."
        ] as Set
    }

    def "a valid extent passes"() {

        given:
        def buildingBlock = new FeaturesCoreBuildingBlock(null, null, null)

        expect:
        buildingBlock.validateConfiguredExtents(apiData(extent(5.0, 50.0, 10.0, 52.0)), MODE.STRICT).getStrictErrors().isEmpty()
    }

    def "no validation without an API validation mode"() {

        given:
        def buildingBlock = new FeaturesCoreBuildingBlock(null, null, null)

        expect:
        buildingBlock.validateConfiguredExtents(apiData(extent(5.0, 40.0, 10.0, 95.0)), MODE.NONE).getStrictErrors().isEmpty()
    }

    def "coordinates are reported as they are, without locale-specific grouping"() {

        given: 'projected coordinates instead of CRS84'
        def buildingBlock = new FeaturesCoreBuildingBlock(null, null, null)

        when:
        def result = buildingBlock.validateConfiguredExtents(apiData(extent(280000.0, 5580000.0, 540000.0, 5900000.0)), MODE.STRICT)

        then:
        result.getStrictErrors().contains(
                "The spatial extent in collection 'foo' has a longitude value that is not between -180 and 180. Found: '280000.0'.")
    }

    static Extent extent(double xmin, double ymin, double xmax, double ymax) {
        ImmutableExtent.builder()
                .spatial(ImmutableSpatialExtent.builder().xmin(xmin).ymin(ymin).xmax(xmax).ymax(ymax).build())
                .build()
    }

    static OgcApiDataV2 apiData(Extent apiExtent) {
        new ImmutableOgcApiDataV2.Builder()
                .id("api")
                .serviceType("OGC_API")
                .addExtensions(new ImmutableFeaturesCoreConfiguration.Builder()
                        .enabled(true)
                        .extent(apiExtent)
                        .build())
                .putCollections("foo", new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                        .id("foo")
                        .label("foo")
                        .build())
                .build()
    }
}
