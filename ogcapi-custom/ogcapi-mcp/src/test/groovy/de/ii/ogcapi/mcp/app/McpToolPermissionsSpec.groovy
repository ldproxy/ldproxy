/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app

import de.ii.ogcapi.foundation.domain.ApiOperation
import de.ii.ogcapi.foundation.domain.ApiRequestAuthorizer
import de.ii.ogcapi.foundation.domain.ApiRequestContext
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApi
import de.ii.ogcapi.foundation.domain.OgcApiResource
import de.ii.ogcapi.foundation.domain.QueryParameterSet
import de.ii.ogcapi.mcp.domain.ImmutableMcpTool
import de.ii.ogcapi.mcp.domain.McpTool
import de.ii.xtraplatform.auth.domain.ImmutableUser
import de.ii.xtraplatform.auth.domain.User
import de.ii.xtraplatform.base.domain.AppContext
import de.ii.xtraplatform.web.domain.URICustomizer
import io.swagger.v3.oas.models.media.ObjectSchema
import jakarta.ws.rs.core.MediaType
import spock.lang.Specification

class McpToolPermissionsSpec extends Specification {

    ApiOperation operation = Stub(ApiOperation)
    User user = new ImmutableUser.Builder().name('alice').addPermissions('read').build()

    static McpTool tool(Optional<ApiOperation> operation) {
        new ImmutableMcpTool.Builder()
                .id('collection_buildings')
                .name('Collection Query - Buildings')
                .description('The buildings.')
                .inputSchema(new ObjectSchema())
                .outputSchema(new ObjectSchema())
                .operation(operation)
                .build()
    }

    ApiRequestContext internalRequest() {
        def appContext = Stub(AppContext)
        appContext.getPathPrefix() >> []

        def api = Stub(OgcApi)
        api.getData() >> new ImmutableOgcApiDataV2.Builder()
                .id('buildings')
                .serviceType('OGC_API')
                .label('Buildings')
                .build()
        api.getId() >> 'buildings'
        api.getUri() >> URI.create('https://example.com/buildings')

        def mcpRequest = Stub(ApiRequestContext)
        mcpRequest.getUser() >> Optional.of(user)
        mcpRequest.getBaseUriCustomizer() >> new URICustomizer('https://example.com')
        mcpRequest.getBasePathSegments() >> []

        McpInternalRequests.internalRequest(
                appContext, api, Optional.of(mcpRequest), [limit: '10'], 'collections', 'building', 'items')
                .method('GET')
                .mediaType(new ImmutableApiMediaType.Builder().type(MediaType.APPLICATION_JSON_TYPE).build())
                .queryParameterSet(QueryParameterSet.of())
                .build()
    }

    def 'a tool call is authorized like a request to the operation of the tool'() {
        given:
        def authorizer = Mock(ApiRequestAuthorizer)
        def request = internalRequest()
        def authorized = Stub(ApiRequestContext)
        def tool = tool(Optional.of(operation))

        when:
        def result = McpInternalRequests.authorize(authorizer, tool, request, Optional.empty())

        then:
        1 * authorizer.checkAuthorization(request, tool.operation.get(), Optional.of(user), Optional.empty()) >> authorized
        result.is(authorized)
    }

    def 'the internal request is the request to the operation'() {
        when:
        def request = internalRequest()

        then:
        request.method == 'GET'
        request.collectionId == Optional.of('building')
        request.user == Optional.of(user)
        request.uriCustomizer.toString() == 'https://example.com/buildings/collections/building/items?limit=10'
    }

    def 'an internal request without a method is an internal request'() {
        when:
        def request = new de.ii.ogcapi.foundation.domain.ImmutableStaticRequestContext.Builder()
                .from(internalRequest())
                .method('INTERNAL')
                .build()
        def unset = new de.ii.ogcapi.foundation.domain.ImmutableStaticRequestContext.Builder()
                .webContext(Stub(AppContext))
                .api(Stub(OgcApi))
                .requestUri(URI.create('https://example.com/buildings'))
                .basePathSegments([])
                .mediaType(new ImmutableApiMediaType.Builder().type(MediaType.APPLICATION_JSON_TYPE).build())
                .queryParameterSet(QueryParameterSet.of())
                .build()

        then:
        request.method == 'INTERNAL'
        unset.method == 'INTERNAL'
    }

    def 'a tool without an operation is never executed unchecked'() {
        given:
        def authorizer = Mock(ApiRequestAuthorizer)

        when:
        McpInternalRequests.authorize(authorizer, tool(Optional.empty()), internalRequest(), Optional.empty())

        then:
        0 * authorizer._
        thrown(IllegalStateException)
    }

    def 'the operation of a tool is the GET operation of the resource'() {
        given:
        def get = Stub(ApiOperation)
        get.getOperationId() >> 'executeStoredQuery_q'
        def post = Stub(ApiOperation)
        post.getOperationId() >> 'executeStoredQuery_q_post'
        def other = Stub(ApiOperation)
        other.getOperationId() >> 'getQueries'
        def resource = Stub(OgcApiResource)
        resource.getOperations() >> [POST: post, DELETE: other, GET: get]

        expect:
        McpToolUtils.operation(resource, 'executeStoredQuery') == Optional.of(get)
        McpToolUtils.operation(resource, 'getItems') == Optional.empty()
    }
}
