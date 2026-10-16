/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.infra.rest

import de.ii.ogcapi.foundation.domain.ListParameterValues
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.StringSchema
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.MultivaluedHashMap
import jakarta.ws.rs.core.MultivaluedMap
import jakarta.ws.rs.core.UriInfo
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class EncodedCommaSpec extends Specification {

    OgcApiDataV2 apiData = Stub(OgcApiDataV2)

    def 'an encoded comma stays within the item of a list'() {
        given:
        OgcApiQueryParameter list = parameter('name', new ArraySchema().items(new StringSchema()))
        OgcApiQueryParameter single = parameter('filter', new StringSchema())
        ContainerRequestContext request = request(MediaType.APPLICATION_JSON_TYPE,
                [name: ['Yemen%2C%20People%27s%20Republic%20of,Aden'], filter: ['a%20IN%20(1%2C2)']],
                [name: ["Yemen, People's Republic of,Aden"], filter: ['a IN (1,2)']])

        when:
        MultivaluedMap<String, String> parameters = ApiRequestDispatcher.withEncodedCommasInLists(
                apiData, [list, single],
                ApiRequestDispatcher.getRawQueryParameters(Optional.of("name=Yemen%2C%20People%27s%20Republic%20of,Aden&filter=a%20IN%20(1%2C2)"), request, Optional.empty(), false),
                ApiRequestDispatcher.getActualQueryParameters(request, Optional.empty(), false))

        then:
        ListParameterValues.split(parameters.getFirst('name')) == ["Yemen, People's Republic of", "Aden"]
        parameters.getFirst('filter') == 'a IN (1,2)'
    }

    def 'an encoded comma stays within the item of a list in a form'() {
        given:
        OgcApiQueryParameter list = parameter('name', new ArraySchema().items(new StringSchema()))
        ContainerRequestContext request = request(MediaType.APPLICATION_FORM_URLENCODED_TYPE, [:], [:])
        Optional<byte[]> body = Optional.of('name=a%2Cb%2Cc,d'.getBytes(StandardCharsets.UTF_8))

        when:
        MultivaluedMap<String, String> parameters = ApiRequestDispatcher.withEncodedCommasInLists(
                apiData, [list],
                ApiRequestDispatcher.getRawQueryParameters(Optional.empty(), request, body, true),
                ApiRequestDispatcher.getActualQueryParameters(request, body, true))

        then:
        ListParameterValues.split(parameters.getFirst('name')) == ['a,b,c', 'd']
    }

    def 'an unencoded comma separates the items'() {
        given:
        OgcApiQueryParameter list = parameter('sortby', new ArraySchema().items(new StringSchema()))
        ContainerRequestContext request = request(MediaType.APPLICATION_JSON_TYPE, [:], [sortby: ['a,b']])

        when:
        MultivaluedMap<String, String> parameters = ApiRequestDispatcher.withEncodedCommasInLists(
                apiData, [list],
                ApiRequestDispatcher.getRawQueryParameters(Optional.of('sortby=a,b'), request, Optional.empty(), false),
                ApiRequestDispatcher.getActualQueryParameters(request, Optional.empty(), false))

        then:
        ListParameterValues.split(parameters.getFirst('sortby')) == ['a', 'b']
    }

    private OgcApiQueryParameter parameter(String name, io.swagger.v3.oas.models.media.Schema<?> schema) {
        OgcApiQueryParameter param = Stub(OgcApiQueryParameter)
        param.getName() >> name
        param.getExplode() >> false
        param.getSchema(_ as OgcApiDataV2) >> schema
        return param
    }

    private ContainerRequestContext request(MediaType mediaType, Map<String, List<String>> raw, Map<String, List<String>> decoded) {
        UriInfo uriInfo = Stub(UriInfo)
        uriInfo.getQueryParameters(false) >> new MultivaluedHashMap<String, String>(raw.collectEntries { k, v -> [k, v[0]] })
        uriInfo.getQueryParameters() >> new MultivaluedHashMap<String, String>(decoded.collectEntries { k, v -> [k, v[0]] })
        ContainerRequestContext request = Stub(ContainerRequestContext)
        request.getUriInfo() >> uriInfo
        request.getMediaType() >> mediaType
        return request
    }
}
