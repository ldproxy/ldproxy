/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain

import spock.lang.Specification

class EndpointDefinitionCacheSpec extends Specification {

    static class CountingEndpoint extends Endpoint {
        int computed = 0

        CountingEndpoint(ExtensionRegistry extensionRegistry) {
            super(extensionRegistry)
        }

        @Override
        boolean isEnabledForApi(OgcApiDataV2 apiData) {
            return true
        }

        @Override
        protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
            computed++
            return new ImmutableApiEndpointDefinition.Builder()
                    .apiEntrypoint('test')
                    .sortPriority(0)
                    .build()
        }

        @Override
        List<? extends FormatExtension> getResourceFormats() {
            return []
        }
    }

    // revision of data outside the API configuration, for example codelists
    long revision = 1

    ApiDefinitionDependency dependency = [getRevision: { -> revision }] as ApiDefinitionDependency

    ExtensionRegistry registry = [
            getExtensions       : { -> [dependency] },
            getExtensionsForType: { Class type -> type.isInstance(dependency) ? [dependency] : [] }
    ] as ExtensionRegistry

    OgcApiDataV2 apiData = new ImmutableOgcApiDataV2.Builder().id('test').serviceType('OGC_API').build()

    def "a definition is reused while the API and its dependencies are unchanged"() {
        given:
        def endpoint = new CountingEndpoint(registry)

        when:
        endpoint.getDefinition(apiData)
        endpoint.getDefinition(apiData)

        then:
        endpoint.computed == 1
    }

    def "a changed dependency revision derives the definition again"() {
        given:
        def endpoint = new CountingEndpoint(registry)
        endpoint.getDefinition(apiData)

        when:
        revision = 2
        endpoint.getDefinition(apiData)
        endpoint.getDefinition(apiData)

        then:
        endpoint.computed == 2
    }

    def "a changed API configuration derives the definition again"() {
        given:
        def endpoint = new CountingEndpoint(registry)
        endpoint.getDefinition(apiData)

        when:
        endpoint.getDefinition(new ImmutableOgcApiDataV2.Builder().from(apiData).label('changed').build())

        then:
        endpoint.computed == 2
    }
}
