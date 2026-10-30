/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app

import de.ii.ogcapi.foundation.domain.ImmutableApiMetadata
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import spock.lang.Specification

class McpServerInfoSpec extends Specification {

    static final URI LANDING_PAGE = URI.create("https://example.com/countries")

    static OgcApiDataV2 api(String version) {
        def builder = new ImmutableOgcApiDataV2.Builder()
                .id('countries')
                .serviceType('OGC_API')
                .label('Countries')
                .description('The countries of the world.')
        if (version != null) {
            builder.metadata(new ImmutableApiMetadata.Builder().version(version).build())
        }
        return builder.build()
    }

    def 'the server information identifies the API and its version'() {
        when:
        def info = McpServerImpl.serverInfo(api('1.4.0'), LANDING_PAGE)

        then:
        info.name() == 'countries'
        info.title() == 'Countries'
        info.version() == '1.4.0'
        info.websiteUrl() == 'https://example.com/countries'
    }

    def 'without a configured version, the version is the default of the OpenAPI definition'() {
        when:
        def info = McpServerImpl.serverInfo(api(null), LANDING_PAGE)

        then:
        info.version() == '1.0.0'
    }

    def 'the instructions state a configured version'() {
        when:
        def instructions = McpServerImpl.instructions(api('1.4.0'))

        then:
        instructions == 'Countries: The countries of the world.\n\nVersion of the API: 1.4.0'
    }

    def 'without a configured version, the instructions do not state one'() {
        when:
        def instructions = McpServerImpl.instructions(api(null))

        then:
        instructions == 'Countries: The countries of the world.'
    }
}
