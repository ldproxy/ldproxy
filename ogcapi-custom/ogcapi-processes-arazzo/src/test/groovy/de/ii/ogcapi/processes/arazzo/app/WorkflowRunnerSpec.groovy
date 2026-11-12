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
import static de.ii.ogcapi.processes.arazzo.app.FakeDispatcher.ok

import de.ii.ogcapi.processes.arazzo.domain.ArazzoDocument
import spock.lang.Specification

class WorkflowRunnerSpec extends Specification {

    FakeDispatcher dispatcher = new FakeDispatcher()
    List<Long> pauses = []
    Map<String, ArazzoDocument> stored = [:]
    FakeHttpClient http = new FakeHttpClient()
    List<String> allowedExternalUris = []
    Map<String, String> serverInputs = new LinkedHashMap<>()

    WorkflowRunner runner(int maximumSteps = 100) {
        return new WorkflowRunner(dispatcher, new HttpExternalApis(http.client(), allowedExternalUris), { String id -> Optional.ofNullable(stored[id]) }, "bag", maximumSteps, 1000, { long ms -> pauses.add(ms) } as WorkflowRunner.Pause, serverInputs)
    }

    def "the outputs of a step are the inputs of the next step"() {
        given:
        dispatcher.responses["pand.getItems"] = { StepRequest r, int n -> ok([features: [[id: "p1"]]]) }
        dispatcher.responses["pand.getItem"] = { StepRequest r, int n -> ok([id: r.pathParameters().featureId, properties: [bouwjaar: 1930]]) }
        def doc = document([workflow("w", [
                [stepId: "find", operationId: "pand.getItems", parameters: [[name: "bouwjaar", in: "query", value: '$inputs.jaar'], [name: "limit", in: "query", value: 1]],
                 outputs: [id: '$response.body#/features/0/id']],
                [stepId: "get", operationId: '$sourceDescriptions.bag.pand.getItem', parameters: [[name: "featureId", in: "path", value: '$steps.find.outputs.id']],
                 successCriteria: [[condition: '$response.body#/properties/bouwjaar < 2000']],
                 outputs: [jaar: '$response.body#/properties/bouwjaar', label: 'pand {$steps.find.outputs.id}']],
        ], [inputs: [type: "object", properties: [jaar: [type: "integer"]], required: ["jaar"]],
            outputs: [bouwjaar: '$steps.get.outputs.jaar', label: '$steps.get.outputs.label']])])

        when:
        def result = runner().run(doc, "w", [jaar: 1930])

        then:
        result.outputs() == [bouwjaar: 1930, label: "pand p1"]
        dispatcher.requests*.path() == ["/collections/pand/items", "/collections/pand/items/p1"]
        dispatcher.requests[0].query() == [bouwjaar: "1930", limit: "1"]
        result.steps()*.stepId == ["find", "get"]
        result.steps()*.statusCode == [200, 200]
    }

    def "a missing required input is a problem"() {
        given:
        def doc = document([workflow("w", [[stepId: "a", operationId: "pand.getItems"]], [inputs: [type: "object", required: ["jaar"]]])])

        when:
        runner().run(doc, "w", [:])

        then:
        WorkflowException e = thrown()
        e.kind == WorkflowException.Kind.PROBLEM
        e.message.contains("jaar")
    }

    def "a failed step without failure actions fails the workflow with the detail of the response"() {
        given:
        dispatcher.responses["pand.getItems"] = { StepRequest r, int n -> new StepResponse(400, [:], Optional.of([detail: "Invalid limit"])) }
        def doc = document([workflow("w", [[stepId: "a", operationId: "pand.getItems"]])])

        when:
        runner().run(doc, "w", [:])

        then:
        WorkflowException e = thrown()
        e.kind == WorkflowException.Kind.FAILURE
        e.message.contains("status 400: Invalid limit")
    }

    def "a retry repeats the step up to the limit, the header Retry-After takes precedence"() {
        given:
        dispatcher.responses["pand.getItems"] = { StepRequest r, int n -> n < 3 ? new StepResponse(503, ["retry-after": "2"], Optional.empty()) : ok([features: []]) }
        def doc = document([workflow("w", [[stepId: "a", operationId: "pand.getItems",
                                           onFailure: [[name: "r", type: "retry", retryAfter: 1, retryLimit: limit, criteria: [[condition: '$statusCode == 503']]]]]])])

        when:
        def failed = false
        try {
            runner().run(doc, "w", [:])
        } catch (WorkflowException e) {
            failed = true
        }

        then:
        failed == fails
        dispatcher.requests.size() == calls
        pauses == expectedPauses

        where:
        limit || fails | calls | expectedPauses
        2     || false | 3     | [2000L, 2000L]
        1     || true  | 2     | [2000L]
    }

    static ArazzoDocument loop() {
        return document([workflow("w", [
                [stepId: "a", operationId: "pand.getItems", outputs: [page: '$response.body#/page'],
                 onSuccess: [[name: "done", type: "end", criteria: [[condition: '$response.body#/page >= 3']]],
                             [name: "loop", type: "goto", stepId: "a"]]],
                [stepId: "never", operationId: "pand.getItem"],
        ], [outputs: [page: '$steps.a.outputs.page']])])
    }

    def "goto jumps to a step and end ends the workflow"() {
        given:
        dispatcher.responses["pand.getItems"] = { StepRequest r, int n -> ok([page: n]) }

        when:
        def result = runner().run(loop(), "w", [:])

        then:
        result.outputs() == [page: 3]
        dispatcher.requests*.operationId() == ["pand.getItems"] * 3
    }

    def "an execution fails when it needs more steps than the maximum"() {
        given:
        dispatcher.responses["pand.getItems"] = { StepRequest r, int n -> ok([page: n]) }

        when:
        runner(2).run(loop(), "w", [:])

        then:
        WorkflowException e = thrown()
        e.kind == WorkflowException.Kind.FAILURE
        e.message.contains("more than 2 steps")
    }

    def "a step calls a nested workflow, its outputs are available"() {
        given:
        dispatcher.responses["executeProcess"] = { StepRequest r, int n -> ok([total: [count: r.body().get().inputs.n * 2]]) }
        def doc = document([
                workflow("outer", [[stepId: "call", workflowId: "inner", parameters: [[name: "n", value: '$inputs.n']],
                                    outputs: [count: '$workflows.inner.outputs.count']]],
                        [outputs: [count: '$steps.call.outputs.count']]),
                workflow("inner", [[stepId: "agg", operationId: "executeProcess",
                                    parameters: [[name: "processId", in: "path", value: "aggregate-features"]],
                                    requestBody: [contentType: "application/json", payload: [inputs: [n: 0, source: [collection: "pand"]]],
                                                  replacements: [[target: "/inputs/n", value: '$inputs.n']]],
                                    outputs: [count: '$response.body#/total/count']]],
                        [outputs: [count: '$steps.agg.outputs.count']]),
        ])

        when:
        def result = runner().run(doc, "outer", [n: 21])

        then:
        result.outputs() == [count: 42]
        dispatcher.requests[0].path() == "/processes/aggregate-features/execution"
        dispatcher.requests[0].body().get() == [inputs: [n: 21, source: [collection: "pand"]]]
    }

    def "a workflow of a stored description is called through an arazzo source"() {
        given:
        dispatcher.responses["pand.getItems"] = { StepRequest r, int n -> ok([numberReturned: 7]) }
        stored["shared"] = document([workflow("count", [[stepId: "a", operationId: "pand.getItems", outputs: [n: '$response.body#/numberReturned']]],
                [outputs: [n: '$steps.a.outputs.n']])])
        def doc = document([workflow("w", [[stepId: "s", workflowId: '$sourceDescriptions.shared.count', outputs: [n: '$workflows.count.outputs.n']]],
                [outputs: [n: '$steps.s.outputs.n']])],
                [sourceDescriptions: [[name: "bag", url: "/bag/api"], [name: "shared", url: "shared.yaml", type: "arazzo"]]])

        expect:
        runner().run(doc, "w", [:]).outputs() == [n: 7]
    }

    def "a source that is not an API of this server is rejected, if it is not allowed"() {
        given:
        allowedExternalUris = allowed
        def doc = document([workflow("w", [[stepId: "a", operationId: '$sourceDescriptions.ext.getItems']])],
                [sourceDescriptions: [[name: "ext", url: url]]])

        when:
        runner().run(doc, "w", [:])

        then:
        WorkflowException e = thrown()
        e.kind == WorkflowException.Kind.PROBLEM
        e.message.contains("not an API of this server")
        dispatcher.requests.isEmpty()
        http.sent.isEmpty()

        where:
        url                                    | allowed
        "https://api.example.com/openapi.json" | []
        "https://api.example.com/openapi.json" | ["https://api.example.com/v1/"]
        "/other/api"                           | ["*"]
    }

    def "steps call allowed external APIs over HTTP and APIs of this server in-process"() {
        given:
        allowedExternalUris = ["https://api.example.com/"]
        http.responses["https://api.example.com/openapi.json"] = { String m ->
            FakeHttpClient.response(200, '{"openapi": "3.0.3", "servers": [{"url": "/v1"}], "paths": {"/codes/{code}": {"get": {"operationId": "getCode"}}}}') }
        http.responses["https://api.example.com/v1/codes/p%201"] = { String m ->
            FakeHttpClient.response(200, '{"label": "Pand 1"}', ["content-type": "application/json"]) }
        dispatcher.responses["pand.getItems"] = { StepRequest r, int n -> ok([features: [[id: "p 1"]]]) }
        def doc = document([workflow("w", [
                [stepId: "find", operationId: '$sourceDescriptions.bag.pand.getItems', outputs: [id: '$response.body#/features/0/id']],
                [stepId: "code", operationId: '$sourceDescriptions.ext.getCode', parameters: [[name: "code", in: "path", value: '$steps.find.outputs.id']],
                 successCriteria: [[condition: '$statusCode == 200']], outputs: [label: '$response.body#/label', url: '$url']],
                [stepId: "again", operationPath: '{$sourceDescriptions.ext.url}#/paths/~1codes~1{code}/get', parameters: [[name: "code", in: "path", value: 'p 1']]],
        ], [outputs: [label: '$steps.code.outputs.label', url: '$steps.code.outputs.url']])],
                [sourceDescriptions: [[name: "bag", url: "/bag/api"], [name: "ext", url: "https://api.example.com/openapi.json"]]])

        when:
        def result = runner().run(doc, "w", [:])

        then:
        result.outputs() == [label: "Pand 1", url: "https://api.example.com/v1/codes/p%201"]
        dispatcher.requests*.operationId() == ["pand.getItems"]
        // the document is read once
        http.sent*.url == ["https://api.example.com/openapi.json", "https://api.example.com/v1/codes/p%201", "https://api.example.com/v1/codes/p%201"]
    }

    def "an operation without a handler is rejected"() {
        given:
        def doc = document([workflow("w", [[stepId: "a", operationId: "getCollections"]])])

        when:
        runner().run(doc, "w", [:])

        then:
        WorkflowException e = thrown()
        e.message.contains("cannot be a step")
    }

    def "an operation path selects the operation"() {
        given:
        dispatcher.responses["pand.getItem"] = { StepRequest r, int n -> ok([id: "x"]) }
        def doc = document([workflow("w", [[stepId: "a", operationPath: '{$sourceDescriptions.bag.url}#/paths/~1collections~1pand~1items~1{featureId}/get',
                                            parameters: [[name: "featureId", in: "path", value: "a b"]]]])])

        when:
        runner().run(doc, "w", [:])

        then:
        dispatcher.requests[0].operationId() == "pand.getItem"
        dispatcher.requests[0].path() == "/collections/pand/items/a%20b"
    }

    def "reusable parameters and actions of the components are resolved"() {
        given:
        dispatcher.responses["pand.getItems"] = { StepRequest r, int n -> n == 1 ? new StepResponse(500, [:], Optional.empty()) : ok([:]) }
        def doc = document([workflow("w", [[stepId: "a", operationId: "pand.getItems",
                                            parameters: [[reference: '$components.parameters.limit', value: 5]],
                                            onFailure: [[reference: '$components.failureActions.again']]]])],
                [components: [parameters: [limit: [name: "limit", in: "query", value: 10]],
                              failureActions: [again: [name: "again", type: "retry", retryLimit: 1]]]])

        when:
        runner().run(doc, "w", [:])

        then:
        dispatcher.requests*.query() == [[limit: "5"], [limit: "5"]]
    }

    def "the outputs of a step that calls a workflow see the outputs of the workflow"() {
        given:
        dispatcher.responses["pand.getItems"] = { StepRequest r, int n -> ok([numberReturned: 5]) }
        def doc = document([
                workflow("outer", [[stepId: "call", workflowId: "inner", successCriteria: [[condition: '$outputs.n > 1']], outputs: [n: '$outputs.n']]],
                        [outputs: [n: '$steps.call.outputs.n']]),
                workflow("inner", [[stepId: "a", operationId: "pand.getItems", outputs: [n: '$response.body#/numberReturned']]],
                        [outputs: [n: '$steps.a.outputs.n']]),
        ])

        expect:
        runner().run(doc, "outer", [:]).outputs() == [n: 5]
    }

    def "x-dso-forEach runs the workflow once per item, the outputs of the step are lists"() {
        given:
        dispatcher.responses["pand.getItem"] = { StepRequest r, int n -> ok([properties: [bouwjaar: r.pathParameters().featureId == "p1" ? 1930 : 1975]]) }
        def doc = document([
                workflow("outer", [
                        [stepId: "ids", operationId: "pand.getItems", outputs: [ids: [context: '$response.body', selector: '$.features[*].id', type: "jsonpath"]]],
                        [stepId: "each", workflowId: "inner", "x-dso-forEach": [items: '$steps.ids.outputs.ids', als: "id"],
                         parameters: [[name: "prefix", value: "pand"]], outputs: [jaar: '$outputs.jaar', label: '$outputs.label']]],
                        [outputs: [jaren: '$steps.each.outputs.jaar', labels: '$steps.each.outputs.label']]),
                workflow("inner", [[stepId: "get", operationId: "pand.getItem", parameters: [[name: "featureId", in: "path", value: '$inputs.id']],
                                    outputs: [jaar: '$response.body#/properties/bouwjaar']]],
                        [inputs : [type: "object", properties: [id: [type: "string"], prefix: [type: "string"]], required: ["id"]],
                         outputs: [jaar: '$steps.get.outputs.jaar', label: '{$inputs.prefix} {$inputs.id}']]),
        ])
        dispatcher.responses["pand.getItems"] = { StepRequest r, int n -> ok([features: features]) }

        when:
        def result = runner().run(doc, "outer", [:])

        then:
        result.outputs() == expected
        result.steps().find { it.stepId == "each" }.iterations == features.size()

        where:
        features               | expected
        [[id: "p1"], [id: "p2"]] | [jaren: [1930, 1975], labels: ["pand p1", "pand p2"]]
        [[id: "p1"]]           | [jaren: [1930], labels: ["pand p1"]]
        []                     | [jaren: [], labels: []]
    }

    def "a failed run of a loop fails the step"() {
        given:
        dispatcher.responses["pand.getItems"] = { StepRequest r, int n -> ok([features: [[id: "p1"], [id: "missing"]]]) }
        dispatcher.responses["pand.getItem"] = { StepRequest r, int n ->
            r.pathParameters().featureId == "p1" ? ok([id: "p1"]) : new StepResponse(404, [:], Optional.empty()) }
        def doc = document([
                workflow("outer", [
                        [stepId: "ids", operationId: "pand.getItems", outputs: [ids: [context: '$response.body', selector: '$.features[*].id', type: "jsonpath"]]],
                        [stepId: "each", workflowId: "inner", "x-dso-forEach": [items: '$steps.ids.outputs.ids', als: "id"]]]),
                workflow("inner", [[stepId: "get", operationId: "pand.getItem", parameters: [[name: "featureId", in: "path", value: '$inputs.id']]]],
                        [inputs: [type: "object", properties: [id: [type: "string"]]]]),
        ])

        when:
        runner().run(doc, "outer", [:])

        then:
        WorkflowException e = thrown()
        e.kind == WorkflowException.Kind.FAILURE
        e.message.contains("'each'")
        dispatcher.requests*.path() == ["/collections/pand/items", "/collections/pand/items/p1", "/collections/pand/items/missing"]
    }

    def "inputs set by the server reach every workflow that declares them, a caller cannot set them"() {
        given:
        serverInputs = [apiKey: "secret"]
        dispatcher.responses["pand.getItems"] = { StepRequest r, int n -> ok([numberReturned: 1]) }
        def inputs = [type: "object", properties: [apiKey: [type: "string", format: "password"]], required: ["apiKey"]]
        def doc = document([
                workflow("outer", [[stepId: "call", workflowId: "inner"]], [inputs: inputs]),
                workflow("inner", [[stepId: "a", operationId: "pand.getItems", parameters: [[name: "x-api-key", in: "header", value: '$inputs.apiKey']]]],
                        [inputs: inputs]),
        ])

        when:
        runner().run(doc, "outer", [:])

        then:
        dispatcher.requests[0].headers()["x-api-key"] == "secret"

        when:
        runner().run(doc, "outer", [apiKey: "other"])

        then:
        WorkflowException e = thrown()
        e.kind == WorkflowException.Kind.PROBLEM
        e.message.contains("set by the server")
    }

    def "JSONPath criteria and selectors decide and select"() {
        given:
        dispatcher.responses["pand.getItems"] = { StepRequest r, int n ->
            ok([features: [[id: "p1", properties: [status: "Pand in gebruik"]], [id: "p2", properties: [status: "Pand gesloopt"]]]]) }
        dispatcher.responses["pand.getItem"] = { StepRequest r, int n -> ok([id: "p0"]) }
        def doc = document([workflow("w", [
                [stepId: "a", operationId: "pand.getItems",
                 successCriteria: [[context: '$response.body', condition: '$[?length(@.features) == 2]', type: "jsonpath"]],
                 onSuccess      : [[name: "none", type: "end", criteria: [[context: '$response.body', condition: '$.features[?@.properties.status == \'{$inputs.status}\']', type: [type: "jsonpath", version: "rfc9535"]]]]],
                 outputs        : [ids: [context: '$response.body', selector: '$.features[?@.properties.status == \'{$inputs.status}\'].id', type: "jsonpath"]]],
                [stepId: "b", operationId: "pand.getItem", parameters: [[name: "featureId", in: "path", value: "p0"]]],
        ], [outputs: [ids: '$steps.a.outputs.ids']])])

        when:
        def result = runner().run(doc, "w", [status: status])

        then:
        result.outputs() == [ids: ids]
        dispatcher.requests.size() == requests

        where:
        status            | ids  | requests
        "Pand gesloopt"   | "p2" | 1
        "Pand in aanbouw" | null | 2
    }

    // answers pand.getItems with 8 features, pand.getItem after a pause; counts the requests in parallel
    static class SlowDispatcher extends FakeDispatcher {
        final java.util.concurrent.atomic.AtomicInteger running = new java.util.concurrent.atomic.AtomicInteger()
        final java.util.concurrent.atomic.AtomicInteger maximum = new java.util.concurrent.atomic.AtomicInteger()
        final java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger()

        @Override
        StepResponse dispatch(StepRequest request, int maximumResponseSize) {
            if (request.operationId() == "pand.getItems") {
                return ok([features: (1..8).collect { [id: "p" + it] }])
            }
            count.incrementAndGet()
            int now = running.incrementAndGet()
            maximum.accumulateAndGet(now, { a, b -> Math.max(a, b) } as java.util.function.IntBinaryOperator)
            Thread.sleep(50)
            running.decrementAndGet()
            return ok([id: request.pathParameters().featureId])
        }
    }

    WorkflowRunner parallelRunner(StepDispatcher slow, int parallel) {
        return new WorkflowRunner(slow, new HttpExternalApis(http.client(), []), { String id -> Optional.empty() }, "bag", 100, 1000,
                { long ms -> } as WorkflowRunner.Pause, [:], parallel)
    }

    def "the runs of a loop execute in parallel up to the maximum, in the order of the items"() {
        given:
        def slow = new SlowDispatcher()
        def doc = document([
                workflow("outer", [
                        [stepId: "ids", operationId: "pand.getItems", outputs: [ids: [context: '$response.body', selector: '$.features[*].id', type: "jsonpath"]]],
                        [stepId: "each", workflowId: "inner", "x-dso-forEach": [items: '$steps.ids.outputs.ids', als: "id"], outputs: [ids: '$outputs.id']]],
                        [outputs: [ids: '$steps.each.outputs.ids']]),
                workflow("inner", [[stepId: "get", operationId: "pand.getItem", parameters: [[name: "featureId", in: "path", value: '$inputs.id']],
                                    outputs: [id: '$response.body#/id']]],
                        [inputs: [type: "object", properties: [id: [type: "string"]]], outputs: [id: '$steps.get.outputs.id']]),
        ])

        when:
        def result = parallelRunner(slow, parallel).run(doc, "outer", [:])

        then:
        result.outputs() == [ids: (1..8).collect { "p" + it }]
        slow.count.get() == 8
        slow.maximum.get() == parallel

        where:
        parallel << [1, 4]
    }

    def "loops within a run of a parallel loop run one after another"() {
        given:
        def slow = new SlowDispatcher()
        def ids = [type: "array", items: [type: "string"]]
        def doc = document([
                workflow("outer", [[stepId: "each", workflowId: "middle", "x-dso-forEach": [items: '$inputs.ids', als: "id"],
                                    parameters: [[name: "ids", value: '$inputs.ids']]]],
                        [inputs: [type: "object", properties: [ids: ids]]]),
                workflow("middle", [[stepId: "each", workflowId: "inner", "x-dso-forEach": [items: '$inputs.ids', als: "id"]]],
                        [inputs: [type: "object", properties: [id: [type: "string"], ids: ids]]]),
                workflow("inner", [[stepId: "get", operationId: "pand.getItem", parameters: [[name: "featureId", in: "path", value: '$inputs.id']]]],
                        [inputs: [type: "object", properties: [id: [type: "string"]]]]),
        ])

        when:
        // 3 runs of "middle" in parallel, each with 4 runs of "inner" one after another
        parallelRunner(slow, 3).run(doc, "outer", [ids: ["a", "b", "c", "d"]])

        then:
        slow.count.get() == 16
        slow.maximum.get() == 3
    }
}
