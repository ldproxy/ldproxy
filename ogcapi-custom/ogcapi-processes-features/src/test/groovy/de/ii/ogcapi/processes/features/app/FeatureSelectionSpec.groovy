/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.features.app

import spock.lang.Specification

class FeatureSelectionSpec extends Specification {

    def "parameter values are written as in a query string, the ignored parameters are dropped, those that shape the returned features are kept"() {
        expect:
        FeatureSelection.parameters([status: "Pand gesloopt", huisnummer: 1, bbox: [5.0, 52.0, 5.1, 52.1], limit: 10, f: "json", crs: "x", "Exclude-Properties": "", properties: ["a", "b"], profile: "rel-as-key-title", leeg: null]) ==
                [status: "Pand gesloopt", huisnummer: "1", bbox: "5.0,52.0,5.1,52.1", "Exclude-Properties": "", properties: "a,b", profile: "rel-as-key-title"]
    }

    def "object values are rejected"() {
        when:
        FeatureSelection.parameters([filter: [op: "="]])

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("filter")
    }

    def "the nearest features are sorted by distance"() {
        given:
        def queue = new PriorityQueue<FeatureNearestProcessor.Nearest>(Comparator.comparingDouble({ FeatureNearestProcessor.Nearest n -> n.distance() }).reversed())
        queue.add(new FeatureNearestProcessor.Nearest("c", "b", 5d))
        queue.add(new FeatureNearestProcessor.Nearest("c", "a", 1d))
        queue.add(new FeatureNearestProcessor.Nearest("c", "c", 5d))

        expect:
        FeatureNearestProcessor.sorted(queue)*.featureId() == ["a", "b", "c"]
    }
}
