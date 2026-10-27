/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.infra.rest

import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.core.MultivaluedHashMap
import spock.lang.Specification

class NulCharacterSpec extends Specification {

    def 'a parameter with the character U+0000 is rejected'() {
        given:
        def parameters = new MultivaluedHashMap<String, String>()
        parameters.add(name, value)

        when:
        ApiRequestDispatcher.rejectNulCharacters(parameters)

        then:
        def e = thrown(BadRequestException)
        e.message.contains("U+0000")

        where:
        name       | value
        "qid"      | "a\u0000b"
        "ids"      | "\u0000"
        "x\u0000y" | "a"
    }

    def 'other parameters are accepted'() {
        given:
        def parameters = new MultivaluedHashMap<String, String>()
        parameters.add("qid", "a\u0001b")
        parameters.add("filter", "name = 'ä'")
        parameters.add("ids", "")

        when:
        ApiRequestDispatcher.rejectNulCharacters(parameters)

        then:
        noExceptionThrown()
    }
}
