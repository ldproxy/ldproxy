/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app

import com.fasterxml.jackson.databind.ObjectMapper
import de.ii.ogcapi.mcp.app.McpServerImpl.AvailableTool
import de.ii.ogcapi.mcp.app.McpServerImpl.ToolCallResult
import de.ii.ogcapi.mcp.app.McpServerImpl.ToolCaller
import de.ii.ogcapi.mcp.domain.ImmutableMcpTool
import io.modelcontextprotocol.common.McpTransportContext
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper
import io.modelcontextprotocol.json.schema.jackson2.DefaultJsonSchemaValidator
import io.modelcontextprotocol.server.McpServer
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import io.modelcontextprotocol.spec.McpSchema.Implementation
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities
import io.modelcontextprotocol.spec.McpSchema.Tool
import io.swagger.v3.oas.models.media.ObjectSchema
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import spock.lang.Specification

class McpEraRouterSpec extends Specification {

    static final ObjectMapper MAPPER = new ObjectMapper()
    static final String MODERN = "2026-07-28"
    static final Map<String, Object> INPUT_SCHEMA = [type: "object", properties: [limit: [type: "integer"]]]
    static final Map<String, Object> OUTPUT_SCHEMA = [type: "object", properties: [count: [type: "integer"]]]
    static final Map<String, Object> META = ["io.modelcontextprotocol/protocolVersion": MODERN,
                                             "io.modelcontextprotocol/clientCapabilities": [:]]

    static final URI API_URI = URI.create("https://example.org/cshapes")

    McpEraRouter router

    def setup() {
        router = createRouter([])
    }

    private static McpEraRouter createRouter(List<String> allowedOrigins) {
        def originValidator = McpServerImpl.originValidator(allowedOrigins, API_URI)
        def jsonMapper = new JacksonMcpJsonMapper(MAPPER)
        def validator = new DefaultJsonSchemaValidator(MAPPER)
        def tool = new AvailableTool(
                new ImmutableMcpTool.Builder()
                        .id("collection_countries")
                        .name("Countries")
                        .description("The countries")
                        .inputSchema(new ObjectSchema())
                        .outputSchema(new ObjectSchema())
                        .build(),
                null, INPUT_SCHEMA, OUTPUT_SCHEMA)
        ToolCaller caller = { AvailableTool available, Map<String, Object> arguments, McpTransportContext context ->
            new ToolCallResult('{"count":3}', false, [count: 3], Optional.empty())
        } as ToolCaller

        def legacy = HttpServletStatelessServerTransport.builder()
                .jsonMapper(jsonMapper)
                .messageEndpoint("/mcp")
                .httpHeaderValidator(originValidator)
                .build()
        McpServer.sync(legacy)
                .serverInfo("legacy", "1.0.0")
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .jsonMapper(jsonMapper)
                .jsonSchemaValidator(validator)
                .tools([new SyncToolSpecification(
                        Tool.builder("collection_countries", INPUT_SCHEMA).build(),
                        { context, request -> CallToolResult.builder().addTextContent("legacy").isError(false).build() })])
                .build()
        def modern = McpModernServer.create(
                new Implementation("cshapes", "CShapes", "1.0.0", null, null, "http://localhost/cshapes"),
                "The instructions",
                [tool],
                caller,
                jsonMapper,
                validator,
                { request -> McpTransportContext.EMPTY },
                originValidator)

        return new McpEraRouter(legacy, modern)
    }

    def cleanup() {
        router?.close()
    }

    def 'the protocol version selects the era'() {
        expect:
        McpEraRouter.isModern(version) == modern

        where:
        version      | modern
        null         | false
        "2024-11-05" | false
        "2025-06-18" | false
        "2025-11-25" | false
        "2026-07-28" | true
        "2027-01-01" | true
    }

    def 'a legacy client is served by the legacy server'() {
        when:
        def result = post([jsonrpc: "2.0", id: 1, method: "initialize", params: [
                protocolVersion: "2025-11-25",
                capabilities   : [:],
                clientInfo     : [name: "spec", version: "1.0.0"]]], [:])

        then:
        result.status == 200
        result.body.result.protocolVersion == "2025-11-25"
        result.body.result.serverInfo.name == "legacy"
    }

    def 'a modern client can discover the server'() {
        when:
        def result = post([jsonrpc: "2.0", id: 1, method: "server/discover", params: [_meta: META]],
                modernHeaders("server/discover", null))

        then:
        result.status == 200
        result.body.result.supportedVersions == [MODERN]
        // the server information is metadata of the result in this revision
        result.body.result._meta["io.modelcontextprotocol/serverInfo"].name == "cshapes"
        result.body.result.instructions == "The instructions"
        result.body.result.capabilities.tools != null
    }

    def 'a modern client lists the tools'() {
        when:
        def result = post([jsonrpc: "2.0", id: 2, method: "tools/list", params: [_meta: META]],
                modernHeaders("tools/list", null))

        then:
        result.status == 200
        result.body.result.tools*.name == ["collection_countries"]
        result.body.result.tools[0].annotations.readOnlyHint == true
    }

    def 'a modern client calls a tool'() {
        when:
        def result = post([jsonrpc: "2.0", id: 3, method: "tools/call",
                           params : [name: "collection_countries", arguments: [limit: 1], _meta: META]],
                modernHeaders("tools/call", "collection_countries"))

        then:
        result.status == 200
        result.body.result.isError == false
        result.body.result.structuredContent == [count: 3]
        result.body.result.content[0].text == '{"count":3}'
    }

    def 'a modern request with an unsupported version lists the supported versions'() {
        given:
        def meta = new LinkedHashMap(META)
        meta["io.modelcontextprotocol/protocolVersion"] = "2027-01-01"

        when:
        def result = post([jsonrpc: "2.0", id: 4, method: "tools/list", params: [_meta: meta]],
                ["MCP-Protocol-Version": "2027-01-01", "Mcp-Method": "tools/list"])

        then:
        result.body.error.code == -32022
        result.body.error.data.supported == [MODERN]
    }

    def 'the origin of the API is the one a browser sends'() {
        expect:
        McpServerImpl.origin(URI.create(uri)) == origin

        where:
        uri                                  | origin
        "https://example.org/cshapes"        | "https://example.org"
        "HTTPS://Example.ORG:443/cshapes"    | "https://example.org"
        "http://localhost:7080/cshapes"      | "http://localhost:7080"
        "http://localhost:80/cshapes"        | "http://localhost"
        "https://example.org:8443/a/b"       | "https://example.org:8443"
    }

    def 'a request from a web page of another origin is rejected in both eras'() {
        when:
        def legacy = post(initialize(), [Origin: "https://evil.example"])
        def modern = post([jsonrpc: "2.0", id: 2, method: "tools/list", params: [_meta: META]],
                modernHeaders("tools/list", null) + [Origin: "https://evil.example"])

        then:
        legacy.status == 403
        modern.status == 403
    }

    def 'a request from a web page of the API itself is accepted in both eras'() {
        when:
        def legacy = post(initialize(), [Origin: "https://example.org"])
        def modern = post([jsonrpc: "2.0", id: 2, method: "tools/list", params: [_meta: META]],
                modernHeaders("tools/list", null) + [Origin: "https://example.org"])

        then:
        legacy.status == 200
        legacy.body.result.protocolVersion == "2025-11-25"
        modern.status == 200
        modern.body.result.tools*.name == ["collection_countries"]
    }

    def 'a configured origin is accepted, with any port if the port is a wildcard'() {
        given:
        router.close()
        router = createRouter(["https://app.example", "http://localhost:*"])

        expect:
        post(initialize(), [Origin: origin]).status == status

        where:
        origin                   | status
        "https://app.example"    | 200
        "http://localhost:3000"  | 200
        "http://localhost"       | 200
        "https://other.example"  | 403
    }

    def 'with the wildcard, every origin is accepted'() {
        given:
        router.close()
        router = createRouter(["*"])

        expect:
        post(initialize(), [Origin: "https://evil.example"]).status == 200
    }

    private static Map initialize() {
        [jsonrpc: "2.0", id: 1, method: "initialize", params: [
                protocolVersion: "2025-11-25",
                capabilities   : [:],
                clientInfo     : [name: "spec", version: "1.0.0"]]]
    }

    private static Map<String, String> modernHeaders(String method, String name) {
        def headers = ["MCP-Protocol-Version": MODERN, "Mcp-Method": method]
        if (name != null) {
            headers["Mcp-Name"] = name
        }
        return headers
    }

    private Map post(Map message, Map<String, String> extraHeaders) {
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
        Map<String, String> headers = [Accept: "application/json, text/event-stream", "Content-Type": "application/json"]
        headers.putAll(extraHeaders)
        def header = { String name -> headers.find { it.key.equalsIgnoreCase(name) }?.value }

        HttpServletRequest request = Stub()
        request.getRequestURI() >> "/test/mcp"
        request.getMethod() >> "POST"
        request.getContentLength() >> bytes.length
        request.getContentLengthLong() >> (long) bytes.length
        request.getCharacterEncoding() >> "UTF-8"
        request.getContentType() >> headers."Content-Type"
        request.getHeader(_ as String) >> { String name -> header(name) }
        request.getHeaderNames() >> { Collections.enumeration(headers.keySet()) }
        request.getHeaders(_ as String) >> { String name ->
            def value = header(name)
            Collections.enumeration(value == null ? [] : [value])
        }
        request.getInputStream() >> servletInput
        request.getReader() >> new BufferedReader(new InputStreamReader(input, "UTF-8"))

        def body = new StringWriter()
        def writer = new PrintWriter(body)
        def status = 200
        HttpServletResponse response = Stub()
        response.setStatus(_ as Integer) >> { int code -> status = code }
        response.sendError(_ as Integer) >> { int code -> status = code }
        response.sendError(_ as Integer, _ as String) >> { int code, String msg -> status = code }
        response.getWriter() >> writer

        router.service(request, response)
        writer.flush()

        return [status: status, body: body.toString().isEmpty() ? [:] : MAPPER.readValue(body.toString(), Map)]
    }
}
