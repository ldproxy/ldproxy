/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.queryables.domain

import de.ii.ogcapi.foundation.domain.SchemaValidator
import de.ii.ogcapi.foundation.infra.json.SchemaValidatorImpl
import de.ii.xtraplatform.cql.domain.Eq
import de.ii.xtraplatform.features.domain.SchemaBase
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import spock.lang.Specification

class QueryParameterTemplateQueryableSpec extends Specification {

    static final String ID_PATTERN = '^DEXX[A-Za-z0-9]{12}$'


    static final SchemaValidator ACCEPT_ALL = { schema, value -> Optional.empty() } as SchemaValidator

    def queryable(SchemaBase.Type type,
                  Schema<?> schema = new StringSchema(),
                  SchemaBase.Type valueType = null,
                  SchemaValidator validator = ACCEPT_ALL) {
        def builder = new ImmutableQueryParameterTemplateQueryable.Builder()
                .apiId("api")
                .collectionId("collection")
                .name("lzi.end")
                .description("a queryable")
                .schema(schema)
                .schemaValidator(validator)
                .type(type)
        if (valueType != null) {
            builder.valueType(valueType)
        }
        return builder.build()
    }

    def idQueryable(SchemaValidator validator = ACCEPT_ALL) {
        return queryable(SchemaBase.Type.STRING, new StringSchema().pattern(ID_PATTERN), null, validator)
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

    def 'a value without a wildcard is validated against the schema of the property'() {
        given:
        def parameter = idQueryable()

        when:
        def schema = parameter.getSchemaForValidation(null, Optional.empty(), ["DEXX123456789012"])

        then:
        schema.pattern == ID_PATTERN
    }

    def 'a value with a wildcard is validated without the constraints of the property'() {
        given:
        def parameter = idQueryable()

        when:
        def schema = parameter.getSchemaForValidation(null, Optional.empty(), ["DEXX1*"])

        then:
        schema.type == "string"
        schema.pattern == null
    }

    def 'the constraints are kept for queryables that do not support wildcards'() {
        given:
        def parameter = queryable(SchemaBase.Type.INTEGER, new IntegerSchema().maximum(100g))

        when:
        def schema = parameter.getSchemaForValidation(null, Optional.empty(), ["1*"])

        then:
        schema.maximum == 100g
    }

    def 'a value with a wildcard for an array queryable is validated as a single pattern'() {
        given:
        def schema = new ArraySchema().items(new StringSchema().pattern(ID_PATTERN))
        def parameter = queryable(SchemaBase.Type.VALUE_ARRAY, schema, SchemaBase.Type.STRING)

        when:
        // the value is one ALIKE pattern, not a list of values, so it must not be split on the comma
        def validationSchema = parameter.getSchemaForValidation(null, Optional.empty(), ["DEXX1*,DEXX2"])

        then:
        validationSchema.type == "string"
    }

    def 'the constraints are kept for arrays that do not have string values'() {
        given:
        def schema = new ArraySchema().items(new IntegerSchema())
        def parameter = queryable(SchemaBase.Type.VALUE_ARRAY, schema, SchemaBase.Type.INTEGER)

        when:
        def validationSchema = parameter.getSchemaForValidation(null, Optional.empty(), ["1*"])

        then:
        validationSchema.type == "array"
    }

    def 'a value with a wildcard is accepted, a value that violates the constraints is rejected'() {
        given:
        def parameter = idQueryable(new SchemaValidatorImpl())

        expect:
        parameter.validate(null, Optional.empty(), [value]).isPresent() == invalid

        where:
        value              || invalid
        "DEXX123456789012" || false
        "DEXX1*"           || false
        "*"                || false
        "DEXX1"            || true
    }
}
