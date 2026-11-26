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
import de.ii.xtraplatform.base.domain.AppContext
import de.ii.xtraplatform.web.domain.ForwardedUri
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.PathSegment
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
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
            getExceptionEntity(_, _) >> { ApiErrorMessage message, String apiPath -> message }
        }
        ExtensionRegistry registry = Stub() {
            getExtensionsForType(ExceptionFormatExtension) >> [format]
        }
        ContentNegotiationMediaType negotiation = Stub() {
            negotiateMediaType(*_) >> Optional.empty()
        }
        mapper = mapper(registry, negotiation, [], ["bag", "collections"], null)
    }

    ExceptionMapper mapper(ExtensionRegistry registry, ContentNegotiationMediaType negotiation,
                           List<String> pathPrefix, List<String> requestPath, String forwardedPrefix) {
        AppContext appContext = Stub()
        appContext.getPathPrefix() >> pathPrefix
        UriInfo uriInfo = Stub()
        uriInfo.getPathSegments(false) >> requestPath.collect { String path ->
            PathSegment segment = Stub()
            segment.getPath() >> path
            segment
        }
        HttpHeaders httpHeaders = Stub()
        httpHeaders.getHeaderString(ForwardedUri.X_FORWARDED_PREFIX) >> forwardedPrefix
        ExceptionMapper exceptionMapper = new ExceptionMapper(registry, negotiation, appContext)
        exceptionMapper.@uriInfo = uriInfo
        exceptionMapper.@httpHeaders = httpHeaders
        return exceptionMapper
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
        Response response = mapper(registry, negotiation, [], ["bag"], null).toResponse(new NotFoundException("not found"))

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
            getExceptionEntity(_, _) >> { ApiErrorMessage message, String apiPath -> message }
        }
    }

    def "the error format receives the external path of the API"() {
        given:
        ExceptionFormatExtension format = Stub()
        format.getMediaType() >> new ImmutableApiMediaType.Builder()
                .type(MediaType.TEXT_HTML_TYPE)
                .label("HTML")
                .parameter("html")
                .build()
        format.getExceptionEntity(_, _) >> { ApiErrorMessage message, String path -> path }
        ExtensionRegistry registry = Stub()
        registry.getExtensionsForType(ExceptionFormatExtension) >> [format]
        ContentNegotiationMediaType negotiation = Stub()
        negotiation.negotiateMediaType(*_) >> Optional.empty()

        when:
        Response response = mapper(registry, negotiation, pathPrefix, requestPath, forwardedPrefix)
                .toResponse(new NotFoundException("not found"))

        then:
        response.getEntity() == apiPath

        where:
        pathPrefix         | requestPath                            | forwardedPrefix || apiPath
        []                 | ["bag", "collections", "pand", "items"] | null            || "/bag"
        ["rest", "services"] | ["bag", "styles", "x"]              | null            || "/rest/services/bag"
        ["rest", "services"] | ["bag", "v1", "collections"]        | "/proxy/"       || "/proxy/bag"
        ["rest", "services"] | [""]                                | null            || "/rest/services"
        []                 | []                                     | null            || ""
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
