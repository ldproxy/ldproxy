/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.sorting.app

import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders
import de.ii.ogcapi.features.core.domain.SchemaInfo
import de.ii.ogcapi.foundation.domain.OgcApi
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.xtraplatform.entities.domain.ValidationResult
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE
import de.ii.xtraplatform.features.domain.FeatureProvider
import de.ii.xtraplatform.features.domain.FeatureQueries
import de.ii.xtraplatform.features.domain.FeatureSchema
import de.ii.xtraplatform.features.domain.transform.OnlySortables
import spock.lang.Specification

import java.util.function.Predicate

import static de.ii.ogcapi.sorting.app.QueryParameterSortbyFeaturesSpec.COLLECTION_ID
import static de.ii.ogcapi.sorting.app.QueryParameterSortbyFeaturesSpec.createApiData
import static de.ii.ogcapi.sorting.app.QueryParameterSortbyFeaturesSpec.featureSchema
import static de.ii.ogcapi.sorting.app.QueryParameterSortbyFeaturesSpec.sortingConfiguration

class SortingBuildingBlockSpec extends Specification {

    static final String NOT_ELIGIBLE = "The sorting configuration for collection 'test' includes a sortable property 'name', but the property is not eligible."
    static final String DOES_NOT_EXIST = "The sorting configuration for collection 'test' includes property 'missing', but the property does not exist."

    def 'configured sortables that are ignored are reported without validation: #mode'() {
        given: 'the provider does not accept "name" as a sortable and "missing" is not a property'
        OgcApiDataV2 apiData = createApiData(sortingConfiguration(['name', 'date', 'missing'], []))

        when:
        ValidationResult result = buildingBlock().onStartup(api(apiData), mode)

        then:
        result.getWarnings() as List == warnings
        result.getStrictErrors() as List == strictErrors
        result.getErrors() as List == errors
        result.isSuccess() == success

        where:
        mode        || warnings                       | strictErrors   | errors           | success
        MODE.NONE   || [DOES_NOT_EXIST, NOT_ELIGIBLE] | []             | []               | true
        MODE.LAX    || []                             | [NOT_ELIGIBLE] | [DOES_NOT_EXIST] | false
        MODE.STRICT || []                             | [NOT_ELIGIBLE] | [DOES_NOT_EXIST] | false
    }

    def 'eligible sortables are not reported: #mode'() {
        given:
        OgcApiDataV2 apiData = createApiData(sortingConfiguration(['date'], []))

        when:
        ValidationResult result = buildingBlock().onStartup(api(apiData), mode)

        then:
        result.getWarnings().isEmpty()
        result.getStrictErrors().isEmpty()
        result.getErrors().isEmpty()

        where:
        mode << [MODE.NONE, MODE.LAX, MODE.STRICT]
    }

    OgcApi api(OgcApiDataV2 apiData) {
        OgcApi api = Stub(OgcApi)
        api.getData() >> apiData
        return api
    }

    SortingBuildingBlock buildingBlock() {
        // the provider only accepts 'date' as a sortable, e.g. because 'name' is in a joined table
        FeatureQueries featureQueries = Stub()
        featureQueries.getSortablesSchema(_ as FeatureSchema, _ as List, _ as List, _ as String) >> {
            FeatureSchema schema, List<String> included, List<String> excluded, String pathSeparator ->
                schema.accept(new OnlySortables(included.findAll { it == 'date' }, excluded, pathSeparator, { path -> false } as Predicate))
        }
        FeatureProvider provider = Stub()
        provider.supportsSorting() >> true
        FeaturesCoreProviders providers = Stub()
        providers.getFeatureProviderOrThrow(_ as OgcApiDataV2) >> provider
        providers.getFeatureProviderOrThrow(_, _, _) >> featureQueries
        providers.getFeatureSchema(_, _) >> Optional.of(featureSchema())
        SchemaInfo schemaInfo = Stub()
        schemaInfo.getPropertyNames(_ as OgcApiDataV2, COLLECTION_ID) >> ['name', 'date']
        return new SortingBuildingBlock(providers, schemaInfo)
    }
}
