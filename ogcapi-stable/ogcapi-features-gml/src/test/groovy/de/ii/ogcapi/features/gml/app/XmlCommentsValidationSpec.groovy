/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app

import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders
import de.ii.ogcapi.features.gml.domain.ImmutableGmlConfiguration
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApi
import de.ii.xtraplatform.entities.domain.ValidationResult
import de.ii.xtraplatform.features.domain.FeatureSchema
import de.ii.xtraplatform.features.domain.ImmutableFeatureSchema
import de.ii.xtraplatform.features.domain.SchemaBase.Scope
import de.ii.xtraplatform.features.domain.SchemaBase.Type
import spock.lang.Specification

/**
 * A property encoded as an annotation comment cannot be decoded from a GML request body, so
 * {@code xmlComments} is restricted to properties that are excluded from the {@code RECEIVABLE}
 * scope. The restriction is what makes the write-only asymmetry harmless, so it is enforced at
 * startup rather than documented only.
 */
class XmlCommentsValidationSpec extends Specification {

    static final String COLLECTION = 'ap_pto'

    private static FeatureSchema featureType(boolean updatedIsReceivable) {
        def updated = new ImmutableFeatureSchema.Builder()
                .name('_updated')
                .type(Type.DATETIME)
        if (!updatedIsReceivable) {
            updated.addExcludedScopes(Scope.RECEIVABLE)
        }
        return new ImmutableFeatureSchema.Builder()
                .name('AP_PTO')
                .type(Type.OBJECT)
                .putPropertyMap('_updated', updated.build())
                .putPropertyMap('art',
                        new ImmutableFeatureSchema.Builder().name('art').type(Type.STRING).build())
                .build()
    }

    private static FeatureTypeConfigurationOgcApi collection(List<String> xmlComments) {
        return new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                .id(COLLECTION)
                .label(COLLECTION)
                .addExtensions(new ImmutableGmlConfiguration.Builder()
                        .enabled(true)
                        .xmlComments(xmlComments)
                        .build())
                .build()
    }

    private ValidationResult validate(List<String> xmlComments, FeatureSchema schema) {
        def collectionData = collection(xmlComments)
        // a real apiData: getCollections() is a BuildableMap, which a stub cannot produce
        def apiData = new ImmutableOgcApiDataV2.Builder()
                .id('alkis')
                .serviceType('OGC_API')
                .putCollections(COLLECTION, collectionData)
                .build()
        def api = Stub(OgcApi)
        api.getData() >> apiData
        def providers = Stub(FeaturesCoreProviders)
        providers.getFeatureSchema(apiData, collectionData) >> Optional.ofNullable(schema)
        return new GmlBuildingBlock(providers).onStartup(api, ValidationResult.MODE.NONE)
    }

    def 'a non-receivable property is accepted'() {
        when:
        def result = validate(['_updated'], featureType(false))

        then:
        result.isSuccess()
    }

    def 'a receivable property is rejected'() {
        when:
        def result = validate(['_updated'], featureType(true))

        then: 'the error names the property and the scope to exclude'
        !result.isSuccess()
        result.getErrors().size() == 1
        result.getErrors().first().contains('_updated')
        result.getErrors().first().contains('RECEIVABLE')
    }

    def 'a property the feature type does not have is rejected'() {
        when:
        def result = validate(['nichtVorhanden'], featureType(false))

        then:
        !result.isSuccess()
        result.getErrors().first().contains('no such property')
    }

    def 'a missing provider schema is reported'() {
        when:
        def result = validate(['_updated'], null)

        then:
        !result.isSuccess()
        result.getErrors().first().contains('no provider')
    }

    def 'nothing is checked when the option is not used'() {
        when:
        def result = validate([], featureType(true))

        then:
        result.isSuccess()
    }
}
