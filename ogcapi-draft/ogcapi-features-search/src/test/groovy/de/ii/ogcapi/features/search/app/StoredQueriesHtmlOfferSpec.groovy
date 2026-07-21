/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app

import de.ii.ogcapi.features.search.domain.ImmutableParameterValue
import de.ii.ogcapi.features.search.domain.ImmutableStoredQueryExpression
import de.ii.ogcapi.features.search.domain.ImmutableStringOrParameter
import de.ii.ogcapi.features.search.domain.StoredQueryExpression
import de.ii.ogcapi.features.search.domain.StringOrParameter
import de.ii.xtraplatform.crs.domain.OgcCrs
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaRef
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaString
import spock.lang.Specification

// The stored queries page only offers HTML for queries that can actually be executed as HTML:
// paging enabled and the default CRS. This locks the static check against the runtime rule in
// SearchQueriesHandlerImpl.executeQuery (406 for HTML otherwise).
class StoredQueriesHtmlOfferSpec extends Specification {

    static final String CRS84_URI = 'http://www.opengis.net/def/crs/OGC/1.3/CRS84'
    static final String UTM32_URI = 'http://www.opengis.net/def/crs/EPSG/0/25832'
    static final String HEIGHT_URI = 'http://www.opengis.net/def/crs/EPSG/0/7837'

    static StringOrParameter literal(String value) {
        return new ImmutableStringOrParameter.Builder().value(value).build()
    }

    static StringOrParameter parameter(String name) {
        return new ImmutableStringOrParameter.Builder()
                .parameter(new ImmutableParameterValue.Builder()
                        .name(name)
                        .schema(new ImmutableJsonSchemaRef.Builder().ref('#/parameters/' + name).build())
                        .build())
                .build()
    }

    def 'HTML is offered iff paging is enabled and the query uses the default CRS (#label)'() {
        given: 'a stored query expression'
        def builder = new ImmutableStoredQueryExpression.Builder()
                .id('test-query')
                .addCollections(literal('ax_test'))
        if (paging != null) {
            builder.supportPaging(paging)
        }
        if (crs != null) {
            builder.crs(crs)
            if (crs.getParameter().isPresent()) {
                builder.putParameters('crs', new ImmutableJsonSchemaString.Builder().build())
            }
        }
        if (verticalCrs != null) {
            builder.verticalCrs(verticalCrs)
        }
        StoredQueryExpression query = builder.build()

        expect:
        SearchQueriesHandlerImpl.offersHtml(query, OgcCrs.CRS84) == expected

        where:
        label                        | paging | crs                | verticalCrs         || expected
        'single-shot by default'     | null   | null               | null                || false
        'paging disabled'            | false  | null               | null                || false
        'paged, no CRS'              | true   | null               | null                || true
        'paged, default CRS'         | true   | literal(CRS84_URI) | null                || true
        'paged, non-default CRS'     | true   | literal(UTM32_URI) | null                || false
        'paged, parameterized CRS'   | true   | parameter('crs')   | null                || false
        'paged, composed 3D CRS'     | true   | literal(CRS84_URI) | literal(HEIGHT_URI) || false
        'vertical CRS without CRS'   | true   | null               | literal(HEIGHT_URI) || true
        'unpaged, non-default CRS'   | false  | literal(UTM32_URI) | null                || false
    }
}
