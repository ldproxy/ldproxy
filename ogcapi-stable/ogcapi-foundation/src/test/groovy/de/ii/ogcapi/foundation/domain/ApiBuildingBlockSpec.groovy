/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain

import spock.lang.Specification

class ApiBuildingBlockSpec extends Specification {

    static class TestBuildingBlock implements ApiBuildingBlock {
        @Override
        ExtensionConfiguration getDefaultConfiguration() {
            return new ImmutableFoundationConfiguration.Builder().enabled(false).build()
        }
    }

    static FeatureTypeConfigurationOgcApi collection(String id, Boolean blockEnabled, boolean collectionEnabled) {
        def builder = new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                .id(id)
                .label(id)
                .enabled(collectionEnabled)
        if (blockEnabled != null) {
            builder.addExtensions(new ImmutableFoundationConfiguration.Builder().enabled(blockEnabled).build())
        }
        return builder.build()
    }

    static OgcApiDataV2 api(boolean apiEnabled, List<FeatureTypeConfigurationOgcApi> collections) {
        def builder = new ImmutableOgcApiDataV2.Builder()
                .id('test')
                .serviceType('OGC_API')
                .addExtensions(new ImmutableFoundationConfiguration.Builder().enabled(apiEnabled).build())
        collections.each { builder.putCollections(it.id, it) }
        return builder.build()
    }

    def "a building block is enabled for an API if it is enabled for the API or an enabled collection"() {
        given:
        def buildingBlock = new TestBuildingBlock()
        def apiData = api(apiEnabled, collections)

        when:
        def enabled = buildingBlock.isEnabledForApi(apiData)

        then:
        enabled == expected

        where:
        apiEnabled | collections                                             || expected
        true       | []                                                      || true
        false      | []                                                      || false
        false      | [collection('a', null, true)]                           || false
        false      | [collection('a', true, true)]                           || true
        false      | [collection('a', false, true), collection('b', true, true)] || true
        false      | [collection('a', true, false)]                          || false
        true       | [collection('a', false, true)]                          || true
    }
}
