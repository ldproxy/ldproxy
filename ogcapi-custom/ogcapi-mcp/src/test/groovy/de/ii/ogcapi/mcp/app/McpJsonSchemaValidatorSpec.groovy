/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app

import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper
import io.modelcontextprotocol.server.McpServer
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities
import spock.lang.Specification

class McpJsonSchemaValidatorSpec extends Specification {

    static final ObjectMapper MAPPER = new ObjectMapper()

    static final Map<String, Object> SCHEMA = [
            type      : "object",
            properties: [
                    name: [type: "string"],
                    area: [type: "number"]
            ],
            required  : ["name"]
    ]

    // the SDK default validator is compiled against json-schema-validator 1.5.x, whose
    // SpecVersion was removed in 2.0; instantiating it threw NoClassDefFoundError, which
    // surfaced as an HTTP 500 on the first request instead of failing at build time
    def 'server can be built with the validator'() {
        given:
        def transport = HttpServletStatelessServerTransportJavaX.builder()
                .jsonMapper(new JacksonMcpJsonMapper(MAPPER))
                .messageEndpoint("/mcp")
                .build()

        when:
        def server = McpServer.sync(transport)
                .serverInfo("test", "1.0.0")
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .jsonSchemaValidator(new McpJsonSchemaValidator(MAPPER))
                .build()

        then:
        server.getServerInfo().name() == "test"
        server.listTools().isEmpty()

        cleanup:
        server.close()
    }

    def 'structured content matching the schema is valid'() {
        given:
        def validator = new McpJsonSchemaValidator(MAPPER)

        when:
        def response = validator.validate(SCHEMA, [name: "France", area: 532308.3])

        then:
        response.valid()
        response.errorMessage() == null
        MAPPER.readTree(response.jsonStructuredOutput()).get("name").asText() == "France"
    }

    def 'structured content violating the schema is invalid'() {
        given:
        def validator = new McpJsonSchemaValidator(MAPPER)

        when:
        def response = validator.validate(SCHEMA, content)

        then:
        !response.valid()
        response.errorMessage().contains("does not match tool outputSchema")

        where:
        description        | content
        'wrong type'       | [name: "France", area: "not a number"]
        'missing required' | [area: 532308.3]
    }

    def 'string content is parsed as json'() {
        given:
        def validator = new McpJsonSchemaValidator(MAPPER)

        when:
        def response = validator.validate(SCHEMA, '{"name":"France","area":532308.3}')

        then:
        response.valid()
    }

    def 'malformed string content is invalid rather than throwing'() {
        given:
        def validator = new McpJsonSchemaValidator(MAPPER)

        when:
        def response = validator.validate(SCHEMA, "not json at all")

        then:
        !response.valid()
        response.errorMessage() != null
    }

    def 'a cached schema stays usable across calls'() {
        given:
        def validator = new McpJsonSchemaValidator(MAPPER)

        expect:
        validator.validate(SCHEMA, [name: "France"]).valid()
        validator.validate(SCHEMA, [name: "Spain"]).valid()
        !validator.validate(SCHEMA, [area: 1.0]).valid()
    }

    def 'null arguments are rejected'() {
        given:
        def validator = new McpJsonSchemaValidator(MAPPER)

        when:
        validator.validate(schema, content)

        then:
        thrown(IllegalArgumentException)

        where:
        schema | content
        null   | [name: "France"]
        SCHEMA | null
    }
}
