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
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

/**
 * Manual smoke spec for the HTML map representation of a style. Gated on {@code SUT_URL} so it is
 * skipped in CI; run it against a running ldproxy whose API exposes the STYLES building block with
 * a map representation for the style under test.
 *
 * <p>Uses {@link java.net.http.HttpClient} so the spec works on Groovy 4 — the former
 * http-builder dependency references {@code groovy.util.slurpersupport.GPathResult}, which
 * Groovy 4 removed.
 *
 * <p>{@code SUT_PATH} defaults to the demo API this spec was written against; {@code SUT_MAP_STYLE}
 * has no default, since a deployment without map representations has no style to name.
 */
// Also gated on SUT_MAP_STYLE, because the resource under test exists only where the MAPS
// building block is enabled: a style can be present in /styles while /maps/{styleId} is 404,
// which is the case in both deployments this was run against. Set SUT_MAP_STYLE to the id of a
// style that has a map representation.
@Requires({env['SUT_URL'] != null && env['SUT_MAP_STYLE'] != null})
class StylesRepresentationRESTApiSpec extends Specification {

    static final String SUT_URL = System.getenv('SUT_URL')
    static final String SUT_PATH = System.getenv('SUT_PATH') ?: '/daraa'
    static final String SUT_STYLE = System.getenv('SUT_MAP_STYLE')

    @Shared
    HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

    def 'Get Request for one Style Representation/Map'() {

        when:
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(SUT_URL + SUT_PATH + '/maps/' + SUT_STYLE))
                        .header('Accept', 'text/html')
                        .GET()
                        .build(),
                BodyHandlers.ofString())

        then:
        response.statusCode() == 200
    }
}
