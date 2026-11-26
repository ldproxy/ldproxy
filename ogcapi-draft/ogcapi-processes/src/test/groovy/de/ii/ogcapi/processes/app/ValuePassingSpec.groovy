/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app

import com.fasterxml.jackson.databind.ObjectMapper
import de.ii.ogcapi.processes.domain.model.OgcInputDescription.Passing
import spock.lang.Specification

class ValuePassingSpec extends Specification {

    ObjectMapper mapper = new ObjectMapper()

    def "a value passing is encoded as in the specification"() {
        expect:
        mapper.writeValueAsString(Passing.BY_VALUE) == '"byValue"'
    }

    def "a value passing is read from the specification value and from the enum name: #json"() {
        expect:
        mapper.readValue(json, Passing) == Passing.BY_VALUE

        where:
        json << ['"byValue"', '"BY_VALUE"', '"by_value"']
    }
}
