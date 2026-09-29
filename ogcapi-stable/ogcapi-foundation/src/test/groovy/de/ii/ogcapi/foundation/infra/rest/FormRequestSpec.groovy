/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.infra.rest

import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent
import de.ii.ogcapi.foundation.domain.ApiOperation
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent
import de.ii.ogcapi.foundation.domain.PermissionGroup
import io.swagger.v3.oas.models.media.ObjectSchema
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.MultivaluedHashMap
import jakarta.ws.rs.core.MultivaluedMap
import jakarta.ws.rs.core.UriInfo
import java.nio.charset.StandardCharsets
import spock.lang.Specification

class FormRequestSpec extends Specification {

    static final PermissionGroup READ = PermissionGroup.of(PermissionGroup.Base.READ, 'data', 'read data')

    def 'a URL-encoded POST is a form request, also with a charset'() {
        expect:
        ApiRequestDispatcher.isFormRequest(method, mediaType, body) == expected

        where:
        method | mediaType                                                        | body                        || expected
        'POST' | MediaType.APPLICATION_FORM_URLENCODED_TYPE                       | Optional.of(bytes('a=1'))   || true
        'POST' | MediaType.valueOf('application/x-www-form-urlencoded;charset=UTF-8') | Optional.of(bytes('a=1')) || true
        'POST' | MediaType.APPLICATION_JSON_TYPE                                  | Optional.of(bytes('{}'))    || false
        'POST' | null                                                             | Optional.of(bytes('a=1'))   || false
        'POST' | MediaType.APPLICATION_FORM_URLENCODED_TYPE                       | Optional.empty()            || false
        'PUT'  | MediaType.APPLICATION_FORM_URLENCODED_TYPE                       | Optional.of(bytes('a=1'))   || false
    }

    def 'the parameters of a form request are read from the body and the URI'() {
        given: 'a form request with a large geometry in the body and the format in the URI'
        String geometry = 'POLYGON((' + (0..2000).collect { "7.${it} 50.${it}" }.join(',') + '))'
        ContainerRequestContext request = request(
                MediaType.APPLICATION_FORM_URLENCODED_TYPE, [f: ['json']])

        when:
        MultivaluedMap<String, String> parameters = ApiRequestDispatcher.getActualQueryParameters(
                request,
                Optional.of(bytes('gebiet=' + URLEncoder.encode(geometry, StandardCharsets.UTF_8) + '&limit=10')),
                true)

        then:
        parameters.getFirst('gebiet') == geometry
        parameters.getFirst('limit') == '10'
        parameters.getFirst('f') == 'json'
    }

    def 'the charset of a form request is respected'() {
        given:
        ContainerRequestContext request = request(
                MediaType.valueOf('application/x-www-form-urlencoded;charset=ISO-8859-1'), [:])

        when:
        MultivaluedMap<String, String> parameters = ApiRequestDispatcher.getActualQueryParameters(
                request,
                Optional.of('name=K%F6ln'.getBytes(StandardCharsets.US_ASCII)),
                true)

        then:
        parameters.getFirst('name') == 'Köln'
    }

    def 'a parameter in both the URI and the body of a form request is rejected'() {
        given:
        ContainerRequestContext request = request(
                MediaType.APPLICATION_FORM_URLENCODED_TYPE, [limit: ['10']])

        when:
        ApiRequestDispatcher.getActualQueryParameters(request, Optional.of(bytes('limit=1000')), true)

        then:
        BadRequestException e = thrown()
        e.message.contains("'limit'")
    }

    def 'other requests only use the parameters in the URI'() {
        given:
        ContainerRequestContext request = request(MediaType.APPLICATION_JSON_TYPE, [f: ['json']])

        when:
        MultivaluedMap<String, String> parameters = ApiRequestDispatcher.getActualQueryParameters(
                request, Optional.of(bytes('{"limit":10}')), false)

        then:
        parameters.keySet() == ['f'] as Set
    }

    def 'a URL-encoded POST operation has a form request body'() {
        when:
        ApiOperation operation = ApiOperation.getResource(
                null, '/search/q', postUrlEncoded, [], [], responseContent(), 'execute q',
                Optional.empty(), Optional.empty(), 'q', READ, [], Optional.empty(), Optional.empty())
                .orElseThrow()

        then:
        operation.hasFormRequestBody() == postUrlEncoded

        where:
        postUrlEncoded << [true, false]
    }

    private ContainerRequestContext request(MediaType mediaType, Map<String, List<String>> uriParameters) {
        MultivaluedMap<String, String> queryParameters = new MultivaluedHashMap<>()
        uriParameters.each { name, values -> queryParameters.put(name, values) }
        UriInfo uriInfo = Stub()
        uriInfo.getQueryParameters() >> queryParameters
        ContainerRequestContext request = Stub()
        request.getMediaType() >> mediaType
        request.getUriInfo() >> uriInfo
        return request
    }

    private static Map<MediaType, ApiMediaTypeContent> responseContent() {
        ApiMediaTypeContent content = new ImmutableApiMediaTypeContent.Builder()
                .ogcApiMediaType(new ImmutableApiMediaType.Builder()
                        .type(new MediaType('application', 'geo+json'))
                        .label('GeoJSON')
                        .parameter('json')
                        .build())
                .schema(new ObjectSchema())
                .schemaRef('#/components/schemas/featureCollection')
                .build()
        return [(content.getOgcApiMediaType().type()): content]
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8)
    }
}
