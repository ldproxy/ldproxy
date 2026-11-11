/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app

import spock.lang.Specification

class McpSchemaVersionsSpec extends Specification {

    def "exclusive bounds of JSON Schema 2020-12 become those of OpenAPI 3.0, also nested"() {
        expect:
        McpSchemaVersions.toOpenApi30([type: "object", properties: [d: [type: "number", exclusiveMinimum: 0, maximum: 10]],
                                       items: [[type: "integer", exclusiveMaximum: 5]]]) ==
                [type: "object", properties: [d: [type: "number", exclusiveMinimum: true, maximum: 10, minimum: 0]],
                 items: [[type: "integer", exclusiveMaximum: true, maximum: 5]]]
    }

    def "exclusive bounds of OpenAPI 3.0 become those of JSON Schema 2020-12"() {
        expect:
        McpSchemaVersions.toJsonSchema2020(input) == expected

        where:
        input                                                        || expected
        [type: "number", minimum: 0, exclusiveMinimum: true]         || [type: "number", exclusiveMinimum: 0]
        [type: "number", minimum: 0, exclusiveMinimum: false]        || [type: "number", minimum: 0]
        [type: "number", maximum: 5, exclusiveMaximum: true]         || [type: "number", exclusiveMaximum: 5]
        [properties: [a: [minimum: 1, exclusiveMinimum: true]]]      || [properties: [a: [exclusiveMinimum: 1]]]
        [type: "string"]                                             || [type: "string"]
    }

    def "a round trip keeps a JSON Schema 2020-12 schema"() {
        given:
        def schema = [type: "number", exclusiveMinimum: 0, exclusiveMaximum: 100]

        expect:
        McpSchemaVersions.toJsonSchema2020(McpSchemaVersions.toOpenApi30(schema)) == schema
    }
}
