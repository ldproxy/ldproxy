/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.domain

import de.ii.ogcapi.foundation.domain.ExtensionConfiguration
import spock.lang.Specification

class GmlConfigurationMergeSpec extends Specification {

    static ImmutableGmlConfiguration.Builder baseBuilder() {
        new ImmutableGmlConfiguration.Builder()
                .enabled(true)
    }

    def "mergeInto: identical namespace declared at both levels does not throw"() {
        given: "an API-level and a collection-level config that both declare the same prefix/URI"
        def apiLevel = baseBuilder()
                .putApplicationNamespaces("gmd", "http://www.isotc211.org/2005/gmd")
                .build()
        def collectionLevel = baseBuilder()
                .putApplicationNamespaces("gmd", "http://www.isotc211.org/2005/gmd")
                .build()

        when: "the collection-level config is merged onto the API-level config"
        def merged = collectionLevel.mergeInto(apiLevel as ExtensionConfiguration) as GmlConfiguration

        then: "no duplicate-key exception is thrown and the entry is present once"
        merged.getApplicationNamespaces() == ["gmd": "http://www.isotc211.org/2005/gmd"]
    }

    def "mergeInto: maps are merged as a union with the collection level winning on conflicts"() {
        given:
        def apiLevel = baseBuilder()
                .putApplicationNamespaces("aaa", "http://www.adv-online.de/old")
                .putApplicationNamespaces("gmd", "http://www.isotc211.org/2005/gmd")
                .putCodelistProperties("anlass", "AX_Anlassart")
                .build()
        def collectionLevel = baseBuilder()
                .putApplicationNamespaces("aaa", "http://www.adv-online.de/new")
                .putApplicationNamespaces("xplan", "http://www.xplanung.de/xplangml")
                .putCodelistProperties("funktion", "AX_Funktion_Bauwerk")
                .build()

        when:
        def merged = collectionLevel.mergeInto(apiLevel as ExtensionConfiguration) as GmlConfiguration

        then: "union of keys, collection-level value wins for the shared key"
        merged.getApplicationNamespaces() == [
                "aaa"  : "http://www.adv-online.de/new",
                "gmd"  : "http://www.isotc211.org/2005/gmd",
                "xplan": "http://www.xplanung.de/xplangml"
        ]
        merged.getCodelistProperties() == [
                "anlass"  : "AX_Anlassart",
                "funktion": "AX_Funktion_Bauwerk"
        ]
    }

    def "mergeInto: lists are concatenated and de-duplicated"() {
        given:
        def apiLevel = baseBuilder()
                .addXmlAttributes("a", "b")
                .build()
        def collectionLevel = baseBuilder()
                .addXmlAttributes("b", "c")
                .build()

        when:
        def merged = collectionLevel.mergeInto(apiLevel as ExtensionConfiguration) as GmlConfiguration

        then: "API-level entries first, no duplicates"
        merged.getXmlAttributes() == ["a", "b", "c"]
    }

    def "mergeInto: scalar from the collection level wins, falls back to the API level otherwise"() {
        given:
        def apiLevel = baseBuilder()
                .gmlIdPrefix("_api")
                .defaultNamespace("aaa")
                .build()
        def collectionLevel = baseBuilder()
                .gmlIdPrefix("_collection")
                .build()

        when:
        def merged = collectionLevel.mergeInto(apiLevel as ExtensionConfiguration) as GmlConfiguration

        then:
        merged.getGmlIdPrefix() == "_collection"
        merged.getDefaultNamespace() == "aaa"
    }
}
