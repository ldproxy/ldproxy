/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app

import de.ii.ogcapi.features.search.domain.ImmutableSearchConfiguration
import de.ii.ogcapi.foundation.domain.HttpMethods
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.foundation.domain.SchemaValidator
import spock.lang.Specification

class HeaderPreferSearchSpec extends Specification {

    HeaderPreferSearch header = new HeaderPreferSearch(Stub(SchemaValidator))

    def "the Prefer header applies to #method #path when search async is enabled: #applicable"() {
        expect:
        header.isApplicable(apiData("async-enabled", true), path, method) == applicable

        where:
        method           | path                                || applicable
        HttpMethods.POST | "/search"                           || true
        HttpMethods.GET  | "/search/{queryId}"                 || true
        HttpMethods.GET  | "/search"                           || false
        HttpMethods.POST | "/search/{queryId}"                 || false
        HttpMethods.PUT  | "/search/{queryId}"                 || false
        HttpMethods.GET  | "/search/{queryId}/definition"      || false
        HttpMethods.POST | "/collections/{collectionId}/items" || false
    }

    def "the Prefer header does not apply to #method #path when search async is #async"() {
        expect:
        !header.isApplicable(apiData("async-" + async, async), path, method)

        where:
        async | method           | path
        false | HttpMethods.POST | "/search"
        false | HttpMethods.GET  | "/search/{queryId}"
        null  | HttpMethods.POST | "/search"
        null  | HttpMethods.GET  | "/search/{queryId}"
    }

    private static OgcApiDataV2 apiData(String id, Boolean async) {
        new ImmutableOgcApiDataV2.Builder()
                .id(id)
                .addExtensions(new ImmutableSearchConfiguration.Builder()
                        .enabled(true)
                        .async(async)
                        .build())
                .build()
    }
}
