/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.domain

import spock.lang.Specification

/**
 * Locks {@link GmlConfiguration#checkSchemaLocations}: a {@code schemaLocations} value that cannot
 * be a document URI fails the config build (service load) rather than degrading to a per-request
 * "no protocol" warning. The canonical trip-wire is a sibling option mis-indented into the map,
 * which turns its entries into bare tokens (e.g. {@code objectTypeNamespaces: } → empty,
 * {@code LI_Lineage: gmd} → {@code gmd}). Templated values are left to runtime substitution.
 */
class SchemaLocationsCheckSpec extends Specification {

    static config(Map<String, String> schemaLocations) {
        new ImmutableGmlConfiguration.Builder()
                .schemaLocations(schemaLocations)
                .build()
    }

    def 'an absolute https URI is accepted'() {
        when:
        config(['adv': 'https://repository.gdi-de.org/schemas/adv/nas/7.1/aaa.xsd'])

        then:
        noExceptionThrown()
    }

    def 'a templated location is accepted (resolved at request time)'() {
        when:
        config(['ns1': '{{serviceUrl}}/resources/ns1.xsd'])

        then:
        noExceptionThrown()
    }

    def 'an empty schemaLocations map is accepted'() {
        when:
        config([:])

        then:
        noExceptionThrown()
    }

    def 'a bare token (mis-indented sibling option folded into the map) is rejected'() {
        when: 'LI_Lineage: gmd lands as a schemaLocations value'
        config(['adv': 'https://example.com/aaa.xsd', 'LI_Lineage': 'gmd'])

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("'LI_Lineage'")
        e.message.contains('absolute URI')
    }

    def 'a blank location is rejected'() {
        when: 'the mis-indented objectTypeNamespaces: key carries an empty value'
        config(['objectTypeNamespaces': ''])

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('no document URI')
    }
}
