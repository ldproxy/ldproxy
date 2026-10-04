/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app

import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper
import io.modelcontextprotocol.json.schema.jackson2.DefaultJsonSchemaValidator
import io.modelcontextprotocol.server.McpServer
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification
import io.modelcontextprotocol.server.McpStatelessSyncServer
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities
import io.modelcontextprotocol.spec.McpSchema.Tool
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import spock.lang.Specification

class McpSdkSpec extends Specification {

    static final ObjectMapper MAPPER = new ObjectMapper()

    static final Map<String, Object> SCHEMA = [
            type      : "object",
            properties: [
                    name: [type: "string"],
                    area: [type: "number"]
            ],
            required  : ["name"]
    ]

    HttpServletStatelessServerTransport transport
    McpStatelessSyncServer server

    def setup() {
        def jsonMapper = new JacksonMcpJsonMapper(MAPPER)
        transport = HttpServletStatelessServerTransport.builder()
                .jsonMapper(jsonMapper)
                .messageEndpoint("/mcp")
                .build()
        def tool = new SyncToolSpecification(
                Tool.builder("collection_countries", SCHEMA).title("Countries").build(),
                { context, request -> CallToolResult.builder().addTextContent("ok").isError(false).build() })
        server = McpServer.sync(transport)
                .serverInfo("test", "1.0.0")
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .jsonMapper(jsonMapper)
                .jsonSchemaValidator(new DefaultJsonSchemaValidator(MAPPER))
                .strictToolNameValidation(false)
                .tools([tool])
                .build()
    }

    def cleanup() {
        server?.close()
    }

    // the SDK default validator must work with the json-schema-validator version exported by
    // ogcapi-foundation; a mismatch surfaced as an HTTP 500 on every request
    def 'server can be built with the SDK default validator'() {
        expect:
        server.getServerInfo().name() == "test"
        server.listTools()*.name() == ["collection_countries"]
    }

    def 'structured content is validated against the output schema'() {
        given:
        def validator = new DefaultJsonSchemaValidator(MAPPER)

        expect:
        validator.validate(SCHEMA, [name: "France", area: 532308.3]).valid()
        !validator.validate(SCHEMA, [name: "France", area: "not a number"]).valid()
        !validator.validate(SCHEMA, [area: 532308.3]).valid()
    }

    def 'a schema that is not valid JSON Schema 2020-12 is detected'() {
        given:
        def validator = new DefaultJsonSchemaValidator(MAPPER)

        expect:
        validator.validateSchema(schema).valid() == valid

        where:
        schema                                                                | valid
        SCHEMA                                                                | true
        [type: "object", properties: [limit: [type: "integer", minimum: 1]]]  | true
        [type: "object", properties: [limit: [type: "integer", minimum: "1"]]] | false
        [type: "object", properties: [area: [type: "number", exclusiveMinimum: true]]] | false
    }

    def 'initialize negotiates the requested protocol version'() {
        when:
        def result = post([jsonrpc: "2.0", id: 1, method: "initialize", params: [
                protocolVersion: version,
                capabilities   : [:],
                clientInfo     : [name: "spec", version: "1.0.0"]]])

        then:
        result.status == 200
        result.body.result.protocolVersion == version

        where:
        version << ["2025-06-18", "2025-11-25"]
    }

    // clients of protocol version 2026-07-28 start with server/discover and fall back to
    // initialize only on a JSON-RPC error, not on an HTTP 500
    def 'an unknown method is answered with a JSON-RPC method-not-found error'() {
        when:
        def result = post([jsonrpc: "2.0", id: "discover-1", method: "server/discover", params: [:]])

        then:
        result.status != 500
        result.body.id == "discover-1"
        result.body.error.code == -32601
    }

    def 'tool arguments that violate the input schema yield a tool execution error'() {
        when:
        def result = post([jsonrpc: "2.0", id: 2, method: "tools/call", params: [
                name     : "collection_countries",
                arguments: [area: "large"]]])

        then:
        result.status == 200
        result.body.result.isError == true
        result.body.result.content[0].text.contains("input validation failed")
    }

    private Map post(Map message) {
        byte[] bytes = MAPPER.writeValueAsBytes(message)
        def input = new ByteArrayInputStream(bytes)
        def servletInput = new ServletInputStream() {
            @Override
            boolean isFinished() { input.available() == 0 }

            @Override
            boolean isReady() { true }

            @Override
            void setReadListener(ReadListener readListener) {}

            @Override
            int read() { input.read() }

            @Override
            int read(byte[] b, int off, int len) { input.read(b, off, len) }
        }
        def headers = [Accept: "application/json, text/event-stream", "Content-Type": "application/json"]

        HttpServletRequest request = Stub()
        request.getRequestURI() >> "/test/mcp"
        request.getMethod() >> "POST"
        request.getContentLengthLong() >> (long) bytes.length
        request.getCharacterEncoding() >> "UTF-8"
        request.getHeader("Accept") >> headers.Accept
        request.getHeaderNames() >> Collections.enumeration(headers.keySet())
        request.getHeaders(_ as String) >> { String name -> Collections.enumeration([headers[name]]) }
        request.getInputStream() >> servletInput

        def body = new StringWriter()
        def status = 0
        def response = [
                setStatus           : { int code -> status = code },
                sendError           : { int code -> status = code },
                setContentType      : { String type -> },
                setCharacterEncoding: { String encoding -> },
                getWriter           : { new PrintWriter(body) }
        ] as HttpServletResponse

        transport.doPost(request, response)

        return [status: status, body: body.toString().isEmpty() ? [:] : MAPPER.readValue(body.toString(), Map)]
    }
}
