/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app

import de.ii.ogcapi.foundation.domain.ClassSchemaCache
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.json.domain.ImmutableJsonConfiguration
import de.ii.ogcapi.processes.app.format.json.ProcessListFormatJson
import de.ii.ogcapi.processes.domain.ImmutableProcessesCoreConfiguration
import spock.lang.Specification

class ProcessesConformanceSpec extends Specification {

    def "the process list JSON format, and with it its conformance class, is enabled only with Processes Core and JSON: #processes, #json"() {
        given:
        def builder = new ImmutableOgcApiDataV2.Builder()
                .id("api")
                .serviceType("OGC_API")
                .addExtensions(new ImmutableJsonConfiguration.Builder().enabled(json).build())
        if (processes != null) {
            builder.addExtensions(new ImmutableProcessesCoreConfiguration.Builder()
                    .enabled(processes)
                    .build())
        }
        def apiData = builder.build()

        when:
        def enabled = new ProcessListFormatJson(Stub(ClassSchemaCache)).isEnabledForApi(apiData)

        then:
        enabled == expected

        where:
        processes | json  || expected
        null      | true  || false
        false     | true  || false
        true      | true  || true
        true      | false || false
    }

    def "the dismiss conformance class is declared only with asynchronous execution: #async"() {
        given:
        def apiData = new ImmutableOgcApiDataV2.Builder()
                .id("api")
                .serviceType("OGC_API")
                .addExtensions(new ImmutableProcessesCoreConfiguration.Builder()
                        .enabled(true)
                        .async(async)
                        .build())
                .build()

        when:
        def uris = new ProcessesCoreBuildingBlock().getConformanceClassUris(apiData)

        then:
        uris.contains("https://www.opengis.net/spec/ogcapi-processes-1/2.0/conf/dismiss") == async

        where:
        async << [false, true]
    }
}
