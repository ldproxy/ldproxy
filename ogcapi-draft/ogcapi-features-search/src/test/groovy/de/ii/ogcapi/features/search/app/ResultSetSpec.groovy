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
import de.ii.xtraplatform.cql.domain.InResultSetByKey
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

    def 'supportPaging is unset by default and parsed when present'() {
        given:
        String byDefault = """{ "queries": [ { "collections": [ "ax_flurstueck" ] } ] }"""
        String paged = """{ "supportPaging": true, "queries": [ { "collections": [ "ax_flurstueck" ] } ] }"""

        expect: 'unset is empty (the handler applies the single-shot default); an explicit value is parsed'
        QueryExpression.of(new ByteArrayInputStream(byDefault.getBytes("UTF-8"))).getSupportPaging().isEmpty()
        QueryExpression.of(new ByteArrayInputStream(paged.getBytes("UTF-8"))).getSupportPaging().get()
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
                "flst": new ResultSetResolver.ResolvedResultSet("ax_flurstueck", Optional.of(producerFilter), Optional.empty(), [:])
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

    def 'a query expression can define a composite-key result set'() {
        given:
        String json = """
        {
            "title": "Gemeinden der ausgewählten Flurstücke",
            "queries": [
                {
                    "collections": [ "ax_flurstueck" ],
                    "resultSets": {
                        "fs_gemeinde": { "key": { "land": "gmd_lan", "kreis": "gmd_krs" } }
                    }
                },
                {
                    "collections": [ "ax_gemeinde" ],
                    "filter": {
                        "op": "inResultSetByKey",
                        "args": [ { "land": { "property": "gkz_lan" }, "kreis": { "property": "gkz_krs" } }, "fs_gemeinde" ]
                    }
                }
            ]
        }
        """

        when:
        QueryExpression query = QueryExpression.of(new ByteArrayInputStream(json.getBytes("UTF-8")))

        then:
        query.getQueries().get(0).getAllResultSets().get("fs_gemeinde").getKey() == [land: "gmd_lan", kreis: "gmd_krs"]
        query.getQueries().get(0).getAllResultSets().get("fs_gemeinde").getValues().isEmpty()
        query.getQueries().get(1).getFilter().get() instanceof InResultSetByKey
    }

    def 'the resolver attaches the producer key'() {
        given:
        def resultSets = [
                "fs_gemeinde": new ResultSetResolver.ResolvedResultSet("ax_flurstueck", Optional.empty(),
                        Optional.empty(), [land: "gmd_lan", kreis: "gmd_krs"])
        ]
        def filter = InResultSetByKey.of([land: Property.of("gkz_lan"), kreis: Property.of("gkz_krs")], "fs_gemeinde")

        when:
        def resolved = (InResultSetByKey) filter.accept(new ResultSetResolver(resultSets))

        then:
        resolved.getProducerType() == Optional.of("ax_flurstueck")
        resolved.getProducerKey() == [land: "gmd_lan", kreis: "gmd_krs"]
        resolved.getKeyNames() == ["kreis", "land"]
        resolved.getArgs() == filter.getArgs()
    }

    def 'key parts that the result set does not define are rejected'() {
        given:
        def resultSets = [
                "fs_gemeinde": new ResultSetResolver.ResolvedResultSet("ax_flurstueck", Optional.empty(),
                        Optional.empty(), [land: "gmd_lan", kreis: "gmd_krs"])
        ]
        def filter = InResultSetByKey.of([land: Property.of("gkz_lan"), gemeinde: Property.of("gkz_gem")], "fs_gemeinde")

        when:
        filter.accept(new ResultSetResolver(resultSets))

        then:
        def e = thrown BadRequestException
        e.message.contains("gemeinde")
        e.message.contains("kreis")
    }

    def 'a value set cannot be consumed as a key set'() {
        given:
        def resultSets = [
                "flst": new ResultSetResolver.ResolvedResultSet("ax_flurstueck", Optional.empty(), Optional.empty(), [:])
        ]
        def filter = InResultSetByKey.of([land: Property.of("gkz_lan")], "flst")

        when:
        filter.accept(new ResultSetResolver(resultSets))

        then:
        def e = thrown BadRequestException
        e.message.contains("set of single values")
    }

    def 'a key set cannot be consumed as a value set'() {
        given:
        def resultSets = [
                "fs_gemeinde": new ResultSetResolver.ResolvedResultSet("ax_flurstueck", Optional.empty(),
                        Optional.empty(), [land: "gmd_lan"])
        ]
        def filter = InResultSet.of("gkz_lan", "fs_gemeinde")

        when:
        filter.accept(new ResultSetResolver(resultSets))

        then:
        def e = thrown BadRequestException
        e.message.contains("set of composite keys")
    }

    def 'a result set is either values or a key, not both'() {
        given:
        String json = """
        {
            "queries": [
                {
                    "collections": [ "ax_flurstueck" ],
                    "resultSets": { "x": { "values": "istGebucht", "key": { "land": "gmd_lan" } } }
                },
                { "collections": [ "ax_gemeinde" ] }
            ]
        }
        """

        when:
        QueryExpression.of(new ByteArrayInputStream(json.getBytes("UTF-8")))

        then:
        thrown Exception
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
