/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app

import de.ii.ogcapi.foundation.domain.ApiMediaType
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent
import de.ii.ogcapi.foundation.domain.ApiOperation
import de.ii.ogcapi.foundation.domain.ApiResponse
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter
import de.ii.ogcapi.mcp.domain.ImmutableMcpTool
import de.ii.ogcapi.mcp.domain.McpTool
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.ObjectSchema
import jakarta.ws.rs.core.MediaType
import spock.lang.Specification

class McpInternalRequestsSpec extends Specification {

    static ApiMediaType mediaType(String type, String subtype, String parameter) {
        new ImmutableApiMediaType.Builder()
                .type(new MediaType(type, subtype))
                .label(parameter)
                .parameter(parameter)
                .build()
    }

    static final ApiMediaType GEOJSON_OF_FORMAT = mediaType("application", "geo+json", "json")
    static final ApiMediaType HTML = mediaType("text", "html", "html")
    static final ApiMediaType JSON_FG = mediaType("application", "vnd.ogc.fg+json", "jsonfg")

    static McpTool tool(Optional<ApiOperation> operation) {
        new ImmutableMcpTool.Builder()
                .id("collection_pand")
                .name("Collection - Pand")
                .description("Panden")
                .inputSchema(new ObjectSchema())
                .outputSchema(new ObjectSchema())
                .operation(operation)
                .build()
    }

    ApiMediaTypeContent content(ApiMediaType mediaType) {
        ApiMediaTypeContent content = Stub(ApiMediaTypeContent)
        content.getOgcApiMediaType() >> mediaType
        return content
    }

    def 'the alternate media types of a tool request are the other encodings of its operation'() {
        given:
        ApiResponse response = Stub(ApiResponse)
        response.getContent() >> [
                (GEOJSON_OF_FORMAT.type()): content(GEOJSON_OF_FORMAT),
                (HTML.type())             : content(HTML),
                (JSON_FG.type())          : content(JSON_FG)]
        ApiOperation operation = Stub(ApiOperation)
        operation.getSuccess() >> Optional.of(response)

        when:
        Set<ApiMediaType> alternates = McpInternalRequests.alternateMediaTypes(tool(Optional.of(operation)), McpInternalRequests.GEOJSON)

        then:
        alternates == [HTML, JSON_FG] as Set
    }

    def 'a tool without an operation has no alternate media types'() {
        expect:
        McpInternalRequests.alternateMediaTypes(tool(Optional.empty()), McpInternalRequests.GEOJSON).isEmpty()
    }

    McpTool toolWithArguments() {
        OgcApiQueryParameter limit = Stub(OgcApiQueryParameter)
        limit.getName() >> "limit"
        OgcApiQueryParameter hidden = Stub(OgcApiQueryParameter)
        hidden.getName() >> "buurtnaam"
        def inputSchema = new ObjectSchema()
        inputSchema.addProperty("limit", new IntegerSchema())
        new ImmutableMcpTool.Builder()
                .id("collection_pand")
                .name("Collection - Pand")
                .description("Panden")
                .inputSchema(inputSchema)
                .outputSchema(new ObjectSchema())
                .queryParameters([limit, hidden])
                .build()
    }

    def 'the arguments of a tool are accepted, also those that are not in the tool definition'() {
        when:
        McpInternalRequests.checkArguments(toolWithArguments(), arguments, Optional.of("Use filter."))

        then:
        noExceptionThrown()

        where:
        arguments << [[:], [limit: 10], [buurtnaam: "Hof"], [limit: 10, gemeentecod: null]]
    }

    def 'an unknown argument is rejected with the arguments of the tool'() {
        when:
        McpInternalRequests.checkArguments(toolWithArguments(), [gemeentecod: "0307", limit: 10, a: 1], Optional.of("Use filter."))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Unknown arguments: 'a', 'gemeentecod'. The arguments of the tool are: limit. Use filter."
    }
}
