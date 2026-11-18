/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import spock.lang.Specification

class EndpointMcpCorsSpec extends Specification {

    def 'a request of a web page may read the response'() {
        given:
        HttpServletRequest request = Stub()
        request.getHeader("Origin") >> origin
        request.getHeader("Sec-Fetch-Mode") >> mode
        HttpServletResponse response = Mock()

        when:
        EndpointMcp.allowCrossOrigin(request, response)

        then:
        allowed * response.setHeader("Access-Control-Allow-Origin", "*")

        where:
        origin                 | mode    | allowed
        "https://app.example"  | null    | 1
        null                   | "cors"  | 1
        ""                     | "CORS"  | 1
        null                   | null    | 0
        ""                     | "no-cors" | 0
    }
}
