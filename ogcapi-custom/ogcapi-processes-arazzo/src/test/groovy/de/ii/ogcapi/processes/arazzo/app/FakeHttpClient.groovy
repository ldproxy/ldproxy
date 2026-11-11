/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app

import de.ii.xtraplatform.web.domain.HttpClient
import de.ii.xtraplatform.web.domain.ImmutableHttpResponse

import java.nio.charset.StandardCharsets

/** Answers requests like {@link HttpClient#send} by URL and records the requests; other methods are not used. */
class FakeHttpClient {

    static class Sent {
        String method
        String url
        Map<String, String> headers
        String body
    }

    final Map<String, Closure<HttpClient.HttpResponse>> responses = [:]
    final List<Sent> sent = []

    static HttpClient.HttpResponse response(int status, String body, Map<String, String> headers = [:]) {
        return ImmutableHttpResponse.of(status, body.getBytes(StandardCharsets.UTF_8)).withHeaders(headers)
    }

    HttpExternalApis.Sender client() {
        return this.&send as HttpExternalApis.Sender
    }

    HttpClient.HttpResponse send(String method, String url, Map<String, String> headers, Optional<byte[]> body, long maximumSize) {
        sent << new Sent(method: method, url: url, headers: new LinkedHashMap<>(headers), body: body.map { new String(it, StandardCharsets.UTF_8) }.orElse(null))
        Closure<HttpClient.HttpResponse> response = responses[url]
        if (response == null) {
            throw new IllegalArgumentException("Connection refused: " + url)
        }
        return response.call(method)
    }
}
