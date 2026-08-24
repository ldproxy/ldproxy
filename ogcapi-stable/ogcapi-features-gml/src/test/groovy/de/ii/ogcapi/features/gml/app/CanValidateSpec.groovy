/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app

import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders
import de.ii.ogcapi.features.core.domain.FeaturesCoreValidation
import de.ii.ogcapi.features.gml.domain.GmlConfiguration
import de.ii.ogcapi.features.gml.domain.GmlWriterRegistry
import de.ii.ogcapi.features.gml.domain.ImmutableGmlConfiguration
import de.ii.ogcapi.foundation.domain.ExtensionRegistry
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.xtraplatform.blobs.domain.ResourceStore
import de.ii.xtraplatform.values.domain.ValueStore
import spock.lang.Specification

/**
 * {@code canValidate} decides whether {@code Prefer: handling=strict} validates a GML request body
 * and whether the "Handling Preference" conformance class is declared, so it has to answer the same
 * question {@code getOrBuildSchema} does: is there a schemaLocation to build a schema from?
 */
class CanValidateSpec extends Specification {

    private FeaturesFormatGml format() {
        return new FeaturesFormatGml(
                Stub(FeaturesCoreProviders),
                Stub(ValueStore),
                Stub(FeaturesCoreValidation),
                Stub(GmlWriterRegistry),
                Stub(ExtensionRegistry),
                Stub(ResourceStore))
    }

    private OgcApiDataV2 apiData(GmlConfiguration config) {
        def collectionData = Stub(FeatureTypeConfigurationOgcApi)
        collectionData.getExtension(GmlConfiguration.class) >> Optional.ofNullable(config)
        def apiData = Stub(OgcApiDataV2)
        apiData.getCollectionData('ap_pto') >> Optional.of(collectionData)
        return apiData
    }

    def 'a collection with a schemaLocation can be validated'() {
        given: 'a GML configuration that names the schema of its application schema'
        def config = new ImmutableGmlConfiguration.Builder()
                .enabled(true)
                .putSchemaLocations('http://example.org/ap', 'https://example.com/ap.xsd')
                .build()

        expect:
        format().canValidate(apiData(config), 'ap_pto')
    }

    def 'a GML configuration without schemaLocations cannot be validated'() {
        given: 'nothing to build a schema from'
        def config = new ImmutableGmlConfiguration.Builder().enabled(true).build()

        expect:
        !format().canValidate(apiData(config), 'ap_pto')
    }

    def 'a collection without a GML configuration cannot be validated'() {
        expect:
        !format().canValidate(apiData(null), 'ap_pto')
    }

    def 'an unknown collection cannot be validated'() {
        given:
        def apiData = Stub(OgcApiDataV2)
        apiData.getCollectionData(_) >> Optional.empty()

        expect:
        !format().canValidate(apiData, 'ap_pto')
    }
}
