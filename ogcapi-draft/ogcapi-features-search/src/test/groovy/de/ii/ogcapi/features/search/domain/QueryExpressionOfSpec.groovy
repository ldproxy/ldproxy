/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain

import de.ii.xtraplatform.cql.app.CqlImpl
import de.ii.xtraplatform.cql.domain.Cql
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class QueryExpressionOfSpec extends Specification {

    static final Cql CQL = new CqlImpl()

    static InputStream body(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
    }

    def 'the JSON literal null is not a query expression'() {
        when:
        QueryExpression.of(body('null'))

        then:
        IOException e = thrown()
        e.getMessage() == 'the request body does not contain a query expression'
    }

    def 'the JSON literal null is not a stored query expression'() {
        when:
        StoredQueryExpression.of(body('null'), 'q1', CQL)

        then:
        thrown(IOException)
    }

    def 'a stored query in the format of the stored query files gets the id of the path'() {
        when:
        StoredQueryExpression query = StoredQueryExpression.of(body("""{
            ${idMember}
            "title": "Countries",
            "collections": ["boundary"],
            "filter": {"op": "=", "args": [{"property": "name"}, {"\$parameter": {"\$ref": "#/parameters/name"}}]},
            "parameters": {"name": {"type": "string"}},
            "limit": 10
        }"""), 'countries', CQL)

        then:
        query.getId() == 'countries'
        query.getTitle() == Optional.of('Countries')
        query.getCollections().collect { it.getValue().orElse(null) } == ['boundary']
        query.getParameters().keySet() == ['name'] as Set
        query.getLimit().flatMap { it.getValue() } == Optional.of(10)
        // derived as for the stored query files, used to validate with the default values
        query.getAllParameters().keySet() == ['name'] as Set

        where:
        idMember << ['', '"id": "other",']
    }

    def 'a body that is not a JSON object or has invalid members is rejected'() {
        when:
        StoredQueryExpression.of(body(json), 'q1', CQL)

        then:
        thrown(IOException)

        where:
        json << ['', '[]', '"query"', '{"collections": ["boundary"], "limit": "ten"}']
    }
}
