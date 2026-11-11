/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app

import static de.ii.ogcapi.processes.arazzo.app.ArazzoFixtures.document
import static de.ii.ogcapi.processes.arazzo.app.ArazzoFixtures.workflow

import spock.lang.Specification

class ArazzoCheckerSpec extends Specification {

    def "a description within the subset has no problems"() {
        given:
        def doc = document([workflow("w", [
                [stepId: "a", operationId: "pand.getItems", parameters: [[name: "limit", in: "query", value: 1]],
                 successCriteria: [[condition: '$statusCode == 200'], [context: '$statusCode', condition: '^2', type: "regex"]],
                 onFailure: [[name: "again", type: "retry", retryAfter: 1, retryLimit: 2], [reference: '$components.failureActions.stop']],
                 outputs: [id: '$response.body#/features/0/id']],
                [stepId: "b", operationPath: '{$sourceDescriptions.bag.url}#/paths/~1collections~1pand~1items~1{featureId}/get',
                 onSuccess: [[name: "back", type: "goto", stepId: "a", criteria: [[condition: '$statusCode == 304']]]]],
        ], [outputs: [id: '$steps.a.outputs.id']])],
                [components: [failureActions: [stop: [name: "stop", type: "end"]]]])

        expect:
        ArazzoChecker.problems(doc) == []
    }

    def "members outside the subset are problems"() {
        when:
        def problems = ArazzoChecker.problems(document([workflow("w", [step])]))

        then:
        problems.any { it.contains(fragment) }

        where:
        step                                                                                                   || fragment
        [stepId: "a", operationId: "x", successCriteria: [[context: '$response.body', condition: '$.a', type: "jsonpath"]]] || "only 'simple' and 'regex'"
        [stepId: "a", operationId: "x", successCriteria: [[condition: '$a ==', type: [type: "simple", version: "1"]]]]      || "is invalid"
        [stepId: "a", channelPath: "c", action: "send"]                                                        || "exactly one of"
        [stepId: "a", operationId: "x", timeout: 1000]                                                          || "timeout"
        [stepId: "a", operationId: "x", dependsOn: ["b"]]                                                       || "dependsOn"
        [stepId: "a", operationId: "x", parameters: [[name: "q", in: "querystring", value: 1]]]                 || "querystring"
        [stepId: "a", operationId: "x", parameters: [[name: "q", in: "query", value: [context: '$response.body', selector: '$.a', type: "jsonpath"]]]] || "selector object"
        [stepId: "a", operationId: "x", onSuccess: [[name: "g", type: "goto", stepId: "nope"]]]                 || "unknown step 'nope'"
        [stepId: "a", operationId: "x", onSuccess: [[name: "r", type: "retry"]]]                                || "type 'retry'"
        [stepId: "a", operationId: "x", outputs: [o: [context: '$response.body', selector: '$.a', type: "jsonpath"]]] || "selector objects"
        [stepId: "a.b", operationId: "x"]                                                                       || "must only contain"
    }

    def "unsupported source types and versions are problems"() {
        when:
        def problems = ArazzoChecker.problems(document([workflow("w", [[stepId: "a", operationId: "x"]])],
                [arazzo: "2.0.0", sourceDescriptions: [[name: "events", url: "events.yaml", type: "asyncapi"]]]))

        then:
        problems.size() == 2
        problems.any { it.contains("2.0.0") }
        problems.any { it.contains("asyncapi") }
    }
}
