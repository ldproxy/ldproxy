/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app

import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.processes.domain.ImmutableProcessesCoreConfiguration
import de.ii.ogcapi.processes.domain.ProcessesCoreConfiguration
import spock.lang.Specification

class CallbackConfigurationSpec extends Specification {

    static final String CALLBACK = "https://www.opengis.net/spec/ogcapi-processes-1/2.0/conf/callback"

    def "the callback conformance class is declared only with allowed callback URIs and async execution: #async, #allowed"() {
        given:
        def apiData = new ImmutableOgcApiDataV2.Builder()
                .id("api")
                .serviceType("OGC_API")
                .addExtensions(new ImmutableProcessesCoreConfiguration.Builder()
                        .enabled(true)
                        .async(async)
                        .allowedCallbackUris(allowed)
                        .build())
                .build()

        expect:
        new ProcessesCoreBuildingBlock().getConformanceClassUris(apiData).contains(CALLBACK) == declared

        where:
        async | allowed                            || declared
        false | []                                 || false
        true  | []                                 || false
        false | ["https://example.com/callbacks/"] || false
        true  | ["https://example.com/callbacks/"] || true
        true  | ["*"]                              || true
    }

    def "the allowed callback URIs of the defaults and of the API are merged"() {
        given:
        ProcessesCoreConfiguration defaults = new ImmutableProcessesCoreConfiguration.Builder()
                .allowedCallbackUris(["https://a.example.com/", "https://b.example.com/"])
                .build()
        ProcessesCoreConfiguration api = new ImmutableProcessesCoreConfiguration.Builder()
                .allowedCallbackUris(["https://b.example.com/", "https://c.example.com/"])
                .build()

        when:
        ProcessesCoreConfiguration merged = (ProcessesCoreConfiguration) api.mergeInto(defaults)

        then:
        merged.getAllowedCallbackUris() == ["https://a.example.com/", "https://b.example.com/", "https://c.example.com/"]
    }
}
