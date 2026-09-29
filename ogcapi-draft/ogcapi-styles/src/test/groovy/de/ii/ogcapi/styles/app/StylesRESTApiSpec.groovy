/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app

import groovy.json.JsonSlurper
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

/**
 * Manual smoke spec for the styles listing. Gated on {@code SUT_URL} so it is skipped in CI; run
 * it against a running ldproxy whose API exposes the STYLES building block.
 *
 * <p>Uses {@link java.net.http.HttpClient} so the spec works on Groovy 4 — the former
 * http-builder dependency references {@code groovy.util.slurpersupport.GPathResult}, which
 * Groovy 4 removed.
 *
 * <p>{@code SUT_PATH} defaults to the demo API this spec was written against.
 */
@Requires({env['SUT_URL'] != null})
class StylesRESTApiSpec extends Specification {

    static final String SUT_URL = System.getenv('SUT_URL')
    static final String SUT_PATH = System.getenv('SUT_PATH') ?: '/daraa'

    @Shared
    HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

    def 'GET Request for the styles Page of the dataset'() {

        when:
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(SUT_URL + SUT_PATH + '/styles'))
                        .header('Accept', 'application/json')
                        .GET()
                        .build(),
                BodyHandlers.ofString())

        then:
        response.statusCode() == 200

        and:
        Map parsed = (Map) new JsonSlurper().parseText(response.body())
        parsed.containsKey('styles')
        Map first = (Map) ((List) parsed.get('styles')).get(0)
        first.get('id') != null || first.get('identifier') != null
        first.get('links') != null
    }
}
