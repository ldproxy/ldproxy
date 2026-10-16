/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app

import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.SchemaValidator
import spock.lang.Specification

class PathParameterStoredQueryParameterNameSpec extends Specification {

    def apiData = new ImmutableOgcApiDataV2.Builder().id('countries').build()

    def 'the values are the parameters of the stored query'() {
        when:
        def parameter = new PathParameterStoredQueryParameterName(
                Stub(SchemaValidator), 'countries-over-time', ['year', 'name', 'output'])

        then:
        parameter.getName() == 'name'
        parameter.getValues(apiData) == ['name', 'output', 'year']
        parameter.getSchema(apiData).getEnum() == ['name', 'output', 'year']
    }

    def 'each stored query has its own parameter in the API definition'() {
        when:
        def a = new PathParameterStoredQueryParameterName(Stub(SchemaValidator), 'a', ['x'])
        def b = new PathParameterStoredQueryParameterName(Stub(SchemaValidator), 'b', ['y'])

        then:
        a.getId() != b.getId()

        and: 'it is not the id of a parameter of the stored query, e.g. a parameter "name"'
        a.getId() != 'name_a'
        a.getId().contains('.')
        a.isApplicable(apiData, '/search/a/parameters/{name}')
        !a.isApplicable(apiData, '/search/b/parameters/{name}')
        !a.isApplicable(apiData, '/search/{queryId}/parameters/{name}')
    }
}
