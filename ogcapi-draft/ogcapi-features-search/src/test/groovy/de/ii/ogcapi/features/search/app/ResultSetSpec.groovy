/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app

import de.ii.ogcapi.features.search.domain.QueryExpression
import de.ii.xtraplatform.cql.domain.Eq
import de.ii.xtraplatform.cql.domain.InResultSet
import de.ii.xtraplatform.cql.domain.Property
import de.ii.xtraplatform.cql.domain.ScalarLiteral
import jakarta.ws.rs.BadRequestException
import spock.lang.Specification

class ResultSetSpec extends Specification {

    def 'query expression with result sets'() {
        given:
        String json = """
        {
            "title": "Flurstücke mit Präsentationsobjekten",
            "queries": [
                {
                    "collections": [ "ax_flurstueck" ],
                    "filter": { "op": "=", "args": [ { "property": "flstkennz" }, "01234001600099______" ] },
                    "resultSets": { "flst": {} }
                },
                {
                    "collections": [ "ap_pto" ],
                    "filter": { "op": "inResultSet", "args": [ { "property": "dientZurDarstellungVon" }, "flst" ] }
                }
            ]
        }
        """

        when:
        QueryExpression query = QueryExpression.of(new ByteArrayInputStream(json.getBytes("UTF-8")))

        then:
        query.getQueries().size() == 2
        query.getQueries().get(0).getAllResultSets().keySet() == ["flst"] as Set
        query.getQueries().get(0).getAllResultSets().get("flst").getValues().isEmpty()
        query.getQueries().get(1).getFilter().get() == InResultSet.of("dientZurDarstellungVon", "flst")
    }

    def 'the resultSet shorthand is equivalent to an id result set'() {
        given:
        String json = """
        {
            "queries": [
                {
                    "collections": [ "ax_flurstueck" ],
                    "resultSet": "flst"
                },
                {
                    "collections": [ "ap_pto" ],
                    "filter": { "op": "inResultSet", "args": [ { "property": "dientZurDarstellungVon" }, "flst" ] }
                }
            ]
        }
        """

        when:
        QueryExpression query = QueryExpression.of(new ByteArrayInputStream(json.getBytes("UTF-8")))

        then:
        query.getQueries().get(0).getAllResultSets().keySet() == ["flst"] as Set
        query.getQueries().get(0).getAllResultSets().get("flst").getValues().isEmpty()
    }

    def 'projected result sets and resultSetOnly'() {
        given:
        String json = """
        {
            "queries": [
                {
                    "collections": [ "ax_flurstueck" ],
                    "resultSets": {
                        "flst": {},
                        "flst_bs": { "values": "istGebucht" }
                    },
                    "resultSetOnly": true
                },
                {
                    "collections": [ "ax_buchungsstelle" ],
                    "filter": { "op": "inResultSet", "args": [ { "property": "id" }, "flst_bs" ] }
                }
            ]
        }
        """

        when:
        QueryExpression query = QueryExpression.of(new ByteArrayInputStream(json.getBytes("UTF-8")))

        then:
        query.getQueries().get(0).getResultSetOnly()
        query.getQueries().get(0).getAllResultSets().get("flst").getValues().isEmpty()
        query.getQueries().get(0).getAllResultSets().get("flst_bs").getValues() == Optional.of("istGebucht")
        !query.getQueries().get(1).getResultSetOnly()
    }

    def 'the resolver attaches the producer context'() {
        given:
        def producerFilter = Eq.of(Property.of("flstkennz"), ScalarLiteral.of("01234001600099______"))
        def resultSets = [
                "flst": new ResultSetResolver.ResolvedResultSet("ax_flurstueck", Optional.of(producerFilter), Optional.empty())
        ]
        def filter = InResultSet.of("dientZurDarstellungVon", "flst")

        when:
        def resolved = (InResultSet) filter.accept(new ResultSetResolver(resultSets))

        then:
        resolved.getProducerType() == Optional.of("ax_flurstueck")
        resolved.getProducerFilter() == Optional.of(producerFilter)
        resolved.getProducerValues().isEmpty()
        resolved.getArgs() == filter.getArgs()
    }

    def 'a reference to an undefined result set is rejected'() {
        given:
        def filter = InResultSet.of("dientZurDarstellungVon", "unknown")

        when:
        filter.accept(new ResultSetResolver([:]))

        then:
        def e = thrown BadRequestException
        e.message.contains("unknown")
    }

    def 'other filters pass through the resolver unchanged'() {
        given:
        def filter = Eq.of(Property.of("flstkennz"), ScalarLiteral.of("foo"))

        when:
        def resolved = filter.accept(new ResultSetResolver([:]))

        then:
        resolved == filter
    }
}
