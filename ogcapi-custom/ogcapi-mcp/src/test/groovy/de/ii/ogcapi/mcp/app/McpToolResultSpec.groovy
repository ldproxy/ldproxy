/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app

import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter
import io.swagger.v3.oas.models.media.IntegerSchema
import spock.lang.Specification

class McpToolResultSpec extends Specification {

    static final String BASE = "https://example.com/bag/collections/pand/items"

    def 'a result with a next page states the offset of the next page and keeps only the paging links'() {
        given:
        Map<String, Object> result = [
                type          : "FeatureCollection",
                numberReturned: 10,
                features      : [],
                links         : [
                        [rel: "self", href: BASE + "?gemeentecode=0307&limit=10&f=json"],
                        [rel: "alternate", href: BASE + "?gemeentecode=0307&limit=10&f=html"],
                        [rel: "next", href: BASE + "?gemeentecode=0307&f=json&limit=10&offset=20"]]]

        when:
        Map<String, Object> forTool = McpServerImpl.forToolResult(result)

        then:
        forTool.keySet() as List == ["type", "nextOffset", "numberReturned", "features", "links"]
        forTool.nextOffset == 20L
        forTool.links == [[rel: "next", href: BASE + "?gemeentecode=0307&f=json&limit=10&offset=20"]]
    }

    def 'a result without a next page has neither links nor nextOffset'() {
        given:
        Map<String, Object> result = [
                type          : "FeatureCollection",
                numberReturned: 3,
                features      : [],
                links         : [[rel: "self", href: BASE + "?gemeentecode=0307&f=json"]]]

        when:
        Map<String, Object> forTool = McpServerImpl.forToolResult(result)

        then:
        forTool.keySet() as List == ["type", "numberReturned", "features"]
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
