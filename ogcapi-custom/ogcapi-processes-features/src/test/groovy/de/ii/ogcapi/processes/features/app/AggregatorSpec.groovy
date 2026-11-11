/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.features.app

import spock.lang.Specification

class AggregatorSpec extends Specification {

    static FeatureRecord feature(Map<String, List<Object>> values, String collection = "pand") {
        new FeatureRecord(collection, "1", values, Optional.empty())
    }

    static Aggregator aggregator(Map<String, Object> inputs, boolean byCollection = false) {
        new Aggregator(Aggregation.of(inputs), byCollection)
    }

    def "counts features per group, the largest group first"() {
        given:
        def aggregator = aggregator([groupBy: ["status"]])

        when:
        aggregator.add(feature([status: ["a"]]), [:])
        aggregator.add(feature([status: ["b"]]), [:])
        aggregator.add(feature([status: ["b"]]), [:])
        def result = aggregator.result()

        then:
        result.groups == [[key: [status: "b"], count: 2L], [key: [status: "a"], count: 1L]]
        result.total == [count: 3L]
        result.featuresScanned == 3L
        result.groupsTruncated == false
    }

    def "a missing value forms the group null"() {
        given:
        def aggregator = aggregator([groupBy: ["status"]])

        when:
        aggregator.add(feature([:]), [:])
        aggregator.add(feature([status: ["a"]]), [:])
        aggregator.add(feature([:]), [:])

        then:
        aggregator.result().groups == [[key: [status: null], count: 2L], [key: [status: "a"], count: 1L]]
    }

    def "a feature with several values is in one group per value, and once in the total"() {
        given:
        def aggregator = aggregator([groupBy: ["gebruiksdoel"]])

        when:
        aggregator.add(feature([gebruiksdoel: ["wonen", "winkel", "wonen"]]), [:])
        aggregator.add(feature([gebruiksdoel: ["wonen"]]), [:])
        def result = aggregator.result()

        then:
        result.groups == [[key: [gebruiksdoel: "wonen"], count: 2L], [key: [gebruiksdoel: "winkel"], count: 1L]]
        result.total == [count: 2L]
    }

    def "groups by several keys"() {
        given:
        def aggregator = aggregator([groupBy: ["a", "b"]])

        when:
        aggregator.add(feature([a: ["x"], b: ["1", "2"]]), [:])
        aggregator.add(feature([a: ["x"], b: ["1"]]), [:])
        def result = aggregator.result()

        then:
        result.groups == [[key: [a: "x", b: "1"], count: 2L], [key: [a: "x", b: "2"], count: 1L]]
    }

    def "the collection is the first key, if several collections are selected"() {
        given:
        def aggregator = aggregator([groupBy: ["status"]], true)

        when:
        aggregator.add(feature([status: ["a"]], "pand"), [:])
        aggregator.add(feature([status: ["a"]], "wegdeel"), [:])
        aggregator.add(feature([status: ["a"]], "wegdeel"), [:])

        then:
        aggregator.result().groups == [
                [key: [objecttype: "wegdeel", status: "a"], count: 2L],
                [key: [objecttype: "pand", status: "a"], count: 1L]]
    }

    def "computes sum, min, max, avg and median"() {
        given:
        def aggregator = aggregator([aggregates: [
                "count",
                [function: "sum", property: "x"],
                [function: "min", property: "x"],
                [function: "max", property: "x"],
                [function: "avg", property: "x"],
                [function: "median", property: "x"],
                [function: "count", property: "x"]]])

        when:
        [1L, 2L, 3L, 10L].each { aggregator.add(feature([x: [it]]), [:]) }
        aggregator.add(feature([:]), [:])

        then:
        aggregator.result().total == [
                "count"    : 5L,
                "sum(x)"   : 16L,
                "min(x)"   : 1L,
                "max(x)"   : 10L,
                "avg(x)"   : 4.0d,
                "median(x)": 2.5d,
                "count(x)" : 4L]
    }

    def "sums of decimal values are rounded, aggregates without values are null"() {
        given:
        def aggregator = aggregator([aggregates: [[function: "sum", property: "x"], [function: "avg", property: "y"]]])

        when:
        aggregator.add(feature([x: [0.1d]]), [:])
        aggregator.add(feature([x: [0.2004d]]), [:])

        then:
        aggregator.result().total == ["sum(x)": 0.300d, "avg(y)": null]
    }

    def "min and max of dates compare the text"() {
        given:
        def aggregator = aggregator([aggregates: [[function: "min", property: "d"], [function: "max", property: "d"]]])

        when:
        ["2016-03-01", "1999-12-31", "2020-01-01"].each { aggregator.add(feature([d: [it]]), [:]) }

        then:
        aggregator.result().total == ["min(d)": "1999-12-31", "max(d)": "2020-01-01"]
    }

    def "aggregates virtual properties"() {
        given:
        def aggregator = aggregator([groupBy: ["t"], aggregates: [[function: "sum", property: Aggregation.AREA]]])

        when:
        aggregator.add(feature([t: ["a"]]), [(Aggregation.AREA): 10.5d])
        aggregator.add(feature([t: ["a"]]), [(Aggregation.AREA): 2.25d])

        then:
        aggregator.result().groups == [[key: [t: "a"], 'sum($area)': 12.75d]]
    }

    def "buckets of years, months and numeric classes"() {
        expect:
        Aggregator.bucket(new Aggregation.GroupKey("p", Optional.of(bucket), width), value) == expected

        where:
        bucket                     | width | value                  || expected
        Aggregation.Bucket.YEAR    | 0     | "2016-03-01T10:00:00Z" || "2016"
        Aggregation.Bucket.MONTH   | 0     | "2016-03-01"           || "2016-03"
        Aggregation.Bucket.WIDTH   | 10    | 1987L                  || 1980L
        Aggregation.Bucket.WIDTH   | 10    | "1987"                 || 1980L
        Aggregation.Bucket.WIDTH   | 0.5   | 1.7d                   || 1.5d
        Aggregation.Bucket.WIDTH   | 10    | "abc"                  || "abc"
    }

    def "the limit cuts the groups"() {
        given:
        def aggregator = aggregator([groupBy: ["s"], limit: 1])

        when:
        aggregator.add(feature([s: ["a"]]), [:])
        aggregator.add(feature([s: ["b"]]), [:])
        aggregator.add(feature([s: ["b"]]), [:])
        def result = aggregator.result()

        then:
        result.groups == [[key: [s: "b"], count: 2L]]
        result.groupsTruncated == true
        result.total == [count: 3L]
    }

    def "invalid inputs are rejected"() {
        when:
        Aggregation.of(inputs)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains(message)

        where:
        inputs                                                  || message
        [groupBy: "status"]                                     || "must be an array"
        [groupBy: [[property: "d", bucket: "week"]]]            || "bucket"
        [aggregates: ["total"]]                                 || "Unknown aggregate function 'total'"
        [aggregates: [[function: "sum"]]]                       || "needs a property"
        [aggregates: ["count", "count"]]                        || "twice"
        [limit: 0]                                              || "positive integer"
    }

    def "the properties of the groups and aggregates"() {
        when:
        def aggregation = Aggregation.of([groupBy: ["a", [property: "b", bucket: 10]], aggregates: [[function: "sum", property: Aggregation.AREA], [function: "max", property: "a"]]])

        then:
        aggregation.properties() == ["a", "b", Aggregation.AREA]
        aggregation.needsGeometry()
    }
}
