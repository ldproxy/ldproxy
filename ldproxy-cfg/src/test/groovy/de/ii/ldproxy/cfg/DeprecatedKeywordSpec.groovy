/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg

import com.networknt.schema.Error
import com.networknt.schema.InputFormat
import com.networknt.schema.Schema
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.dialect.Dialect
import com.networknt.schema.dialect.DialectId
import com.networknt.schema.dialect.Dialects
import spock.lang.Shared
import spock.lang.Specification

class DeprecatedKeywordSpec extends Specification {

    @Shared SchemaRegistry registry

    def setupSpec() {
        Dialect dialect = Dialect.builder(DialectId.DRAFT_2020_12, Dialects.getDraft202012())
                .keyword(new DeprecatedKeyword())
                .build()
        registry = SchemaRegistry.withDefaultDialect(dialect)
    }

    def "deprecated:true on a property surfaces as a DeprecatedKeyword error"() {
        given:
        String schemaJson = '''
            {
              "type": "object",
              "properties": {
                "oldField": { "type": "string", "deprecated": true }
              }
            }
        '''
        String instance = '{ "oldField": "value" }'

        when:
        Schema schema = registry.getSchema(schemaJson)
        List<Error> errors = schema.validate(instance, InputFormat.JSON)

        then:
        errors.size() == 1
        DeprecatedKeyword.isDeprecated(errors[0])
        errors[0].message.contains('is deprecated')
    }

    def "deprecated:false on a property produces no error"() {
        given:
        String schemaJson = '''
            {
              "type": "object",
              "properties": {
                "okField": { "type": "string", "deprecated": false }
              }
            }
        '''
        String instance = '{ "okField": "value" }'

        when:
        Schema schema = registry.getSchema(schemaJson)
        List<Error> errors = schema.validate(instance, InputFormat.JSON)

        then:
        errors.isEmpty()
    }

    def "absent deprecated keyword produces no error"() {
        given:
        String schemaJson = '''
            {
              "type": "object",
              "properties": {
                "okField": { "type": "string" }
              }
            }
        '''
        String instance = '{ "okField": "value" }'

        when:
        Schema schema = registry.getSchema(schemaJson)
        List<Error> errors = schema.validate(instance, InputFormat.JSON)

        then:
        errors.isEmpty()
    }
}
