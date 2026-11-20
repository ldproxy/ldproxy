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
                [key: ['$collection': "wegdeel", status: "a"], count: 2L],
                [key: ['$collection': "pand", status: "a"], count: 1L]]
    }

    def "the collection is a group key at the given position, also for a single collection"() {
        given:
        def aggregator = aggregator([groupBy: ["status", Aggregation.COLLECTION]], multiCollection)

        when:
        aggregator.add(feature([status: ["a"]], "pand"), [:])
        aggregator.add(feature([status: ["b"]], "pand"), [:])
        aggregator.add(feature([status: ["a"]], "pand"), [:])

        then:
        aggregator.result().groups == [
                [key: [status: "a", '$collection': "pand"], count: 2L],
                [key: [status: "b", '$collection': "pand"], count: 1L]]

        where:
        multiCollection << [true, false]
    }

    def "the collection needs neither the geometry nor a property of the features"() {
        when:
        def aggregation = Aggregation.of([groupBy: [Aggregation.COLLECTION]])

        then:
        !aggregation.needsGeometry()
        aggregation.groupsByCollection()
        Aggregation.isVirtual(Aggregation.COLLECTION)
    }

    def "the collection takes no bucket and can only be a group key"() {
        when:
        Aggregation.of(inputs)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(message)

        where:
        inputs                                                                     | message
        [groupBy: [[property: Aggregation.COLLECTION, bucket: 10]]]                | "takes no bucket"
        [groupBy: [Aggregation.COLLECTION, Aggregation.COLLECTION]]                | "twice"
        [aggregates: [[function: "count", property: Aggregation.COLLECTION]]]      | "can only be a group key"
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

    def "a weighted average weights the values of each feature, per group and in the total"() {
        given:
        def aggregator = aggregator([groupBy: ["g"], aggregates: [
                [function: "avg", property: "p", weight: "n"],
                [function: "avg", property: "p"]]])

        when:
        aggregator.add(feature([g: ["a"], p: [100L], n: [300L]]), [:])
        aggregator.add(feature([g: ["a"], p: [0L], n: [100L]]), [:])
        aggregator.add(feature([g: ["b"], p: [50L], n: [10L]]), [:])
        // left out of the weighted average: no weight, several weights, no number
        aggregator.add(feature([g: ["b"], p: [0L]]), [:])
        aggregator.add(feature([g: ["b"], p: [0L], n: [1L, 2L]]), [:])
        aggregator.add(feature([g: ["b"], p: [0L], n: ["x"]]), [:])
        def result = aggregator.result()

        then:
        result.groups == [
                [key: [g: "a"], "avg(p, weight=n)": 75.0d, "avg(p)": 50.0d],
                [key: [g: "b"], "avg(p, weight=n)": 50.0d, "avg(p)": 12.5d]]
        result.total == ["avg(p, weight=n)": 74.390d, "avg(p)": 25.0d]
    }

    def "a weighted average without a weight sum is null"() {
        given:
        def aggregator = aggregator([aggregates: [[function: "avg", property: "p", weight: "n"]]])

        when:
        aggregator.add(feature([p: [10L], n: [0L]]), [:])
        aggregator.add(feature([p: [20L]]), [:])

        then:
        aggregator.result().total == ["avg(p, weight=n)": null]
    }

    def "a weighted average can be weighted by the area"() {
        given:
        def aggregator = aggregator([aggregates: [[function: "avg", property: "p", weight: Aggregation.AREA]]])

        when:
        aggregator.add(feature([p: [1L]]), [(Aggregation.AREA): 3.0d])
        aggregator.add(feature([p: [5L]]), [(Aggregation.AREA): 1.0d])

        then:
        aggregator.result().total == ['avg(p, weight=$area)': 2.0d]
        Aggregation.of([aggregates: [[function: "avg", property: "p", weight: Aggregation.AREA]]]).needsMeasures()
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
        [aggregates: [[function: "sum", property: "p", weight: "n"]]]          || "Only the aggregate 'avg' takes a weight"
        [aggregates: [[function: "avg", property: "p", weight: 1]]]            || "must be the name of a property"
        [aggregates: [[function: "avg", property: "p", weight: '$collection']]] || "can only be a group key"
    }

    def "the properties of the groups and aggregates"() {
        when:
        def aggregation = Aggregation.of([groupBy: ["a", [property: "b", bucket: 10]], aggregates: [[function: "sum", property: Aggregation.AREA], [function: "max", property: "a"]]])

        then:
        aggregation.properties() == ["a", "b", Aggregation.AREA]
        aggregation.needsGeometry()
    }

    def "the weight is one of the properties"() {
        expect:
        Aggregation.of([aggregates: [[function: "avg", property: "p", weight: "n"]]]).properties() == ["p", "n"]
    }

    def "the grid cell is a group key, a feature without a geometry is in the group null"() {
        given:
        def aggregator = aggregator([groupBy: [[property: Aggregation.GEOMETRY, bucket: 250]]])

        when:
        aggregator.add(feature([:]), [(Aggregation.GEOMETRY): [5.1d, 52.1d]])
        aggregator.add(feature([:]), [(Aggregation.GEOMETRY): [5.1d, 52.1d]])
        aggregator.add(feature([:]), [(Aggregation.GEOMETRY): [5.2d, 52.1d]])
        aggregator.add(feature([:]), [:])

        then:
        aggregator.result().groups == [
            [key: [(Aggregation.GEOMETRY): [5.1d, 52.1d]], count: 2L],
            [key: [(Aggregation.GEOMETRY): [5.2d, 52.1d]], count: 1L],
            [key: [(Aggregation.GEOMETRY): null], count: 1L]]
    }

    def "the grid cell needs a width and can only be a group key"() {
        when:
        Aggregation.of(inputs)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(message)

        where:
        inputs                                                                     | message
        [groupBy: [Aggregation.GEOMETRY]]                                          | "needs a bucket"
        [groupBy: [[property: Aggregation.GEOMETRY, bucket: "year"]]]              | "needs a bucket"
        [groupBy: [[property: Aggregation.GEOMETRY, bucket: 100], [property: Aggregation.GEOMETRY, bucket: 200]]] | "twice"
        [aggregates: [[function: "count", property: Aggregation.GEOMETRY]]]        | "can only be a group key"
    }

    def "the grid cell needs the geometry, but neither area nor length"() {
        when:
        def aggregation = Aggregation.of([groupBy: [[property: Aggregation.GEOMETRY, bucket: 100]]])

        then:
        aggregation.needsGeometry()
        !aggregation.needsMeasures()
        aggregation.cellWidth() == Optional.of(100d)
    }
}
