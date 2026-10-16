/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain

import de.ii.ogcapi.foundation.domain.SchemaValidator
import io.swagger.v3.oas.models.media.StringSchema
import spock.lang.Specification

class QueryParameterTemplateParameterSpec extends Specification {

    def parameter(StringSchema schema) {
        return new ImmutableQueryParameterTemplateParameter.Builder()
                .apiId("api")
                .queryId("query")
                .name("beginn")
                .description("the start")
                .schema(schema)
                .schemaValidator(Stub(SchemaValidator))
                .build()
    }

    def 'a date-time value with an offset is converted to UTC'() {
        expect:
        parameter(new StringSchema().format("date-time")).parse(value, [:], null, Optional.empty()) == expected

        where:
        value                        || expected
        "2020-01-01T10:00:00+01:00"  || "2020-01-01T09:00:00Z"
        "2020-01-01T10:00:00.5-08:20" || "2020-01-01T18:20:00.500Z"
        "2020-01-01T10:00:00Z"       || "2020-01-01T10:00:00Z"
        "not a timestamp"            || "not a timestamp"
    }

    def 'a string without the format date-time is not changed'() {
        expect:
        parameter(new StringSchema()).parse("2020-01-01T10:00:00+01:00", [:], null, Optional.empty()) == "2020-01-01T10:00:00+01:00"
    }
}
