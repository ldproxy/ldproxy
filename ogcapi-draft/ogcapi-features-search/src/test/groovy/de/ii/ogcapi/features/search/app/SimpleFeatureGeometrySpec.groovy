/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app

import de.ii.xtraplatform.features.domain.ImmutableMultiFeatureQuery
import de.ii.xtraplatform.features.domain.ImmutableSubQuery
import de.ii.xtraplatform.features.domain.MultiFeatureQuery
import de.ii.xtraplatform.geometries.domain.GeometryType
import spock.lang.Specification

// A format that is restricted to Simple Features geometries (e.g. GeoJSON) silently drops curve
// geometries, so every sub-query has to request linearized geometries from the provider.
class SimpleFeatureGeometrySpec extends Specification {

    static MultiFeatureQuery query() {
        return ImmutableMultiFeatureQuery.builder()
                .addQueries(ImmutableSubQuery.builder().type('parcel').collectionId('parcels').build())
                .addQueries(ImmutableSubQuery.builder().type('building').collectionId('buildings').build())
                .limit(7)
                .build()
    }

    def 'curve geometries are linearized in every sub-query of a restricted format'() {
        given:
        MultiFeatureQuery query = query()

        when:
        MultiFeatureQuery result = SearchQueriesHandlerImpl.forceSimpleFeatureGeometryIfRequired(
                query, true, [GeometryType.CURVE_POLYGON, GeometryType.POINT] as Set)

        then:
        result.getQueries()*.forceSimpleFeatureGeometry() == [true, true]
        result.getQueries()*.getCollectionId() == ['parcels', 'buildings']
        result.getLimit() == 7
    }

    def 'the query is unchanged (#label)'() {
        given:
        MultiFeatureQuery query = query()

        when:
        MultiFeatureQuery result = SearchQueriesHandlerImpl.forceSimpleFeatureGeometryIfRequired(
                query, restricted, geometryTypes as Set)

        then:
        result.is(query)

        where:
        label                                  | restricted | geometryTypes
        'format supports curves'               | false      | [GeometryType.CURVE_POLYGON]
        'provider has only simple geometries'  | true       | [GeometryType.POLYGON, GeometryType.POINT]
    }
}
