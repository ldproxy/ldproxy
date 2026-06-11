/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.domain

import de.ii.ogcapi.foundation.domain.AliasConfiguration
import spock.lang.Specification

class GmlConfigurationAliasSpec extends Specification {

    static ImmutableGmlConfiguration.Builder baseBuilder() {
        new ImmutableGmlConfiguration.Builder()
                .enabled(true)
    }

    def "isUseAlias defaults to false when getUseAlias is null"() {
        given:
        def config = baseBuilder().build()

        expect:
        config.getUseAlias() == null
        !config.isUseAlias()
    }

    def "isUseAlias reflects getUseAlias when set"() {
        given:
        def config = baseBuilder().useAlias(value).build()

        expect:
        config.getUseAlias() == value
        config.isUseAlias() == value

        where:
        value << [true, false]
    }

    def "mergeInto: useAlias from this wins when set, falls back to source otherwise"() {
        given:
        def thisConfig = baseBuilder().useAlias(thisValue).build()
        def sourceConfig = baseBuilder().useAlias(sourceValue).build()

        when:
        def merged = thisConfig.mergeInto(sourceConfig as AliasConfiguration)

        then:
        merged.getUseAlias() == expected

        where:
        thisValue | sourceValue | expected
        true      | false       | true
        false     | true        | false
        null      | true        | true
        null      | false       | false
        null      | null        | null
    }
}
