/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.sorting.app

import de.ii.ogcapi.collections.queryables.domain.QueryablesConfiguration.PathSeparator
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.foundation.infra.json.SchemaValidatorImpl
import de.ii.ogcapi.sorting.domain.ImmutableSortingConfiguration
import de.ii.ogcapi.sorting.domain.SortingConfiguration
import de.ii.xtraplatform.features.domain.FeatureQueries
import de.ii.xtraplatform.features.domain.FeatureSchema
import de.ii.xtraplatform.features.domain.ImmutableFeatureSchema
import de.ii.xtraplatform.features.domain.SchemaBase
import de.ii.xtraplatform.features.domain.transform.OnlySortables
import io.swagger.v3.oas.models.media.ArraySchema
import spock.lang.Specification

import java.util.function.Predicate

class QueryParameterSortbyFeaturesSpec extends Specification {

    static final String COLLECTION_ID = 'test'

    def 'the parameter is not available for a collection without sortable properties'() {
        given: 'sorting is enabled, but no property is included as a sortable'
        def parameter = createParameter()
        def apiData = createApiData(sortingConfiguration([], []))

        expect: 'the parameter is disabled for the collection'
        !parameter.isEnabledForApi(apiData, COLLECTION_ID)
    }

    def 'the parameter is not available if all sortable properties are excluded'() {
        given: 'all properties are included as sortables, but all of them are also excluded'
        def parameter = createParameter()
        def apiData = createApiData(sortingConfiguration(['*'], ['name', 'date']))

        expect: 'the parameter is disabled for the collection'
        !parameter.isEnabledForApi(apiData, COLLECTION_ID)
    }

    def 'only the sortable properties are accepted as sort keys'() {
        given: 'sorting is enabled with a single sortable property'
        def parameter = createParameter()
        def apiData = createApiData(sortingConfiguration(['name'], []))

        expect: 'the parameter is enabled for the collection'
        parameter.isEnabledForApi(apiData, COLLECTION_ID)

        and: 'the schema only allows the sortable property'
        ((ArraySchema) parameter.getSchema(apiData, COLLECTION_ID)).getItems().getEnum() == ['name', '+name', '-name']

        and: 'sort keys for the sortable property are valid'
        parameter.validate(apiData, Optional.of(COLLECTION_ID), ['-name']).isEmpty()

        and: 'a sort key for another property of the feature type is rejected'
        parameter.validate(apiData, Optional.of(COLLECTION_ID), ['date']).isPresent()
    }

    def 'a default sort order without sortable properties rejects all sort keys'() {
        given: 'sorting is enabled with a default sort order, but without sortables'
        def parameter = createParameter()
        def apiData = createApiData(sortingConfiguration([], [], ['name']))

        expect: 'the parameter is enabled for the collection'
        parameter.isEnabledForApi(apiData, COLLECTION_ID)

        and: 'no sort key is valid'
        ((ArraySchema) parameter.getSchema(apiData, COLLECTION_ID)).getMaxItems() == 0
        parameter.validate(apiData, Optional.of(COLLECTION_ID), ['name']).isPresent()

        and: 'the default sort order is applied when the parameter is absent'
        def sortKeys = parameter.parse((String) null, [:], null, Optional.of(apiData.getCollections().get(COLLECTION_ID)))
        sortKeys*.getField() == ['name']
    }

    QueryParameterSortbyFeatures createParameter() {
        FeatureQueries featureQueries = Stub()
        featureQueries.getSortablesSchema(_ as FeatureSchema, _ as List, _ as List, _ as String) >> {
            FeatureSchema schema, List<String> included, List<String> excluded, String pathSeparator ->
                schema.accept(new OnlySortables(included, excluded, pathSeparator, { path -> false } as Predicate))
        }
        FeaturesCoreProviders providers = Stub()
        providers.getFeatureSchema(_, _) >> Optional.of(featureSchema())
        providers.getFeatureProviderOrThrow(_, _, _) >> featureQueries
        return new QueryParameterSortbyFeatures(new SchemaValidatorImpl(), providers)
    }

    static FeatureSchema featureSchema() {
        return new ImmutableFeatureSchema.Builder()
                .name(COLLECTION_ID)
                .type(SchemaBase.Type.OBJECT)
                .sourcePath('/test')
                .putProperties2('name', new ImmutableFeatureSchema.Builder()
                        .type(SchemaBase.Type.STRING))
                .putProperties2('date', new ImmutableFeatureSchema.Builder()
                        .type(SchemaBase.Type.DATE))
                .build()
    }

    static OgcApiDataV2 createApiData(SortingConfiguration sortingConfiguration) {
        return new ImmutableOgcApiDataV2.Builder()
                .id('api')
                .serviceType('OGC_API')
                .putCollections(COLLECTION_ID, new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                        .id(COLLECTION_ID)
                        .label(COLLECTION_ID)
                        .enabled(true)
                        .addExtensions(sortingConfiguration)
                        .build())
                .build()
    }

    static SortingConfiguration sortingConfiguration(List<String> included, List<String> excluded, List<String> defaultSortby = []) {
        return new ImmutableSortingConfiguration.Builder()
                .enabled(true)
                .pathSeparator(PathSeparator.DOT)
                .included(included)
                .excluded(excluded)
                .defaultSortby(defaultSortby)
                .build()
    }
}
