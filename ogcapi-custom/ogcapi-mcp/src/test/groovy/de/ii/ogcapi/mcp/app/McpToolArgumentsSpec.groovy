/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app

import de.ii.ogcapi.collections.queryables.domain.ImmutableQueryParameterTemplateQueryable
import de.ii.ogcapi.collections.queryables.domain.QueryParameterTemplateQueryable
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter
import de.ii.ogcapi.foundation.domain.SchemaValidator
import de.ii.xtraplatform.features.domain.SchemaBase
import io.swagger.v3.oas.models.media.StringSchema
import spock.lang.Specification

class McpToolArgumentsSpec extends Specification {

    static final SchemaValidator ACCEPT_ALL = { schema, value -> Optional.empty() } as SchemaValidator

    OgcApiQueryParameter parameter(String name, String description) {
        OgcApiQueryParameter param = Stub(OgcApiQueryParameter)
        param.getName() >> name
        param.getDescription() >> description
        return param
    }

    def 'a standard argument has the short description and the schema of the parameter is not changed'() {
        given:
        def schema = new StringSchema().pattern("^[0-9]+\$")
        schema.setDescription("the description in the API definition")

        when:
        def argument = McpToolUtils.argumentSchema(parameter("datetime", "a long description"), schema, [])

        then:
        argument.description.startsWith("Only objects with a temporal property")
        argument.pattern == null
        schema.pattern == "^[0-9]+\$"
        schema.description == "the description in the API definition"
    }

    def 'the filter argument names the queryables that are only available in the filter'() {
        when:
        def argument = McpToolUtils.argumentSchema(
                parameter("filter", "a long description"), new StringSchema(), filterOnly)

        then:
        argument.description.startsWith("A filter expression in CQL2 text")
        argument.description.contains("Queryables that are only available here") == !filterOnly.isEmpty()
        filterOnly.every { argument.description.contains(it) }

        where:
        filterOnly << [[], ["eindRegistratie", "buurtnaam"]]
    }

    def 'a queryable argument has the description without the note on wildcards'() {
        given:
        QueryParameterTemplateQueryable queryable = new ImmutableQueryParameterTemplateQueryable.Builder()
                .apiId("api")
                .collectionId("collection")
                .name("naam")
                .baseDescription("Filter the collection by property 'naam'.")
                .schema(new StringSchema())
                .schemaValidator(ACCEPT_ALL)
                .type(SchemaBase.Type.STRING)
                .build()

        when:
        def argument = McpToolUtils.argumentSchema(queryable, queryable.getSchema(null), [])

        then:
        queryable.description.contains(QueryParameterTemplateQueryable.WILDCARD_NOTE)
        argument.description == "Filter the collection by property 'naam'."
    }

    def 'another argument keeps the description of the parameter'() {
        when:
        def argument = McpToolUtils.argumentSchema(parameter("zoom-level", "the zoom level"), new StringSchema(), [])

        then:
        argument.description == "the zoom level"
    }
}
