/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app

import de.ii.xtraplatform.features.domain.ImmutableResultReduced
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.InternalServerErrorException
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.core.MediaType
import spock.lang.Specification

class TimeMapResponseSpec extends Specification {

    static final MediaType JSON = MediaType.APPLICATION_JSON_TYPE

    def "a failed query is an error, not an empty Time Map"() {
        given:
        def result = ImmutableResultReduced.<byte[]> builder()
                .isEmpty(true)
                .hasFeatures(true)
                .error(new IllegalStateException("query failed"))
                .build()

        when:
        VersionedFeaturesQueriesHandlerImpl.toResponse(result, JSON)

        then:
        thrown(InternalServerErrorException)
    }

    def "an invalid value in the query is a bad request"() {
        given:
        def result = ImmutableResultReduced.<byte[]> builder()
                .isEmpty(true)
                .hasFeatures(false)
                .error(new IllegalArgumentException("invalid value"))
                .build()

        when:
        VersionedFeaturesQueriesHandlerImpl.toResponse(result, JSON)

        then:
        thrown(BadRequestException)
    }

    def "a feature without versions is not found"() {
        given:
        def result = ImmutableResultReduced.<byte[]> builder()
                .isEmpty(true)
                .hasFeatures(false)
                .build()

        when:
        VersionedFeaturesQueriesHandlerImpl.toResponse(result, JSON)

        then:
        thrown(NotFoundException)
    }

    def "the Time Map of a feature is returned"() {
        given:
        byte[] body = '{"mementos":[]}'.bytes
        def result = ImmutableResultReduced.<byte[]> builder()
                .isEmpty(false)
                .hasFeatures(true)
                .reduced(body)
                .build()

        when:
        def response = VersionedFeaturesQueriesHandlerImpl.toResponse(result, JSON)

        then:
        response.status == 200
        response.entity == body
        response.mediaType == JSON
    }
}
