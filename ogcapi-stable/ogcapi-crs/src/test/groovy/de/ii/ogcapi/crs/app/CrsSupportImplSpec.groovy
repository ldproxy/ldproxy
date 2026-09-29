/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crs.app

import de.ii.ogcapi.crs.domain.ImmutableCrsConfiguration
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders
import de.ii.ogcapi.features.core.domain.ImmutableFeaturesCoreConfiguration
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.xtraplatform.base.domain.resiliency.OptionalVolatileCapability
import de.ii.xtraplatform.crs.domain.EpsgCrs
import de.ii.xtraplatform.crs.domain.OgcCrs
import de.ii.xtraplatform.features.domain.FeatureCrs
import de.ii.xtraplatform.features.domain.FeatureProvider
import spock.lang.Specification

class CrsSupportImplSpec extends Specification {

    static final EpsgCrs NATIVE_CRS = EpsgCrs.of(25832)
    static final EpsgCrs ADDITIONAL_CRS = EpsgCrs.of(4258)

    FeaturesCoreProviders providers = Stub()
    CrsSupportImpl subject = new CrsSupportImpl(providers)

    def setup() {
        FeatureCrs featureCrs = Stub()
        featureCrs.getNativeCrs() >> NATIVE_CRS
        OptionalVolatileCapability<FeatureCrs> capability = Stub()
        capability.isSupported() >> true
        capability.get() >> featureCrs
        FeatureProvider provider = Stub()
        provider.crs() >> capability
        providers.getFeatureProviderOrThrow(_ as OgcApiDataV2) >> provider
        providers.getFeatureProviderOrThrow(_ as OgcApiDataV2, _ as FeatureTypeConfigurationOgcApi) >> provider
    }

    def 'the default CRS is the only supported CRS without the CRS building block'() {
        given: 'an API that does not configure the CRS building block'
        OgcApiDataV2 apiData = api([featuresCore()])

        when: 'the supported CRSs of the collection are determined'
        List<EpsgCrs> supported = subject.getSupportedCrsList(apiData, apiData.getCollections().get('buildings'))

        then: 'only the default CRS is supported, not the native CRS of the provider'
        supported == [OgcCrs.CRS84]
        subject.isSupported(apiData, apiData.getCollections().get('buildings'), OgcCrs.CRS84)
        !subject.isSupported(apiData, apiData.getCollections().get('buildings'), NATIVE_CRS)
    }

    def 'the default CRS is the only supported CRS when the CRS building block is disabled'() {
        given: 'an API with the CRS building block explicitly disabled'
        OgcApiDataV2 apiData = api([featuresCore(), new ImmutableCrsConfiguration.Builder()
                .enabled(false)
                .addAdditionalCrs(ADDITIONAL_CRS)
                .build()])

        expect: 'neither the native nor the additional CRS is supported'
        subject.getSupportedCrsList(apiData, apiData.getCollections().get('buildings')) == [OgcCrs.CRS84]
        !subject.isSupported(apiData, apiData.getCollections().get('buildings'), NATIVE_CRS)
        !subject.isSupported(apiData, apiData.getCollections().get('buildings'), ADDITIONAL_CRS)
    }

    def 'the API-level and collection-level lists agree when the CRS building block is disabled'() {
        given:
        OgcApiDataV2 apiData = api([featuresCore()])

        expect: 'the overload without a collection reports the same single CRS'
        subject.getSupportedCrsList(apiData) == [OgcCrs.CRS84]
        !subject.isSupported(apiData, NATIVE_CRS)
    }

    def 'CRS84h is the only supported CRS when it is the default and the building block is disabled'() {
        given:
        OgcApiDataV2 apiData = api([new ImmutableFeaturesCoreConfiguration.Builder()
                .enabled(true)
                .defaultCrs(FeaturesCoreConfiguration.DefaultCrs.CRS84h)
                .build()])

        expect:
        subject.getSupportedCrsList(apiData, apiData.getCollections().get('buildings')) == [OgcCrs.CRS84h]
        !subject.isSupported(apiData, apiData.getCollections().get('buildings'), OgcCrs.CRS84)
    }

    def 'the native and the additional CRSs are supported with the CRS building block'() {
        given: 'an API with the CRS building block enabled'
        OgcApiDataV2 apiData = api([featuresCore(), new ImmutableCrsConfiguration.Builder()
                .enabled(true)
                .addAdditionalCrs(ADDITIONAL_CRS)
                .build()])

        when:
        List<EpsgCrs> supported = subject.getSupportedCrsList(apiData, apiData.getCollections().get('buildings'))

        then: 'the default, the native and the additional CRS are supported'
        supported == [OgcCrs.CRS84, NATIVE_CRS, ADDITIONAL_CRS]
        subject.isSupported(apiData, apiData.getCollections().get('buildings'), NATIVE_CRS)
        subject.isSupported(apiData, apiData.getCollections().get('buildings'), ADDITIONAL_CRS)
    }

    def 'the feature provider is not consulted when the CRS building block is disabled'() {
        given: 'a providers registry that fails when asked for a provider'
        FeaturesCoreProviders failing = Stub()
        failing.getFeatureProviderOrThrow(_ as OgcApiDataV2, _ as FeatureTypeConfigurationOgcApi) >> {
            throw new IllegalStateException('provider must not be needed')
        }
        CrsSupportImpl withoutProvider = new CrsSupportImpl(failing)
        OgcApiDataV2 apiData = api([featuresCore()])

        expect: 'the storage CRS is never looked up, so no provider is required'
        withoutProvider.getSupportedCrsList(apiData, apiData.getCollections().get('buildings')) == [OgcCrs.CRS84]
    }

    private static FeaturesCoreConfiguration featuresCore() {
        new ImmutableFeaturesCoreConfiguration.Builder().enabled(true).build()
    }

    private static OgcApiDataV2 api(List<ExtensionConfiguration> extensions) {
        ImmutableFeatureTypeConfigurationOgcApi collection =
                new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                        .id('buildings')
                        .label('Buildings')
                        .build()

        new ImmutableOgcApiDataV2.Builder()
                .id('buildings')
                .extensions(extensions)
                .putCollections(collection.getId(), collection)
                .build()
    }
}
