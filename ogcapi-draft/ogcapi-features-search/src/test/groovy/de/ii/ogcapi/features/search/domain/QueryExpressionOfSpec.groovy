/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain

import spock.lang.Specification

import java.nio.charset.StandardCharsets

class QueryExpressionOfSpec extends Specification {

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
        StoredQueryExpression.of(body('null'))

        then:
        thrown(IOException)
    }
}
