/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain

import com.google.common.collect.ImmutableList
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import jakarta.ws.rs.core.MediaType
import spock.lang.Specification

class ApiOperationLinksSpec extends Specification {

    static final ApiOperationLink LINK = new ImmutableApiOperationLink.Builder()
            .name('item')
            .operationId('buildings.getItems')
            .putParameters('featureId', '$response.body#/features/0/id')
            .description('the first feature')
            .build()

    def 'a link is added to the response of the source operation'() {
        given:
        Map<String, Operation> operations = [
                'buildings.getItems': operation(),
                'buildings.getItem' : operation()]

        when:
        target([LINK]).updateOpenApiLinks(operations)

        then:
        def link = operations['buildings.getItems'].getResponses().get('200').getLinks().get('item')
        link.getOperationId() == 'buildings.getItem'
        link.getParameters() == ['featureId': '$response.body#/features/0/id']
        link.getDescription() == 'the first feature'
    }

    def 'no link is added, if the source operation is not in the definition'() {
        given:
        Map<String, Operation> operations = ['buildings.getItem': operation()]

        when:
        target([LINK]).updateOpenApiLinks(operations)

        then:
        noExceptionThrown()
    }

    def 'no link is added, if the operation itself is not in the definition'() {
        given:
        Map<String, Operation> operations = ['buildings.getItems': operation()]

        when:
        target([LINK]).updateOpenApiLinks(operations)

        then:
        operations['buildings.getItems'].getResponses().get('200').getLinks() == null
    }

    def 'no link is added, if the source operation has no response with the status code'() {
        given:
        ApiOperationLink created = new ImmutableApiOperationLink.Builder().from(LINK).statusCode('201').build()
        Map<String, Operation> operations = [
                'buildings.getItems': operation(),
                'buildings.getItem' : operation()]

        when:
        target([created]).updateOpenApiLinks(operations)

        then:
        operations['buildings.getItems'].getResponses().get('200').getLinks() == null
        operations['buildings.getItems'].getResponses().get('201') == null
    }

    private static Operation operation() {
        new Operation().responses(new ApiResponses().addApiResponse('200', new ApiResponse().description('OK')))
    }

    private static ApiOperation target(List<ApiOperationLink> links) {
        OgcApiDataV2 apiData = new ImmutableOgcApiDataV2.Builder().id('buildings').build()
        ApiOperation operation = ApiOperation.getResource(
                apiData,
                '/collections/buildings/items/{featureId}',
                false,
                ImmutableList.of(),
                ImmutableList.of(),
                Map.of(MediaType.APPLICATION_JSON_TYPE, new ImmutableApiMediaTypeContent.Builder()
                        .schema(new io.swagger.v3.oas.models.media.ObjectSchema())
                        .schemaRef('#/components/schemas/Feature')
                        .ogcApiMediaType(new ImmutableApiMediaType.Builder()
                                .type(MediaType.APPLICATION_JSON_TYPE)
                                .build())
                        .build()),
                'fetch a feature',
                Optional.empty(),
                Optional.empty(),
                'buildings.getItem',
                PermissionGroup.of(PermissionGroup.Base.READ, 'data', 'read data'),
                ImmutableList.of('Access data'),
                Optional.empty(),
                Optional.empty()).get()
        new ImmutableApiOperation.Builder().from(operation).linkedFrom(links).build()
    }
}
