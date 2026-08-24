/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app

import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

/**
 * Manual smoke spec for the styles manager (write path). Gated on {@code SUT_URL} so it is skipped
 * in CI; run it against a running ldproxy whose API has the STYLES building block with
 * {@code managerEnabled}, and note that it overwrites the style it targets.
 *
 * <p>Uses {@link java.net.http.HttpClient} so the spec works on Groovy 4 — the former
 * http-builder dependency references {@code groovy.util.slurpersupport.GPathResult}, which
 * Groovy 4 removed.
 *
 * <p>{@code SUT_PATH} and {@code SUT_STYLE} default to the demo API this spec was written against.
 */
@Requires({env['SUT_URL'] != null})
class StylesManagerRESTApiSpec extends Specification {

    static final String SUT_URL = System.getenv('SUT_URL')
    static final String SUT_PATH = System.getenv('SUT_PATH') ?: '/daraa'
    static final String SUT_STYLE = System.getenv('SUT_STYLE') ?: 'default'

    @Shared
    HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

    def 'PUT Request for a style of the dataset'() {

        when:
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(SUT_URL + SUT_PATH + '/styles/' + SUT_STYLE))
                        .header('Accept', 'application/json')
                        .header('Content-Type', 'application/json')
                        .PUT(BodyPublishers.ofString('{"id": "' + SUT_STYLE + '"}'))
                        .build(),
                BodyHandlers.ofString())

        then:
        response.statusCode() == 204

        and:
        response.body().isEmpty()
    }
}
