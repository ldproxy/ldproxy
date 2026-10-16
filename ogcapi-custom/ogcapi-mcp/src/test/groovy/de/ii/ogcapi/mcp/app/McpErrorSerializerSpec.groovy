/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.spec.McpError
import io.modelcontextprotocol.spec.McpSchema
import spock.lang.Specification

class McpErrorSerializerSpec extends Specification {

    def 'an error is written as a JSON-RPC error without the stack trace'() {
        given:
        ObjectMapper mapper = McpServerImpl.withMcpErrorSerializer(new ObjectMapper())
        McpError error = McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST).message("Invalid message format").build()

        when:
        JsonNode json = mapper.readTree(mapper.writeValueAsString(error))

        then:
        json.get("jsonrpc").asText() == "2.0"
        json.get("id").isNull()
        json.get("error").get("code").asInt() == McpSchema.ErrorCodes.INVALID_REQUEST
        json.get("error").get("message").asText() == "Invalid message format"
        !json.has("stackTrace")
        !json.toString().contains("stackTrace")
    }

    def 'the shared object mapper is not changed'() {
        given:
        ObjectMapper shared = new ObjectMapper()

        when:
        McpServerImpl.withMcpErrorSerializer(shared)

        then:
        shared.writeValueAsString(McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR).message("x").build()).contains("stackTrace")
    }
}
