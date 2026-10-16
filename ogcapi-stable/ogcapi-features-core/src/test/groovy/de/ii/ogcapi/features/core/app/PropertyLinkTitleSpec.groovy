/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app

import de.ii.ogcapi.foundation.domain.I18n
import de.ii.xtraplatform.features.domain.PropertyLink
import spock.lang.Specification

class PropertyLinkTitleSpec extends Specification {

    // like the default implementation, a missing translation is returned as the key
    static class TestI18n implements I18n {
        Map<String, String> translations = ['alternateLink': 'Alternate representation']

        Set<Locale> getLanguages() { [Locale.ENGLISH] as Set }

        String get(String key) { get(key, Optional.empty()) }

        String get(String key, Optional<Locale> language) { translations.getOrDefault(key, key) }

        Set<String> getKeys() { translations.keySet() }

        Set<String> getKeysWithPrefix(String prefix) { getKeys().findAll { it.startsWith(prefix) } }
    }

    def i18n = new TestI18n()

    def 'a relation type without a translation has the title of the property link'() {
        expect:
        FeaturesCoreQueriesHandlerImpl.getLinkTitle(
                i18n, PropertyLink.of('describedby', '{{value}}', 'x', Optional.of('Owner')), Optional.empty()) == Optional.of('Owner')
    }

    def 'a relation type without a translation and a property link without a title have no title'() {
        expect:
        FeaturesCoreQueriesHandlerImpl.getLinkTitle(
                i18n, PropertyLink.of('describedby', '{{value}}', 'x', Optional.empty()), Optional.empty()) == Optional.empty()
    }

    def 'a translation of the relation type is the title'() {
        expect:
        FeaturesCoreQueriesHandlerImpl.getLinkTitle(
                i18n, PropertyLink.of('alternate', '{{value}}', 'x', Optional.of('Owner')), Optional.empty()) == Optional.of('Alternate representation')
    }
}
