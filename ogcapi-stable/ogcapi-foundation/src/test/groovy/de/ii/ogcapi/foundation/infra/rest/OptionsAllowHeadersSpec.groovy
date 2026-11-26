/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.infra.rest

import de.ii.ogcapi.foundation.domain.ApiHeader
import de.ii.ogcapi.foundation.domain.ApiOperation
import de.ii.ogcapi.foundation.domain.HttpMethods
import de.ii.ogcapi.foundation.domain.ImmutableApiOperation
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.foundation.domain.PermissionGroup
import de.ii.ogcapi.foundation.domain.SchemaValidator
import spock.lang.Specification

class OptionsAllowHeadersSpec extends Specification {

    def 'without declared request headers, the general headers are allowed'() {
        expect:
        OptionsEndpoint.allowedHeaders([Optional.empty(), operation([])]) == OptionsEndpoint.GENERAL_REQUEST_HEADERS
    }

    def 'the request headers declared by an operation are allowed in addition, sorted'() {
        given: 'an operation that declares the headers of a protocol it implements'
        def operation = operation([header('Mcp-Name', true), header('MCP-Protocol-Version', true), header('Mcp-Method', true)])

        expect:
        OptionsEndpoint.allowedHeaders([operation]) ==
                OptionsEndpoint.GENERAL_REQUEST_HEADERS + ['MCP-Protocol-Version', 'Mcp-Method', 'Mcp-Name']
    }

    def 'response headers are not request headers'() {
        given:
        def operation = operation([header('Location', false), header('Mcp-Method', true)])

        expect:
        OptionsEndpoint.allowedHeaders([operation]) == OptionsEndpoint.GENERAL_REQUEST_HEADERS + ['Mcp-Method']
    }

    def 'a header is listed once, regardless of its case and of the number of operations'() {
        given: 'two methods of a resource declare the same header, one also a general header'
        def post = operation([header('Mcp-Method', true), header('content-crs', true)])
        def put = operation([header('MCP-METHOD', true)])

        expect: 'the first in the sort order is kept'
        OptionsEndpoint.allowedHeaders([post, put]) == OptionsEndpoint.GENERAL_REQUEST_HEADERS + ['MCP-METHOD']
    }

    private static Optional<ApiOperation> operation(List<ApiHeader> headers) {
        Optional.of(new ImmutableApiOperation.Builder()
                .summary('an operation')
                .operationId('operation')
                .permissionGroup(PermissionGroup.of(PermissionGroup.Base.READ, 'data', 'read data'))
                .headers(headers)
                .build())
    }

    private static ApiHeader header(String name, boolean request) {
        new ApiHeader() {
            @Override
            String getName() { name }

            @Override
            String getDescription() { name }

            @Override
            boolean isRequestHeader() { request }

            @Override
            boolean isResponseHeader() { !request }

            @Override
            boolean isApplicable(OgcApiDataV2 apiData, String definitionPath, HttpMethods method) { true }

            @Override
            SchemaValidator getSchemaValidator() { null }
        }
    }
}
