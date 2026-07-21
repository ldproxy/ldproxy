/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.queryables.domain

import de.ii.ogcapi.foundation.domain.SchemaValidator
import de.ii.xtraplatform.cql.domain.Eq
import de.ii.xtraplatform.features.domain.SchemaBase
import io.swagger.v3.oas.models.media.StringSchema
import spock.lang.Specification

class QueryParameterTemplateQueryableSpec extends Specification {

    def queryable(SchemaBase.Type type) {
        return new ImmutableQueryParameterTemplateQueryable.Builder()
                .apiId("api")
                .collectionId("collection")
                .name("lzi.end")
                .description("a queryable")
                .schema(new StringSchema())
                .schemaValidator({ schema, value -> Optional.empty() } as SchemaValidator)
                .type(type)
                .build()
    }

    def 'invalid temporal value raises a client error, not a CQL parse error'() {
        given:
        def parameter = queryable(SchemaBase.Type.DATETIME)

        when:
        parameter.parse("*", [:], null, Optional.empty())

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("lzi.end")
        e.message.contains("*")
    }

    def 'valid temporal value parses to an equality expression'() {
        given:
        def parameter = queryable(SchemaBase.Type.DATETIME)

        when:
        def result = parameter.parse("2026-02-17T17:38:11Z", [:], null, Optional.empty())

        then:
        result instanceof Eq
    }

    def 'invalid numeric value raises a client error'() {
        given:
        def parameter = queryable(SchemaBase.Type.INTEGER)

        when:
        parameter.parse("abc", [:], null, Optional.empty())

        then:
        thrown(IllegalArgumentException)
    }
}
