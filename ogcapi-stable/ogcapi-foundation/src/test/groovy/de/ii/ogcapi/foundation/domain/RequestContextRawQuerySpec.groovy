/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain

import de.ii.xtraplatform.web.domain.URICustomizer
import spock.lang.Specification

class RequestContextRawQuerySpec extends Specification {

    // the request URI of JAX-RS writes every comma of the query as %2C
    static final URI REQUEST_URI = URI.create("https://example.org/bag/collections/pand/items?weglaten=a%2Cb&land=Bosnia%2C%20Herzegovina&f=json")

    def 'the links of a request keep the commas of the query as the client sent it'() {
        when:
        URICustomizer uri = AbstractRequestContext.withQuery(new URICustomizer(REQUEST_URI), "weglaten=a,b&land=Bosnia%2C%20Herzegovina&f=json").orElseThrow()

        then:
        uri.toString() == "https://example.org/bag/collections/pand/items?weglaten=a,b&land=Bosnia%2C%20Herzegovina&f=json"
        uri.copy().removeParameters("f").setParameter("offset", "10").toString() == "https://example.org/bag/collections/pand/items?weglaten=a,b&land=Bosnia%2C%20Herzegovina&offset=10"
    }

    def 'a character that is not allowed in a query is encoded'() {
        expect:
        AbstractRequestContext.withQuery(new URICustomizer(REQUEST_URI), "q=a b|c&naam=Bühl").orElseThrow().build().getRawQuery() == "q=a%20b%7Cc&naam=B%C3%BChl"
    }

    def 'a query with an invalid escape sequence is not used'() {
        expect:
        AbstractRequestContext.withQuery(new URICustomizer(REQUEST_URI), "q=100%zz").isEmpty()
    }
}
