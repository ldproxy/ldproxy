/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification

/**
 * Finding an empty value in the value of an updated property — the one case that does not go through
 * a decoder. A request body is checked while it is decoded, so what counts as empty is defined and
 * exercised in {@code FeatureEventHandlerEmptyValues}.
 */
class EmptyValuesSpec extends Specification {

    private static final ObjectMapper MAPPER = new ObjectMapper()

    private Optional<String> firstEmptyValue(String json) {
        return EmptyValues.firstEmptyValue(MAPPER.readTree(json))
    }

    def 'an empty string is an empty value'() {
        expect:
        firstEmptyValue('{"name":""}').get() == 'name'
    }

    def 'a string with only whitespace is an empty value'() {
        expect:
        firstEmptyValue('{"name":"  \\t\\n "}').get() == 'name'
    }

    def 'the path of a nested empty value joins the member names'() {
        expect:
        firstEmptyValue('{"properties":{"lifetime":{"end":""}}}').get() == 'properties.lifetime.end'
    }

    def 'the path of an empty value in an array carries its index'() {
        expect:
        firstEmptyValue('{"tags":["a","","c"]}').get() == 'tags[1]'
    }

    def 'the first empty value wins'() {
        expect:
        firstEmptyValue('{"a":"x","b":"","c":""}').get() == 'b'
    }

    def 'a scalar at the root has no path of its own'() {
        expect:
        firstEmptyValue('""').get() == '.'
    }

    def 'a body without empty values passes'() {
        expect:
        firstEmptyValue('''{
            "type": "Feature",
            "id": "1",
            "geometry": {"type": "Point", "coordinates": [7.1, 50.7]},
            "properties": {"name": "Bonn", "count": 0, "flag": false, "note": null, "tags": []}
        }''').isEmpty()
    }

    def 'only strings can be empty'() {
        expect:
        firstEmptyValue('{"count":0,"flag":false,"note":null,"nested":{},"list":[]}').isEmpty()
    }

    def 'join appends a sub-path, an index without a separator'() {
        expect:
        EmptyValues.join('tags', '[1]') == 'tags[1]'
        EmptyValues.join('addresses', '[1].street') == 'addresses[1].street'
        EmptyValues.join('lifetime', 'end') == 'lifetime.end'
        EmptyValues.join('name', '.') == 'name'
    }

}
