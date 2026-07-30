/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.profile.crs.app

import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders
import de.ii.ogcapi.foundation.domain.ExtensionRegistry
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.xtraplatform.crs.domain.EpsgCrs
import de.ii.xtraplatform.features.domain.FeatureCrs
import spock.lang.Specification

class ProfileCrsOriginalSpec extends Specification {

    static final String COLLECTION_ID = 'ax_punktortau'
    static final EpsgCrs NATIVE_CRS = EpsgCrs.of(25832)

    def 'the response CRS is the CRS of the feature provider'() {

        given: 'a collection whose provider stores the positions in EPSG:25832'
        def collectionData = Stub(FeatureTypeConfigurationOgcApi)
        def apiData = Stub(OgcApiDataV2)
        apiData.getCollectionData(COLLECTION_ID) >> Optional.of(collectionData)
        def featureCrs = Stub(FeatureCrs)
        featureCrs.getNativeCrs() >> NATIVE_CRS
        def providers = Stub(FeaturesCoreProviders)
        providers.getFeatureProvider(apiData, collectionData, _) >> Optional.of(featureCrs)
        def profile = new ProfileCrsOriginal(Stub(ExtensionRegistry), providers)

        when: 'the profile is asked for the CRS of the response'
        def crs = profile.getResponseCrs(apiData, COLLECTION_ID)

        then: 'it is the CRS of the provider, not the default CRS of the API'
        crs.isPresent()
        crs.get() == NATIVE_CRS
    }

    def 'no response CRS without a CRS in the feature provider'() {

        given: 'a collection whose provider does not provide a CRS'
        def collectionData = Stub(FeatureTypeConfigurationOgcApi)
        def apiData = Stub(OgcApiDataV2)
        apiData.getCollectionData(COLLECTION_ID) >> Optional.of(collectionData)
        def providers = Stub(FeaturesCoreProviders)
        providers.getFeatureProvider(apiData, collectionData, _) >> Optional.empty()
        def profile = new ProfileCrsOriginal(Stub(ExtensionRegistry), providers)

        when: 'the profile is asked for the CRS of the response'
        def crs = profile.getResponseCrs(apiData, COLLECTION_ID)

        then: 'the default CRS of the API is not changed'
        crs.isEmpty()
    }

    def 'no response CRS for an unknown collection'() {

        given: 'a collection that is not part of the API'
        def apiData = Stub(OgcApiDataV2)
        apiData.getCollectionData(COLLECTION_ID) >> Optional.empty()
        def profile = new ProfileCrsOriginal(Stub(ExtensionRegistry), Stub(FeaturesCoreProviders))

        when: 'the profile is asked for the CRS of the response'
        def crs = profile.getResponseCrs(apiData, COLLECTION_ID)

        then: 'the default CRS of the API is not changed'
        crs.isEmpty()
    }
}
