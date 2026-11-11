/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app

import de.ii.ogcapi.foundation.domain.I18n
import de.ii.ogcapi.foundation.domain.Link
import de.ii.xtraplatform.web.domain.URICustomizer
import spock.lang.Specification

class StoredQueriesLinkGeneratorSpec extends Specification {

    I18n i18n = Stub(I18n)

    def setup() {
        i18n.get(_, _) >> 'title'
    }

    def 'the self link of a stored query is the query resource, not a template (#parameters)'() {
        when:
        List<Link> links = new StoredQueriesLinkGenerator().generateStoredQueryLinks(
                new URICustomizer('http://localhost/api/search?f=json'),
                'Query', 'test-query', parameters as Set, false, i18n, Optional.empty())
        Link self = links.find { it.rel == 'self' }

        then:
        self.href == 'http://localhost/api/search/test-query'
        self.templated == null
        self.varBase == null
        links.findAll { it.templated }.isEmpty()

        where:
        parameters << [[], ['gebiet'], ['gebiet', 'datum']]
    }
}
