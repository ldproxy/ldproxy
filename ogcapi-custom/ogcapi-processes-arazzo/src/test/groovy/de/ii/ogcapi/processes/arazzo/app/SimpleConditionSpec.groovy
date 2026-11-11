/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app

import spock.lang.Specification

class SimpleConditionSpec extends Specification {

    static final Map<String, Object> VALUES = [
            '$statusCode'           : 200,
            '$response.body'        : [data: [items: ["a", "b"]], count: "12"],
            '$response.body#/count' : "12",
            '$inputs.status'        : "Pand in gebruik",
            '$inputs.none'          : null,
            '$inputs.flag'          : true,
    ]

    def "conditions are evaluated"() {
        expect:
        SimpleCondition.parse(condition).test({ String e -> VALUES[e] }) == result

        where:
        condition                                                || result
        '$statusCode == 200'                                     || true
        '$statusCode != 200'                                     || false
        '$statusCode >= 200 && $statusCode < 300'                || true
        '$statusCode == 404 || $statusCode == 200'               || true
        "\$inputs.status == 'PAND IN GEBRUIK'"                  || true
        "\$inputs.status != 'pand gesloopt'"                    || true
        '$response.body#/count > 9'                              || true
        '$response.body#/count == 12'                            || true
        '$inputs.none == null'                                   || true
        '$inputs.none != null'                                   || false
        '$inputs.none < 3'                                       || false
        '$inputs.none'                                           || false
        '$inputs.flag'                                           || true
        '!$inputs.flag'                                          || false
        '!($statusCode == 404)'                                  || true
        '$statusCode == 404 && false || true'                    || true
        '$statusCode == 404 && (false || true)'                  || false
        '($response.body).data.items[1] == \'B\''              || true
        '($response.body).data.items[7] == null'                 || true
        "'it''s' == 'IT''S'"                                     || true
        '-1 < 0'                                                 || true
    }

    def "invalid conditions are rejected"() {
        when:
        SimpleCondition.parse(condition)

        then:
        thrown(IllegalArgumentException)

        where:
        condition << ['$statusCode ==', '($statusCode == 200', "'open", '$statusCode = 200', '$a == 1 1']
    }
}
