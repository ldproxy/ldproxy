/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain

import de.ii.xtraplatform.features.domain.PropertyLink
import spock.lang.Specification

class PropertyLinkResolverSpec extends Specification {

    static final String SERVICE = "https://example.com/api"
    static final String COLLECTION = "https://example.com/api/collections/test"
    static final String FEATURE = "https://example.com/api/collections/test/items/f1"

    static String resolve(String template, String value) {
        return PropertyLinkResolver.resolve(
                PropertyLink.of("related", template, value, Optional.empty()),
                SERVICE, COLLECTION, FEATURE)
    }

    def 'all template parameters are substituted'() {
        expect:
        resolve(template, value) == expected

        where:
        template                              | value  || expected
        '{{serviceUri}}/{{value}}'            | "abc"  || "https://example.com/api/abc"
        '{{collectionUri}}?code={{value}}'    | "abc"  || "https://example.com/api/collections/test?code=abc"
        '{{featureUri}}?datetime={{value}}'   | "abc"  || "https://example.com/api/collections/test/items/f1?datetime=abc"
        'https://other.com/reg/{{value}}'     | "abc"  || "https://other.com/reg/abc"
    }

    def 'the value is percent-encoded'() {
        expect:
        resolve('{{featureUri}}?datetime={{value}}', "2026-05-12T11:46:39Z")
                == "https://example.com/api/collections/test/items/f1?datetime=2026-05-12T11%3A46%3A39Z"
        resolve('{{serviceUri}}/{{value}}', "a b/c&d")
                == "https://example.com/api/a%20b%2Fc%26d"
    }

    def 'the request URIs are not encoded'() {
        expect:
        resolve('{{featureUri}}', "ignored") == FEATURE
    }
}
