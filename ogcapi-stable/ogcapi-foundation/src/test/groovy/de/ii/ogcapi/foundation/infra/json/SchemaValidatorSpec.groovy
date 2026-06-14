/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.infra.json

import com.fasterxml.jackson.core.JsonParseException
import de.ii.ogcapi.foundation.infra.json.SchemaValidatorImpl
import spock.lang.Shared
import spock.lang.Specification

class SchemaValidatorSpec extends Specification {

    @Shared SchemaValidatorImpl schemaValidator

    def setupSpec() {
        schemaValidator = new SchemaValidatorImpl()
    }

    def "Validate a feature against its JSON schema"() {
        given:
        String schema = new File('src/test/resources/schema.json').getText()
        String feature = new File('src/test/resources/feature.json').getText()
        when:
        Optional<String> result = schemaValidator.validate(schema, feature)
        then:
        result.isEmpty()
    }

    def "Validate a feature against its JSON schema (draft 07)"() {
        given:
        String schema = new File('src/test/resources/schema-draft07.json').getText()
        String feature = new File('src/test/resources/feature.json').getText()
        when:
        Optional<String> result = schemaValidator.validate(schema, feature)
        then:
        result.isEmpty()
    }

    def "Validate an invalid feature against its JSON schema"() {
        given:
        String schema = new File('src/test/resources/schema.json').getText()
        String feature = new File('src/test/resources/feature2.json').getText()
        when:
        Optional<String> result = schemaValidator.validate(schema, feature)
        then:
        result.isPresent()
    }


    def "Validate and catch errors"() {
        when:
        schemaValidator.validate(schema, feature)
        then:
        thrown(exception)
        where:
        schema                                                | feature                                                | exception
        // invalid schema json, proper feature
        new File('src/test/resources/schema2.json').getText() | new File('src/test/resources/feature.json').getText()  | JsonParseException
        // invalid schema json, invalid feature json
        new File('src/test/resources/schema2.json').getText() | new File('src/test/resources/feature2.json').getText()  | JsonParseException
    }
}
