/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain

import spock.lang.Specification

class I18nLinkTitleSpec extends Specification {

    // like the default implementation, a missing translation is returned as the key
    static class TestI18n implements I18n {
        Map<String, String> translations

        Set<Locale> getLanguages() { [Locale.ENGLISH] as Set }

        String get(String key) { get(key, Optional.empty()) }

        String get(String key, Optional<Locale> language) { translations.getOrDefault(key, key) }

        Set<String> getKeys() { translations.keySet() }

        Set<String> getKeysWithPrefix(String prefix) { getKeys().findAll { it.startsWith(prefix) } }
    }

    def i18n = new TestI18n(translations: ['versionHistoryLink': 'Version history', 'originalLink': 'Stable feature URI'])

    def 'the title of a link is the translation of the relation type in camel case'() {
        expect:
        i18n.getLinkTitle(rel, Optional.empty()) == Optional.of(title)

        where:
        rel               || title
        'version-history' || 'Version history'
        'original'        || 'Stable feature URI'
    }

    def 'a relation type without a translation has no title'() {
        expect:
        i18n.getLinkTitle(rel, Optional.empty()) == Optional.empty()

        where:
        rel << ['describedby', 'http://www.opengis.net/def/rel/ogc/1.0/data-meta', 'a--b']
    }
}
