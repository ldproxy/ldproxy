/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app

import de.ii.ogcapi.processes.arazzo.domain.ImmutableArazzoCriterion
import spock.lang.Specification

class SelectorsSpec extends Specification {

    static final Map BODY = [
            _embedded: [documenten: [
                    [type: "Omgevingsplan", uriIdentificatie: "/akn/gm0772/plan", ontwerp: false],
                    [type: "bestemmingsplan", uriIdentificatie: "NL.IMRO.1", ontwerp: false],
                    [type: "bestemmingsplan", uriIdentificatie: "NL.IMRO.2", ontwerp: false],
            ]],
            page     : [totalElements: 3],
    ]

    static ExpressionContext context(Map inputs = [:]) {
        def request = new StepRequest("x", "op", "GET", "/p", [:], [:], [:], Optional.empty(), Optional.empty())
        def response = new StepResponse(200, [:], Optional.of(BODY))
        return new ExpressionContext(inputs, [:], [:], [:], [:], Optional.empty())
                .withExchange(new ExpressionContext.Exchange(request, response))
    }

    def "a JSONPath selector with one node is its value, with several nodes a list, with none no value"() {
        expect:
        Selectors.select([context: '$response.body', selector: selector, type: "jsonpath"], context()) == expected

        where:
        selector                                                           | expected
        '$._embedded.documenten[?@.type == \'Omgevingsplan\'].uriIdentificatie' | "/akn/gm0772/plan"
        '$._embedded.documenten[?@.type == \'bestemmingsplan\'].uriIdentificatie' | ["NL.IMRO.1", "NL.IMRO.2"]
        '$._embedded.documenten[?@.type == \'structuurvisie\'].uriIdentificatie' | null
        '$._embedded.documenten[?search(@.type, \'[Pp]lan\')].type'        | ["Omgevingsplan", "bestemmingsplan", "bestemmingsplan"]
    }

    def "a selector embeds runtime expressions, other selector types are supported"() {
        expect:
        Selectors.select([context: '$response.body', selector: '$._embedded.documenten[?@.uriIdentificatie == \'{$inputs.id}\'].type', type: "jsonpath"],
                context([id: "NL.IMRO.2"])) == "bestemmingsplan"
        Selectors.select([context: '$response.body', selector: '/page/totalElements', type: [type: "jsonpointer"]], context()) == 3
    }

    def "a selector object is recognized with extensions, but not with other members"() {
        expect:
        Selectors.isSelector(value) == expected

        where:
        value                                                                 | expected
        [context: '$response.body', selector: '$.a', type: "jsonpath"]        | true
        [context: '$response.body', selector: '$.a', type: "jsonpath", "x-dso-uniek": true] | true
        [context: '$response.body', selector: '$.a', type: "jsonpath", other: 1] | false
        [context: '$response.body', type: "jsonpath"]                         | false
    }

    def "a JSONPath criterion with a filter at the root tests an object context itself"() {
        expect:
        Criteria.test(new ImmutableArazzoCriterion.Builder().context('$response.body').condition(condition).type("jsonpath").build(), context([id: "NL.IMRO.2"])) == expected

        where:
        condition                                                                  | expected
        '$[?length(@._embedded.documenten) == 3]'                                  | true
        '$[?length(@._embedded.documenten) == 1]'                                  | false
        // count() counts the selected nodes, one array
        '$[?count(@._embedded.documenten) == 1]'                                   | true
        '$._embedded.documenten[?@.uriIdentificatie == \'NL.IMRO.1\']'              | true
        '$[?count(@._embedded.documenten[?@.uriIdentificatie == \'{$inputs.id}\']) == 0]' | false
        '$.missing'                                                                | false
    }

    def "an XPath selector and criterion work on XML content"() {
        given:
        String xml = '<dmn xmlns="https://www.omg.org/spec/DMN"><vraag><vraagTekst>Hoe hoog?</vraagTekst></vraag><vraag><vraagTekst>Waar?</vraagTekst></vraag></dmn>'
        def request = new StepRequest("x", "op", "GET", "/p", [:], [:], [:], Optional.empty(), Optional.empty())
        def ctx = new ExpressionContext([:], [:], [:], [:], [:], Optional.empty())
                .withExchange(new ExpressionContext.Exchange(request, new StepResponse(200, [:], Optional.of(xml))))

        expect:
        Selectors.select([context: '$response.body', selector: "//*[local-name()='vraagTekst']/text()", type: "xpath"], ctx) == ["Hoe hoog?", "Waar?"]
        Criteria.test(new ImmutableArazzoCriterion.Builder().context('$response.body').condition("count(//*[local-name()='vraag']) = 2").type("xpath").build(), ctx)
        !Criteria.test(new ImmutableArazzoCriterion.Builder().context('$response.body').condition("//*[local-name()='antwoord']").type("xpath").build(), ctx)
    }

    def "an XML document with a DTD is refused"() {
        when:
        Selectors.matchesXPath('<?xml version="1.0"?><!DOCTYPE a [<!ENTITY e SYSTEM "file:///etc/passwd">]><a>&e;</a>', "/a")

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("not XML")
    }

    def "invalid expressions are reported"() {
        expect:
        Selectors.syntaxProblem(type, expression) != null

        where:
        type          | expression
        "jsonpath"    | '$[?(@.a ==]'
        "jsonpath"    | '$..[?@.a == \'x\''
        "xpath"       | "//*["
        "jsonpointer" | "no-pointer"
    }
}
