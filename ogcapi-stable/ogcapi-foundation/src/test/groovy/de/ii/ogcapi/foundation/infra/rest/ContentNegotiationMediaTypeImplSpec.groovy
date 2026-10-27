/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.infra.rest

import de.ii.ogcapi.foundation.domain.ApiMediaType
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.UriInfo
import org.glassfish.jersey.internal.MapPropertiesDelegate
import org.glassfish.jersey.server.ContainerRequest
import spock.lang.Specification

class ContentNegotiationMediaTypeImplSpec extends Specification {

    static final ApiMediaType JSON = new ImmutableApiMediaType.Builder()
            .type(MediaType.APPLICATION_JSON_TYPE)
            .label("JSON")
            .parameter("json")
            .build()

    static final ApiMediaType ANY = new ImmutableApiMediaType.Builder()
            .type(MediaType.WILDCARD_TYPE)
            .label("Any")
            .parameter("any")
            .qs(100)
            .build()

    ContentNegotiationMediaTypeImpl negotiation = new ContentNegotiationMediaTypeImpl()

    def "for Accept '#accept' the #expected.label format is selected"() {
        expect:
        negotiate(accept, [JSON, ANY]) == Optional.of(expected)

        where:
        accept                                 || expected
        "application/json"                     || JSON
        "*/*"                                  || JSON
        "application/geo+json"                 || ANY
        "text/csv, application/geo+json;q=0.9" || ANY
    }

    def "a format with a reduced quality source is selected when it is the only match"() {
        expect:
        negotiate("image/png", [ANY]) == Optional.of(ANY)
    }

    def "nothing is selected when no format matches"() {
        expect:
        negotiate("image/png", [JSON]).isEmpty()
    }

    private Optional<ApiMediaType> negotiate(String accept, List<ApiMediaType> supported) {
        negotiation.negotiateMediaType(request(accept), Stub(HttpHeaders), Stub(UriInfo), supported as LinkedHashSet)
    }

    private static ContainerRequest request(String accept) {
        def request = new ContainerRequest(
                URI.create("http://localhost/"),
                URI.create("http://localhost/api/jobs/job-1/results"),
                "GET",
                null,
                new MapPropertiesDelegate(),
                null)
        request.header(HttpHeaders.ACCEPT, accept)
        request
    }
}
