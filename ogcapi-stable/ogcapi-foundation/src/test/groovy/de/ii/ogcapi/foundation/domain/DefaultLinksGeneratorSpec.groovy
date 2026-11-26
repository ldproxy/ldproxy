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

class DefaultLinksGeneratorSpec extends Specification {

    static class KeyI18n implements I18n {
        Set<Locale> getLanguages() { [Locale.ENGLISH] as Set }

        String get(String key) { key }

        String get(String key, Optional<Locale> language) { key }

        Set<String> getKeys() { [] as Set }

        Set<String> getKeysWithPrefix(String prefix) { [] as Set }
    }

    def generator = new DefaultLinksGenerator()
    def i18n = new KeyI18n()

    def 'without alternate media types the self link keeps the query parameters except f and lang'() {
        given:
        def uri = new URICustomizer('https://example.com/api/search/q?postcode=3811LM&huisnummer=1&f=json&lang=en')

        when:
        def links = generator.generateLinks(uri, ApiMediaType.JSON_MEDIA_TYPE, [], i18n, Optional.empty())

        then:
        links.size() == 1
        links[0].rel == 'self'
        links[0].href == 'https://example.com/api/search/q?postcode=3811LM&huisnummer=1'
    }

    def 'with alternate media types the self link and the alternate links keep the query parameters'() {
        given:
        def uri = new URICustomizer('https://example.com/api/search/q?postcode=3811LM&huisnummer=1')

        when:
        def links = generator.generateLinks(uri, ApiMediaType.JSON_MEDIA_TYPE, [ApiMediaType.HTML_MEDIA_TYPE], i18n, Optional.empty())

        then:
        links*.rel == ['self', 'alternate']
        links*.href == [
            'https://example.com/api/search/q?postcode=3811LM&huisnummer=1&f=json',
            'https://example.com/api/search/q?postcode=3811LM&huisnummer=1&f=html'
        ]
    }
}
