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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import spock.lang.Specification

class WorkflowLogSpec extends Specification {

    Logger logger = (Logger) LoggerFactory.getLogger(WorkflowLog)
    ListAppender<ILoggingEvent> appender = new ListAppender<>()
    Level level

    def setup() {
        level = logger.level
        appender.start()
        logger.addAppender(appender)
    }

    def cleanup() {
        logger.detachAppender(appender)
        logger.level = level
    }

    def "each step is logged with its request, status code and duration, never with headers or secret inputs"() {
        given:
        logger.level = Level.TRACE
        FakeDispatcher dispatcher = new FakeDispatcher()
        dispatcher.responses["pand.getItems"] = { StepRequest r, int n -> ok([features: [[id: "p1"], [id: "p2"]]]) }
        dispatcher.responses["pand.getItem"] = { StepRequest r, int n -> ok([id: r.pathParameters().featureId]) }
        def secret = [type: "object", properties: [apiKey: [type: "string"], token: [type: "string", format: "password"], id: [type: "string"]]]
        def doc = document([
                workflow("outer", [
                        [stepId: "ids", operationId: "pand.getItems", parameters: [[name: "x-api-key", in: "header", value: '$inputs.apiKey'], [name: "limit", in: "query", value: 2]],
                         outputs: [ids: [context: '$response.body', selector: '$.features[*].id', type: "jsonpath"]]],
                        [stepId: "each", workflowId: "inner", "x-dso-forEach": [items: '$steps.ids.outputs.ids', als: "id"]]],
                        [inputs: secret]),
                workflow("inner", [[stepId: "get", operationId: "pand.getItem", parameters: [[name: "featureId", in: "path", value: '$inputs.id'],
                                                                                         [name: "authorization", in: "header", value: '$inputs.token']]]],
                        [inputs: secret]),
        ])
        def runner = new WorkflowRunner(dispatcher, new HttpExternalApis(new FakeHttpClient().client(), []), { String id -> Optional.empty() }, "bag", 100, 1000,
                { long ms -> } as WorkflowRunner.Pause, [apiKey: "server-secret"])

        when:
        runner.run(doc, "outer", [token: "caller-secret"])
        def lines = appender.list*.formattedMessage

        then:
        lines.any { it ==~ /outer\/ids: GET \/bag\/collections\/pand\/items\?limit=2 -> 200 in \d+ ms/ }
        lines.any { it.contains("outer/each: calls workflow 'inner' for 2 items") }
        lines.any { it.contains("item 2/2: p2") }
        lines.any { it ==~ /  inner\/get: GET \/bag\/collections\/pand\/items\/p1 -> 200 in \d+ ms/ }
        lines.any { it.contains("response content: {\"features\"") }
        lines.any { it.contains("workflow 'outer' finished") }
        lines.any { it.contains("apiKey=***") && it.contains("token=***") }
        lines.every { !it.contains("server-secret") && !it.contains("caller-secret") }
    }

    def "nothing is logged without DEBUG"() {
        given:
        logger.level = Level.INFO
        FakeDispatcher dispatcher = new FakeDispatcher()
        dispatcher.responses["pand.getItems"] = { StepRequest r, int n -> ok([features: []]) }
        def doc = document([workflow("w", [[stepId: "a", operationId: "pand.getItems"]])])

        when:
        new WorkflowRunner(dispatcher, new HttpExternalApis(new FakeHttpClient().client(), []), { String id -> Optional.empty() }, "bag", 100, 1000,
                { long ms -> } as WorkflowRunner.Pause).run(doc, "w", [:])

        then:
        appender.list.isEmpty()
    }
}
