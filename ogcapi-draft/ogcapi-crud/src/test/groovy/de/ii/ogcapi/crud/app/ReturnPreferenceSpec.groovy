/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crud.app

import de.ii.ogcapi.features.core.domain.FeatureFormatExtension
import de.ii.ogcapi.foundation.domain.ApiMediaType
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType
import jakarta.ws.rs.NotAcceptableException
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The 'return' preference of a request that changes a feature: which media type a representation
 * of the feature uses, and when the preference counts as applied.
 */
class ReturnPreferenceSpec extends Specification {

    static final MediaType GEOJSON = new MediaType('application', 'geo+json')
    static final MediaType GML = new MediaType('application', 'gml+xml')
    static final MediaType MERGE_PATCH = new MediaType('application', 'merge-patch+json')
    static final List<MediaType> ANY = [MediaType.WILDCARD_TYPE]

    @Unroll
    def 'the representation of #contentType with Accept #accept is #expected'() {
        when: 'the encodings of the collection are GeoJSON and GML'
        ApiMediaType selected = EndpointCrud.negotiateRepresentation(
                [format(GEOJSON), format(GML)], contentType, headers(accept))

        then:
        selected.type() == expected

        where:
        contentType | accept                        || expected
        // no 'Accept' header: the encoding of the request body (RFC 9110, 12.5.1)
        GEOJSON     | ANY                           || GEOJSON
        GML         | ANY                           || GML
        // a request body that is not a feature encoding falls back to the first encoding
        MERGE_PATCH | ANY                           || GEOJSON
        // an 'Accept' header that names an encoding wins over the request body
        GEOJSON     | [GML]                         || GML
        GML         | [GEOJSON]                     || GEOJSON
        // 'application/json' matches a '+json' encoding, but not the GML request body
        GML         | [MediaType.APPLICATION_JSON_TYPE] || GEOJSON
        // media type parameters are not part of the comparison
        GML         | [withQuality(GML, '0.9')]     || GML
        withCharset(GEOJSON) | ANY                  || GEOJSON
    }

    @Unroll
    def 'an Accept header that matches no encoding of the collection is rejected: #accept'() {
        when: 'the encodings of the collection are GeoJSON and GML'
        EndpointCrud.negotiateRepresentation(
                [format(GEOJSON), format(GML)], GEOJSON, headers([accept], accept.toString()))

        then: 'the encodings are not offered for another subtype of the same type'
        NotAcceptableException e = thrown()
        e.message.contains(accept.getSubtype())

        where:
        accept << [MediaType.TEXT_HTML_TYPE, new MediaType('text', 'csv'),
                   new MediaType('application', 'vnd.ogc.wkt')]
    }

    def 'a subtype of the same type is not a match'() {
        when: 'the collection offers HTML and a CSV representation is requested'
        EndpointCrud.negotiateRepresentation(
                [format(GEOJSON), format(MediaType.TEXT_HTML_TYPE)], GEOJSON,
                headers([new MediaType('text', 'csv')], 'text/csv'))

        then:
        thrown(NotAcceptableException)
    }

    @Unroll
    def 'the return preference #prefer is applied: #applied'() {
        expect:
        EndpointCrud.isReturnApplied(prefer, response) == applied

        where:
        prefer                    | response                  || applied
        ['return=representation'] | created('feature')        || true
        ['return=representation'] | Response.created(null).build() || false
        ['return=representation'] | Response.status(200).entity('feature').build() || true
        ['return=minimal']        | Response.noContent().build() || true
        // a response body would not follow 'return=minimal', so the preference was not applied
        ['return=minimal']        | created('feature')        || false
        // a response that reports an error follows neither preference
        ['return=representation'] | Response.status(400).entity('error').build() || false
        ['return=minimal']        | Response.status(412).build() || false
        // the endpoints of a feature do not support any other value
        ['return=none']           | Response.noContent().build() || false
        ['handling=strict']       | Response.noContent().build() || false
        []                        | Response.noContent().build() || false
    }

    static MediaType withQuality(MediaType type, String q) {
        return new MediaType(type.getType(), type.getSubtype(), ['q': q])
    }

    static MediaType withCharset(MediaType type) {
        return new MediaType(type.getType(), type.getSubtype(), ['charset': 'utf-8'])
    }

    static ApiMediaType apiMediaType(MediaType type) {
        new ImmutableApiMediaType.Builder().type(type).build()
    }

    FeatureFormatExtension format(MediaType type) {
        FeatureFormatExtension format = Stub(FeatureFormatExtension)
        format.getMediaType() >> apiMediaType(type)
        return format
    }

    HttpHeaders headers(List<MediaType> acceptable, String accept = null) {
        HttpHeaders headers = Stub(HttpHeaders)
        headers.getAcceptableMediaTypes() >> acceptable
        headers.getHeaderString(HttpHeaders.ACCEPT) >> accept
        return headers
    }

    static Response created(String entity) {
        return Response.status(201).entity(entity).build()
    }
}
