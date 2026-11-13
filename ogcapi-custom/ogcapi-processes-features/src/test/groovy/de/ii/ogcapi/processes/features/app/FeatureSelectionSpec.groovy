/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.features.app

import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter
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

    OgcApiQueryParameter parameter(String name) {
        OgcApiQueryParameter param = Stub(OgcApiQueryParameter)
        param.getName() >> name
        param.isInternal() >> false
        param.validate(_, _, _) >> Optional.empty()
        return param
    }

    def "the message on an unknown parameter lists only the parameters that select features"() {
        given:
        def definitions = ["access_token", "bbox", "pretty", "debug", "pipeline-skip", "limit", "status"].collect { parameter(it) }

        when:
        def errors = FeatureSelection.validate(null, Optional.of("pand"), definitions, [pandStatus: "x"])

        then:
        errors == ["The following parameters are rejected: pandStatus. Valid parameters are: bbox, status."]
    }

    def "an access token is rejected in a selection"() {
        given:
        def definitions = ["access_token", "bbox", "status"].collect { parameter(it) }

        when:
        def errors = FeatureSelection.validate(null, Optional.of("pand"), definitions, [access_token: "secret", status: "x"])

        then:
        errors == ["The following parameters are rejected: access_token. Valid parameters are: bbox, status."]
    }

    def "the parameters that only shape or debug a response are dropped"() {
        expect:
        FeatureSelection.parameters([pretty: true, debug: true, "pipeline-skip": "x", status: "y"]) == [status: "y"]
    }
}
