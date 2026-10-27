/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain

import de.ii.ogcapi.foundation.infra.json.SchemaValidatorImpl
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.BooleanSchema
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.NumberSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import spock.lang.Shared
import spock.lang.Specification

/**
 * Locks the schema validation in {@link ParameterExtension#validateSchema}: a parameter whose
 * schema is unavailable must skip schema validation instead of failing the request with a
 * NullPointerException, and a value that does not match the type of the schema must be reported
 * by the schema validation, not as a JSON syntax error.
 */
class ParameterExtensionSpec extends Specification {

    @Shared
    SchemaValidator validator = new SchemaValidatorImpl()

    ParameterExtension parameter(Schema<?> parameterSchema) {
        return new ParameterExtension() {
            @Override
            String getName() { 'test' }

            @Override
            String getDescription() { 'test parameter' }

            @Override
            Schema<?> getSchema(OgcApiDataV2 apiData) { parameterSchema }

            @Override
            SchemaValidator getSchemaValidator() { validator }
        }
    }

    def 'a parameter without a schema skips schema validation instead of failing'() {
        given: 'a parameter whose schema is unavailable'
        def parameter = new ParameterExtension() {
            @Override
            String getName() { 'test' }

            @Override
            String getDescription() { 'test parameter' }

            @Override
            Schema<?> getSchema(OgcApiDataV2 apiData) { null }

            @Override
            SchemaValidator getSchemaValidator() { null }
        }

        when: 'a value is validated'
        def result = parameter.validate(null, Optional.empty(), ['foo'])

        then: 'validation reports no error and does not throw'
        result == Optional.empty()
    }

    def 'a value is accepted, if it is valid for the schema'() {
        expect:
        parameter(schema).validate(null, Optional.empty(), [value]) == Optional.empty()

        where:
        schema                                                || value
        new IntegerSchema()                                   || '45'
        new IntegerSchema()                                   || '-7'
        new NumberSchema()                                    || '4.5e3'
        new BooleanSchema()                                   || 'true'
        new StringSchema()                                    || 'C:\\path'
        new StringSchema()                                    || 'a"b'
        new ArraySchema().items(new IntegerSchema())          || '45,46'
    }

    def 'a value of the wrong type is rejected by the schema validation'() {
        when:
        def result = parameter(schema).validate(null, Optional.empty(), [value])

        then:
        result.isPresent()
        result.get().contains(expectedType)
        !result.get().contains('Unexpected character')
        !result.get().contains('Unrecognized token')

        where:
        schema                                                || value   || expectedType
        new IntegerSchema()                                   || '4*'    || 'integer'
        new IntegerSchema()                                   || 'abc'   || 'integer'
        new IntegerSchema()                                   || '4 5'   || 'integer'
        new IntegerSchema()                                   || '+5'    || 'integer'
        new NumberSchema()                                    || '0x10'  || 'number'
        new NumberSchema()                                    || '0.5 '  || 'number'
        new BooleanSchema()                                   || 'yes'   || 'boolean'
        new BooleanSchema()                                   || 'TRUE'  || 'boolean'
        new BooleanSchema()                                   || 'true ' || 'boolean'
        new ArraySchema().items(new IntegerSchema())          || '4*'    || 'integer'
        new ArraySchema().items(new IntegerSchema())          || '45,4a' || 'integer'
    }

    def 'the constraints of a string schema are still applied'() {
        expect:
        parameter(new StringSchema().pattern('^[A-Z]{2}$')).validate(null, Optional.empty(), [value]).isPresent() == invalid

        where:
        value  || invalid
        'AB'   || false
        'A"'   || true
        'ABC'  || true
    }
}
