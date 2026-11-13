/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app

import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter
import de.ii.ogcapi.mcp.domain.ImmutableMcpTool
import de.ii.ogcapi.mcp.domain.McpTool
import de.ii.xtraplatform.base.domain.resiliency.VolatileUnavailableException
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.ObjectSchema
import jakarta.ws.rs.InternalServerErrorException
import jakarta.ws.rs.NotFoundException
import spock.lang.Specification

class McpToolResultSpec extends Specification {

    static final String BASE = "https://example.com/bag/collections/pand/items"

    static final Map<String, Object> SELF = [rel: "self", type: "application/geo+json", title: "This document", href: BASE + "?gemeentecode=0307&limit=10&f=json"]
    static final Map<String, Object> HTML = [rel: "alternate", type: "text/html", title: "This document as HTML", href: BASE + "?gemeentecode=0307&limit=10&f=html"]
    static final Map<String, Object> FG = [rel: "alternate", type: "application/geo+json", title: "This document as JSON-FG", href: BASE + "?gemeentecode=0307&limit=10&f=json&profile=jsonfg"]
    static final Map<String, Object> NEXT = [rel: "next", type: "application/geo+json", href: BASE + "?gemeentecode=0307&f=json&limit=10&offset=20"]

    def 'a result with a next page states the offset of the next page and keeps the links to the result in the HTTP API'() {
        given:
        Map<String, Object> result = [
                type          : "FeatureCollection",
                numberReturned: 10,
                features      : [],
                links         : [SELF, HTML, FG, NEXT,
                                 [rel: "collection", type: "application/json", href: "https://example.com/bag/collections/pand"]]]

        when:
        Map<String, Object> forTool = McpServerImpl.forToolResult(result)

        then:
        forTool.keySet() as List == ["type", "nextOffset", "numberReturned", "features", "links"]
        forTool.nextOffset == 20L
        forTool.links == [SELF, HTML, NEXT]
    }

    def 'a result without a next page has no nextOffset'() {
        given:
        Map<String, Object> result = [
                type          : "FeatureCollection",
                numberReturned: 3,
                features      : [],
                links         : [SELF, HTML]]

        when:
        Map<String, Object> forTool = McpServerImpl.forToolResult(result)

        then:
        forTool.keySet() as List == ["type", "numberReturned", "features", "links"]
        forTool.links == [SELF, HTML]
    }

    def 'a result without links has neither links nor nextOffset'() {
        expect:
        McpServerImpl.forToolResult([type: "FeatureCollection", features: []]).keySet() as List == ["type", "features"]
    }

    def 'a result above the maximum size is not returned, the error states its size and links the complete result'() {
        given:
        McpTool tool = new ImmutableMcpTool.Builder()
                .id("collection_pand")
                .name("Collection - Pand")
                .description("Panden")
                .inputSchema(new ObjectSchema())
                .outputSchema(new ObjectSchema())
                .build()
        Map<String, Object> result = McpServerImpl.forToolResult([
                type          : "FeatureCollection",
                numberReturned: 2,
                features      : [[type: "Feature", id: "a"], [type: "Feature", id: "b"]],
                links         : [SELF, HTML]])

        when:
        Optional<String> error = McpServerImpl.tooLarge(tool, result, 1001, Optional.of(1000))

        then:
        error.isPresent()
        error.get().startsWith("Error executing tool 'collection_pand': the result has 1001 characters (2 features), more than the 1000 characters")
        error.get().contains("The complete result is available at " + SELF.href)
    }

    def 'a result above the maximum size that is one of several pages links this page and names the links to the others'() {
        given:
        McpTool tool = new ImmutableMcpTool.Builder()
                .id("collection_pand")
                .name("Collection - Pand")
                .description("Panden")
                .inputSchema(new ObjectSchema())
                .outputSchema(new ObjectSchema())
                .build()
        Map<String, Object> result = McpServerImpl.forToolResult([
                type          : "FeatureCollection",
                numberReturned: 10,
                features      : [],
                links         : [SELF, HTML, NEXT]])

        when:
        String error = McpServerImpl.tooLarge(tool, result, 1001, Optional.of(1000)).get()

        then:
        error.contains("This page of the result is available at " + SELF.href + ", the other pages through the links \"next\" and \"prev\" of each page")
        !error.contains("complete result")
    }

    def 'a result within the maximum size, or without a maximum, is returned'() {
        given:
        McpTool tool = new ImmutableMcpTool.Builder()
                .id("process_aggregate-features")
                .name("Aggregate features")
                .description("Aggregates")
                .inputSchema(new ObjectSchema())
                .outputSchema(new ObjectSchema())
                .build()
        Map<String, Object> result = [groups: [], total: [count: 0L]]

        expect:
        McpServerImpl.tooLarge(tool, result, 1000, Optional.of(1000)).isEmpty()
        McpServerImpl.tooLarge(tool, result, 5_000_000, Optional.empty()).isEmpty()
        McpServerImpl.tooLarge(tool, result, 1001, Optional.of(1000)).get() ==~ /Error executing tool 'process_aggregate-features': the result has 1001 characters, more than .*if there is one\./
    }

    def 'the result in the HTTP API is a resource link'() {
        given:
        McpTool tool = new ImmutableMcpTool.Builder()
                .id("collection_pand")
                .name("Collection - Pand")
                .description("Panden")
                .inputSchema(new ObjectSchema())
                .outputSchema(new ObjectSchema())
                .build()

        when:
        def link = McpServerImpl.resourceLink(tool, McpServerImpl.forToolResult([type: "FeatureCollection", features: [], links: [HTML, SELF, NEXT]]))

        then:
        link.isPresent()
        link.get().uri() == SELF.href
        link.get().mimeType() == "application/geo+json"
        link.get().name() == "collection_pand"
        link.get().title() == "Collection - Pand"

        and:
        McpServerImpl.resourceLink(tool, [groups: [], total: [count: 0L]]).isEmpty()
        McpServerImpl.resourceLink(tool, [type: "FeatureCollection", features: [], links: [HTML]]).isEmpty()
    }

    def 'the links of the features are left out, their other members are kept'() {
        given:
        Map<String, Object> result = [
                type    : "FeatureCollection",
                features: [
                        [type: "Feature", id: "a", geometry: null, properties: [x: 1],
                         links: [[rel: "predecessor-version", href: BASE + "/a?datetime=2020-01-01T00:00:00Z"]]],
                        [type: "Feature", id: "b", geometry: null, properties: [x: 2]]]]

        when:
        Map<String, Object> forTool = McpServerImpl.forToolResult(result)

        then:
        forTool.features == [
                [type: "Feature", id: "a", geometry: null, properties: [x: 1]],
                [type: "Feature", id: "b", geometry: null, properties: [x: 2]]]
        forTool.features[0].keySet() as List == ["type", "id", "geometry", "properties"]
        result.features[0].links.size() == 1
    }

    def 'an error in the arguments keeps its message'() {
        expect:
        McpServerImpl.toolError("query_x", error) == "Error executing tool 'query_x': " + message

        where:
        error                                                                       | message
        new IllegalArgumentException("Parameter 'punt' is not a valid geometry")    | "Parameter 'punt' is not a valid geometry"
        new NotFoundException("The feature 'a' does not exist.")                    | "The feature 'a' does not exist."
        new RuntimeException("wrapped", new IllegalArgumentException("bad filter")) | "bad filter"
    }

    def 'an error in the arguments is logged without a stacktrace'() {
        given:
        def logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(McpServerImpl)
        def appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>()
        def level = logger.level
        appender.start()
        logger.addAppender(appender)
        logger.level = ch.qos.logback.classic.Level.DEBUG

        when:
        McpServerImpl.toolError("process_x", new NotFoundException("The workId 'a' does not exist."))

        then:
        appender.list.size() == 1
        appender.list[0].formattedMessage.contains("The workId 'a' does not exist.")
        appender.list[0].throwableProxy == null

        cleanup:
        logger.detachAppender(appender)
        logger.level = level
    }

    def 'an error of the server does not disclose its message'() {
        when:
        String text = McpServerImpl.toolError("query_x", error)

        then:
        text.startsWith("Error executing tool 'query_x': an error of the server, not caused by the arguments")
        text =~ /error ID [0-9a-f]{16}/
        !text.contains("relation")

        where:
        error << [
                new RuntimeException("Unexpected SQL query error: relation \"wegdeel\" does not exist"),
                new InternalServerErrorException("relation \"wegdeel\" does not exist")]
    }

    def 'unavailable data is reported as temporary'() {
        expect:
        McpServerImpl.toolError("query_x", new VolatileUnavailableException("provider bgt not available")) ==
                "Error executing tool 'query_x': the data is currently not available. Please try again later."
    }

    def 'the paging hint is added to a copy of the schema of the offset parameter'() {
        given:
        OgcApiQueryParameter offset = Stub(OgcApiQueryParameter)
        offset.getName() >> "offset"
        IntegerSchema schema = new IntegerSchema().minimum(0)
        schema.setDescription("The index of the first feature.")

        when:
        def published = McpToolUtils.withPagingHint(offset, schema, schema.getDescription())

        then:
        published.getDescription() == "The index of the first feature. " + McpToolUtils.PAGING_HINT
        published.getMinimum() == 0
        schema.getDescription() == "The index of the first feature."
    }

    def 'the schema of another parameter is not changed'() {
        given:
        OgcApiQueryParameter limit = Stub(OgcApiQueryParameter)
        limit.getName() >> "limit"
        IntegerSchema schema = new IntegerSchema()

        expect:
        McpToolUtils.withPagingHint(limit, schema, "The maximum number of features.").is(schema)
    }
}
