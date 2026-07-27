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

class XmlPathElementSpec extends Specification {

    @Unroll
    def 'parses "#entry"'() {
        when:
        def el = XmlPathElement.parse(entry)

        then:
        el.getName() == name
        el.getAttributes() == attributes
        el.isEmptyElement() == emptyElement
        el.repeats() == repeats

        where:
        entry                                        || name              | attributes                      | emptyElement | repeats
        'beginnt'                                    || 'beginnt'         | [:]                             | false        | false
        'gco:Record'                                 || 'gco:Record'      | [:]                             | false        | false
        'gco:Record[xsi:type=gml:doubleList]'        || 'gco:Record'      | ['xsi:type': 'gml:doubleList']  | false        | false
        'gmd:valueUnit[xlink:href=urn:adv:uom:m]/'   || 'gmd:valueUnit'   | ['xlink:href': 'urn:adv:uom:m'] | true         | false
        "gmd:valueUnit[xlink:href='urn:adv:uom:m']/" || 'gmd:valueUnit'   | ['xlink:href': 'urn:adv:uom:m'] | true         | false
        'el[a=1][b=two words]'                       || 'el'              | ['a': '1', 'b': 'two words']    | false        | false
        'el/'                                        || 'el'              | [:]                             | true         | false
        '*gmd:processStep'                           || 'gmd:processStep' | [:]                             | false        | true
        '*el[a=1]'                                   || 'el'              | ['a': '1']                      | false        | true
    }

    def 'the repetition marker is part of a segments identity'() {
        expect: 'sharing a wrapper compares segments, and a repeated one is not the same element'
        XmlPathElement.parse('*gmd:processStep') != XmlPathElement.parse('gmd:processStep')
    }

    @Unroll
    def 'rejects "#entry"'() {
        when:
        XmlPathElement.parse(entry)

        then:
        thrown(IllegalArgumentException)

        where:
        entry << ['', '/', '[a=b]', 'el[a=b', 'el[a]', 'el[=b]', 'el[a=b]x', 'el[a=b][a=c]', 'el name']
    }

    @Unroll
    def 'toString round-trips "#entry"'() {
        expect:
        XmlPathElement.parse(entry).toString() == entry

        where:
        entry << ['beginnt', 'gco:Record[xsi:type=gml:doubleList]', 'gmd:valueUnit[xlink:href=urn:adv:uom:m]/', '*gmd:processStep']
    }
}
