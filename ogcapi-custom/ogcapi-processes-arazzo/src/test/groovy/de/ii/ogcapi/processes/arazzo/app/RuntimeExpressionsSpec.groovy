/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app

import spock.lang.Specification

class RuntimeExpressionsSpec extends Specification {

    static ExpressionContext context() {
        def request = new StepRequest("bag", "pand.getItems", "GET", "/collections/pand/items", [:], [limit: "2"], ["accept-language": "nl"], Optional.empty())
        def response = new StepResponse(200, ["content-type": "application/geo+json"], Optional.of([numberReturned: 2, features: [[id: "a", properties: [bouwjaar: 1930]], [id: "b"]]]))
        new ExpressionContext(
                [postcode: "3811LM", huisnummer: 1, "x.y": "dotted"],
                [total: 5],
                [adres: [id: "0307200000408911", data: [name: "Hof"]]],
                [inner: [inputs: [a: 1], outputs: [sum: 3]]],
                [bag: "/bag/api"],
                Optional.of(new ExpressionContext.Exchange(request, response)))
    }

    def "expressions evaluate to the values they refer to"() {
        expect:
        RuntimeExpressions.evaluate(expression, context()) == value

        where:
        expression                                  || value
        '$statusCode'                               || 200
        '$method'                                   || "GET"
        '$url'                                      || "/collections/pand/items?limit=2"
        '$request.query.limit'                      || "2"
        '$request.header.Accept-Language'           || "nl"
        '$response.header.Content-Type'             || "application/geo+json"
        '$response.body#/numberReturned'            || 2
        '$response.body#/features/0/id'             || "a"
        '$response.body#/features/5/id'             || null
        '$response.body.numberReturned'             || 2
        '$inputs.postcode'                          || "3811LM"
        '$inputs.x.y'                               || "dotted"
        '$outputs.total'                            || 5
        '$steps.adres.outputs.id'                   || "0307200000408911"
        '$steps.adres.outputs.data#/name'           || "Hof"
        '$steps.adres.outputs.data.name'            || "Hof"
        '$steps.unknown.outputs.id'                 || null
        '$workflows.inner.outputs.sum'              || 3
        '$workflows.inner.inputs.a'                 || 1
        '$sourceDescriptions.bag.url'               || "/bag/api"
    }

    def "unsupported expressions are rejected"() {
        when:
        RuntimeExpressions.evaluate(expression, context())

        then:
        thrown(IllegalArgumentException)

        where:
        expression << ['$message.payload', '$self', '$steps.adres.inputs.id', '$response.query.x']
    }

    def "embedded expressions are replaced, scalars as strings and objects as JSON"() {
        expect:
        RuntimeExpressions.interpolate('{$inputs.postcode} {$inputs.huisnummer}: {$steps.adres.outputs.data}', context()) ==
                '3811LM 1: {"name":"Hof"}'
    }

    def "values are resolved member by member"() {
        when:
        def resolved = RuntimeExpressions.resolve([q: '$inputs.postcode', list: ['$outputs.total', 'literal'], text: 'nr {$inputs.huisnummer}', n: 4], context())

        then:
        resolved == [q: "3811LM", list: [5, "literal"], text: "nr 1", n: 4]
    }

    def "numbers are written without a fraction if they are integral"() {
        expect:
        RuntimeExpressions.stringify(value) == text

        where:
        value       || text
        5.0d        || "5"
        5.25d       || "5.25"
        12L         || "12"
        null        || ""
        [1, 2]      || "[1,2]"
    }
}
