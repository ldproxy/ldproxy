/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain

import de.ii.xtraplatform.web.domain.URICustomizer
import spock.lang.Specification

class FeatureTransformationContextServiceUrlSpec extends Specification {

    def 'service url is the base path plus the api path'() {
        expect:
        FeatureTransformationContext.getServiceUrl(new URICustomizer(requestUri), basePath, subPath) == expected

        where:
        requestUri                                                         | basePath   | subPath           || expected
        'https://example.com/api/collections/c/items?f=json'               | []         | ['api']           || 'https://example.com/api'
        'https://example.com/vineyards/collections/vineyards/items/1?f=json' | []       | ['vineyards']     || 'https://example.com/vineyards'
        'https://example.com/vineyards/collections/vineyards/items'        | []         | ['vineyards']     || 'https://example.com/vineyards'
        'https://example.com/a/b/collections/b/items'                      | []         | ['a', 'b']        || 'https://example.com/a/b'
        'https://example.com/proxy/api/collections/api/items'              | ['proxy']  | ['api']           || 'https://example.com/proxy/api'
        'https://example.com/api/api/collections/api/items'                | ['api']    | ['api']           || 'https://example.com/api/api'
    }

    def 'missing base path segments are treated as an empty base path'() {
        expect:
        FeatureTransformationContext.getServiceUrl(new URICustomizer('https://example.com/api/collections/api/items'), null, ['api']) == 'https://example.com/api'
    }

    def 'without a matching prefix the path is cut after the api path'() {
        expect:
        FeatureTransformationContext.getServiceUrl(new URICustomizer('https://example.com/api/collections/c/items'), ['other'], ['api']) == 'https://example.com/api'
    }
}
