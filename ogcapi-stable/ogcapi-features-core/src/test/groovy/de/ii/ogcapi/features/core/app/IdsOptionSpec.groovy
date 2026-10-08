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
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.foundation.domain.SchemaValidator
import spock.lang.Specification

class IdsOptionSpec extends Specification {

    static final String CONFORMANCE_CLASS = 'http://www.opengis.net/spec/ogcapi-features-1/1.1/conf/ids'

    def 'the option defaults to enabled'() {
        expect:
        new ImmutableFeaturesCoreConfiguration.Builder().build().supportsIds()
        !new ImmutableFeaturesCoreConfiguration.Builder().ids(false).build().supportsIds()
    }

    def 'the conformance class is only declared, if no collection disables the option'() {
        given:
        OgcApiDataV2 apiData = apiData(a: idsA, b: idsB)

        when:
        List<String> conformanceClasses = new FeaturesCore().getConformanceClassUris(apiData)

        then:
        conformanceClasses.contains(CONFORMANCE_CLASS) == expected

        where:
        idsA  | idsB  || expected
        null  | null  || true
        true  | true  || true
        true  | false || false
    }

    def 'the parameter is only available for collections that enable the option'() {
        given:
        OgcApiDataV2 mixed = apiData(a: true, b: false)
        OgcApiDataV2 disabled = apiData(a: false)
        QueryParameterIds ids = new QueryParameterIds(Stub(SchemaValidator), Stub(FeaturesCoreProviders))

        expect:
        ids.isEnabledForApi(mixed, 'a')
        !ids.isEnabledForApi(mixed, 'b')
        ids.isEnabledForApi(mixed)
        !ids.isEnabledForApi(disabled)
    }

    private static OgcApiDataV2 apiData(Map<String, Boolean> ids) {
        // a real apiData: getCollections() is a BuildableMap, which a stub cannot produce
        def builder = new ImmutableOgcApiDataV2.Builder()
                .id('api')
                .serviceType('OGC_API')
        ids.each { id, enabled ->
            builder.putCollections(id, new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                    .id(id)
                    .label(id)
                    .addExtensions(new ImmutableFeaturesCoreConfiguration.Builder()
                            .enabled(true)
                            .ids(enabled)
                            .build())
                    .build())
        }
        return builder.build()
    }
}
