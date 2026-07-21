/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.domain

import spock.lang.Specification
import spock.lang.Unroll

class ValueWrapElementSpec extends Specification {

    @Unroll
    def 'parses "#entry"'() {
        when:
        def el = ValueWrapElement.parse(entry)

        then:
        el.getName() == name
        el.getAttributes() == attributes
        el.isEmptyElement() == emptyElement

        where:
        entry                                        || name            | attributes                      | emptyElement
        'beginnt'                                    || 'beginnt'       | [:]                             | false
        'gco:Record'                                 || 'gco:Record'    | [:]                             | false
        'gco:Record[xsi:type=gml:doubleList]'        || 'gco:Record'    | ['xsi:type': 'gml:doubleList']  | false
        'gmd:valueUnit[xlink:href=urn:adv:uom:m]/'   || 'gmd:valueUnit' | ['xlink:href': 'urn:adv:uom:m'] | true
        "gmd:valueUnit[xlink:href='urn:adv:uom:m']/" || 'gmd:valueUnit' | ['xlink:href': 'urn:adv:uom:m'] | true
        'el[a=1][b=two words]'                       || 'el'            | ['a': '1', 'b': 'two words']    | false
        'el/'                                        || 'el'            | [:]                             | true
    }

    @Unroll
    def 'rejects "#entry"'() {
        when:
        ValueWrapElement.parse(entry)

        then:
        thrown(IllegalArgumentException)

        where:
        entry << ['', '/', '[a=b]', 'el[a=b', 'el[a]', 'el[=b]', 'el[a=b]x', 'el[a=b][a=c]', 'el name']
    }

    @Unroll
    def 'toString round-trips "#entry"'() {
        expect:
        ValueWrapElement.parse(entry).toString() == entry

        where:
        entry << ['beginnt', 'gco:Record[xsi:type=gml:doubleList]', 'gmd:valueUnit[xlink:href=urn:adv:uom:m]/']
    }
}
