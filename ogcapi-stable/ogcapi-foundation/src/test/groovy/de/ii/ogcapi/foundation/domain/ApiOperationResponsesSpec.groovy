/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain

import com.google.common.collect.ImmutableList
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Paths
import jakarta.ws.rs.core.MediaType
import spock.lang.Specification

class ApiOperationResponsesSpec extends Specification {

    static final String PATH = '/collections/buildings/items/{featureId}'

    def 'a PUT that cannot create the resource describes only the success response'() {
        given:
        ApiOperation operation = putOperation(false)

        when:
        Set<String> statusCodes = render(operation, 'PUT')

        then: 'no 201, because the operation never creates a resource'
        !statusCodes.contains('201')
        statusCodes.contains('204')
    }

    def 'a PUT that can create the resource describes 201 as well'() {
        given: 'a PUT operation of a collection with client-assigned feature ids'
        ApiOperation operation = putOperation(true)

        when:
        Set<String> statusCodes = render(operation, 'PUT')

        then: 'both outcomes of the request are described'
        statusCodes.contains('201')
        statusCodes.contains('204')
    }

    def 'the 201 of a PUT describes the Location header, the 204 does not'() {
        given: 'a response header that belongs to the 201 only'
        ApiHeader location = Stub()
        location.getId() >> 'Location'
        location.getName() >> 'Location'
        location.isResponseHeader() >> true
        location.getResponseStatusCodes() >> Set.of('201')

        when:
        ApiOperation operation = putOperation(true, ImmutableList.of(location))

        then: 'the created-resource response carries it'
        operation.getAdditionalResponses().find { it.getStatusCode() == '201' }
                .getHeaders().collect { it.getName() } == ['Location']

        and: 'the response of a replaced resource does not'
        operation.getSuccess().get().getStatusCode() == '204'
        operation.getSuccess().get().getHeaders().isEmpty()
    }

    def 'a response header without status codes is described for every response'() {
        given:
        ApiHeader anyResponse = Stub()
        anyResponse.getId() >> 'Content-Crs'
        anyResponse.getName() >> 'Content-Crs'
        anyResponse.isResponseHeader() >> true
        anyResponse.getResponseStatusCodes() >> Set.of()

        when:
        ApiOperation operation = putOperation(true, ImmutableList.of(anyResponse))

        then:
        operation.getSuccess().get().getHeaders().collect { it.getName() } == ['Content-Crs']
        operation.getAdditionalResponses().find { it.getStatusCode() == '201' }
                .getHeaders().collect { it.getName() } == ['Content-Crs']
    }

    def 'an operation describes the error status codes it declares'() {
        given: 'an operation that evaluates preconditions'
        ApiOperation operation = new ImmutableApiOperation.Builder()
                .from(putOperation(false))
                .addErrorStatusCodes(412, 428)
                .build()

        when:
        Set<String> statusCodes = render(operation, 'PUT')

        then:
        statusCodes.contains('412')
        statusCodes.contains('428')
    }

    def 'an operation that declares no precondition status codes describes neither'() {
        given:
        ApiOperation operation = putOperation(false)

        when:
        Set<String> statusCodes = render(operation, 'PUT')

        then:
        !statusCodes.contains('412')
        !statusCodes.contains('428')
    }

    def 'the responses are described in ascending order of the status code'() {
        given:
        ApiOperation operation = new ImmutableApiOperation.Builder()
                .from(putOperation(true))
                .addErrorStatusCodes(412, 428)
                .build()

        when:
        List<String> statusCodes = new ArrayList<>(render(operation, 'PUT'))

        then:
        statusCodes == statusCodes.toSorted()
    }

    private static ApiOperation putOperation(boolean putAllowsCreate) {
        putOperation(putAllowsCreate, ImmutableList.of())
    }

    private static ApiOperation putOperation(boolean putAllowsCreate, List<ApiHeader> headers) {
        ApiOperation.of(
                PATH,
                HttpMethods.PUT,
                Map.of(MediaType.APPLICATION_JSON_TYPE, content()),
                ImmutableList.of(),
                headers,
                'replace a feature',
                Optional.empty(),
                Optional.empty(),
                'replaceItem',
                PermissionGroup.of(PermissionGroup.Base.WRITE, 'data', 'mutate data'),
                ImmutableList.of('Mutate data'),
                Optional.empty(),
                Optional.empty(),
                putAllowsCreate).get()
    }

    private static ApiMediaTypeContent content() {
        new ImmutableApiMediaTypeContent.Builder()
                .schema(new io.swagger.v3.oas.models.media.ObjectSchema())
                .schemaRef('#/components/schemas/Feature')
                .ogcApiMediaType(new ImmutableApiMediaType.Builder()
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build())
                .build()
    }

    // renders the operation into an OpenAPI document and returns the status codes of its responses
    private static Set<String> render(ApiOperation operation, String method) {
        Components components = new Components()
                .schemas(new LinkedHashMap<>())
                .responses(new LinkedHashMap<>())
                .parameters(new LinkedHashMap<>())
                .headers(new LinkedHashMap<>())
        OpenAPI openAPI = new OpenAPI().paths(new Paths()).components(components)
        OgcApiDataV2 apiData = new ImmutableOgcApiDataV2.Builder().id('buildings').build()
        OgcApiResource resource = new ImmutableOgcApiResourceAuxiliary.Builder()
                .path(PATH)
                .pathParameters(ImmutableList.of())
                .build()

        operation.updateOpenApiDefinition(
                apiData, Optional.empty(), openAPI, resource, method, new HashSet<Integer>())

        openAPI.getPaths().get(PATH).readOperationsMap().values().first().getResponses().keySet()
    }
}
