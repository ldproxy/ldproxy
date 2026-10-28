/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.infra.rest

import de.ii.ogcapi.foundation.domain.ApiErrorMessage
import de.ii.ogcapi.foundation.domain.ExceptionFormatExtension
import de.ii.ogcapi.foundation.domain.ExtensionRegistry
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType
import de.ii.ogcapi.foundation.domain.TypedClientErrorException
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import spock.lang.Specification

class ExceptionMapperSpec extends Specification {

    static final String RESULT_NOT_READY = "http://www.opengis.net/def/exceptions/ogcapi-features-11/1.0/result-not-ready"

    ExceptionMapper mapper

    def setup() {
        ExceptionFormatExtension format = Stub() {
            getMediaType() >> new ImmutableApiMediaType.Builder()
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .label("JSON")
                    .parameter("json")
                    .build()
            getExceptionEntity(_) >> { ApiErrorMessage message -> message }
        }
        ExtensionRegistry registry = Stub() {
            getExtensionsForType(ExceptionFormatExtension) >> [format]
        }
        ContentNegotiationMediaType negotiation = Stub() {
            negotiateMediaType(*_) >> Optional.empty()
        }
        mapper = new ExceptionMapper(registry, negotiation)
    }

    def "the type of a typed client error is reported"() {
        when:
        Response response = mapper.toResponse(new TypedClientErrorException(
                "The results of job 'job-1' are not ready yet.", Response.Status.NOT_FOUND, RESULT_NOT_READY))
        ApiErrorMessage message = response.getEntity() as ApiErrorMessage

        then:
        response.getStatus() == 404
        message.getType() == RESULT_NOT_READY
        message.getDetails() == "The results of job 'job-1' are not ready yet."
    }

    def "a client error without a type has no type"() {
        when:
        Response response = mapper.toResponse(new NotFoundException("No job found with job id 'job-1'."))
        ApiErrorMessage message = response.getEntity() as ApiErrorMessage

        then:
        response.getStatus() == 404
        message.getType() == null
    }

    def "without a negotiated media type the error is not encoded as HTML"() {
        given:
        ExtensionRegistry registry = Stub() {
            getExtensionsForType(ExceptionFormatExtension) >> [exceptionFormat(MediaType.TEXT_HTML_TYPE), exceptionFormat(MediaType.valueOf("application/problem+json"))]
        }
        ContentNegotiationMediaType negotiation = Stub() {
            negotiateMediaType(*_) >> Optional.empty()
        }

        when:
        Response response = new ExceptionMapper(registry, negotiation).toResponse(new NotFoundException("not found"))

        then:
        response.getMediaType() == MediaType.valueOf("application/problem+json")
    }

    ExceptionFormatExtension exceptionFormat(MediaType mediaType) {
        return Stub(ExceptionFormatExtension) {
            getMediaType() >> new ImmutableApiMediaType.Builder()
                    .type(mediaType)
                    .label(mediaType.getSubtype())
                    .parameter(mediaType.getSubtype())
                    .build()
            getExceptionEntity(_) >> { ApiErrorMessage message -> message }
        }
    }

    def "the status of a server error is kept"() {
        when:
        Response response = mapper.toResponse(new WebApplicationException("timeout", Response.Status.GATEWAY_TIMEOUT))
        ApiErrorMessage message = response.getEntity() as ApiErrorMessage

        then:
        response.getStatus() == 504
        message.getCode() == 504
    }
}
