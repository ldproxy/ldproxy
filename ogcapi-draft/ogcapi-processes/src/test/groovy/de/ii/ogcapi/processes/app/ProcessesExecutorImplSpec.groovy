/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app

import static de.ii.ogcapi.processes.app.ProcessFixtures.input
import static de.ii.ogcapi.processes.app.ProcessFixtures.output
import static de.ii.ogcapi.processes.app.ProcessFixtures.process
import static de.ii.ogcapi.processes.app.ProcessFixtures.schema
import static de.ii.ogcapi.processes.domain.model.OgcProcessSummary.JobControlOptions.ASYNC_EXECUTE
import static de.ii.ogcapi.processes.domain.model.OgcProcessSummary.JobControlOptions.SYNC_EXECUTE

import de.ii.ogcapi.common.domain.async.AsyncCallbacks
import de.ii.ogcapi.common.domain.async.JobFailure
import de.ii.ogcapi.foundation.domain.TypedClientErrorException
import de.ii.ogcapi.foundation.infra.json.SchemaValidatorImpl
import de.ii.ogcapi.processes.domain.model.ImmutableOgcExecute
import de.ii.ogcapi.processes.domain.model.ImmutableOgcSchema
import de.ii.ogcapi.processes.domain.model.ProcessData
import de.ii.ogcapi.processes.domain.model.Property.Type
import de.ii.xtralink.jobs.Identifiers.Status
import de.ii.xtralink.jobs.Job
import de.ii.xtralink.jobs.JobConfiguration
import de.ii.xtralink.jobs.JobProgress
import de.ii.xtralink.jobs.PartialJobConfiguration
import de.ii.xtraplatform.base.domain.Jackson
import de.ii.xtraplatform.xtralink.domain.JobProcessing
import de.ii.xtraplatform.xtralink.domain.Jobs
import jakarta.ws.rs.WebApplicationException
import java.util.concurrent.CompletableFuture
import spock.lang.Specification

class ProcessesExecutorImplSpec extends Specification {

    ProcessData testProcess = process([SYNC_EXECUTE, ASYNC_EXECUTE],
            [
                    count : input(schema(Type.INTEGER)),
                    labels: input(schema(Type.STRING), 0, 2),
                    bbox  : input(new ImmutableOgcSchema.Builder().type(Type.OBJECT).format("ogc-bbox").build(), 0),
                    options: input(schema(Type.OBJECT), 0)
            ],
            [result: output(schema(Type.INTEGER))])

    Jobs jobs = Mock()
    JobProcessing jobProcessing = Mock()

    ProcessesExecutorImpl executor

    def setup() {
        Jackson jackson = Stub() {
            getDefaultObjectMapper() >> Jobs.DEFAULT_MAPPER
        }
        executor = new ProcessesExecutorImpl(jackson, jobs, jobProcessing, new SchemaValidatorImpl(), Stub(AsyncCallbacks))
    }

    def "valid inputs are accepted: #inputs"() {
        when:
        executor.executeAsync("api", testProcess, execute(inputs), 0, 3600)

        then:
        1 * jobs.push(_, _) >> job(Status.ACCEPTED, [:])

        where:
        inputs << [
                [count: 1],
                [count: 1, labels: ["a", "b"]],
                [count: [value: 1]],
                [count: 1, labels: [[value: "a"], "b"]],
                [count: 1, bbox: [bbox: [0, 0, 1, 1]]],
                [count: 1, bbox: [bbox: [0, 0, 0, 1, 1, 1]]]
        ]
    }

    def "invalid inputs are rejected before a job is created: #reason"() {
        when:
        executor.executeAsync("api", testProcess, execute(inputs), 0, 3600)

        then:
        thrown(IllegalArgumentException)
        0 * jobs.push(*_)

        where:
        reason                          | inputs
        "missing required input"        | [:]
        "unknown input"                 | [count: 1, other: 1]
        "wrong type"                    | [count: "one"]
        "wrong type in qualified value" | [count: [value: "one"]]
        "multi-valued input not array"  | [count: 1, labels: "a"]
        "too many values"               | [count: 1, labels: ["a", "b", "c"]]
        "null value in array"           | [count: 1, labels: ["a", null]]
        "bbox with 5 coordinates"       | [count: 1, bbox: [bbox: [0, 0, 1, 1, 1]]]
    }

    def "a job with a setup step is pushed with it and the execution step is left to the setup"() {
        given:
        def process = process([ASYNC_EXECUTE], [count: input(schema(Type.INTEGER))], [:], true)

        when:
        executor.executeAsync("api", process, execute([count: 1]), 0, 3600)

        then:
        1 * jobs.push({ JobConfiguration job -> job.kind() == "oap:TestProcess" && job.setup() && job.cleanup() }, _) >> job(Status.ACCEPTED, [:])
        0 * jobProcessing.push(_)
    }

    def "by default the execution step is queued directly, without setup and cleanup steps"() {
        when:
        executor.executeAsync("api", testProcess, execute([count: 1]), 0, 3600)

        then:
        1 * jobs.push({ JobConfiguration job -> !job.setup() && !job.cleanup() }, _) >> job(Status.ACCEPTED, [:])
        1 * jobProcessing.init("job-1", 1, null)
        1 * jobProcessing.push({ PartialJobConfiguration partial -> partial.kind() == "oap:TestProcess:execute" })
    }

    def "the selected outputs of a synchronous execution are returned"() {
        when:
        def results = executor.executeSync("api", testProcess, execute([count: 1], [result: [:]]), 1000)

        then:
        1 * jobs.push(_) >> job(Status.ACCEPTED, [:])
        1 * jobs.waitFor("job-1") >> CompletableFuture.completedFuture(job(Status.SUCCESSFUL, [result: 42, other: 1]))
        results == [result: 42]
    }

    def "the job receives the validated inputs: #inputs"() {
        when:
        executor.executeAsync("api", testProcess, execute(inputs), 0, 3600)

        then:
        1 * jobs.push({ JobConfiguration job -> Jobs.DEFAULT_MAPPER.convertValue(job.inputs(), Map) == jobInputs }, _) >> job(Status.ACCEPTED, [:])

        where:
        inputs                                                     | jobInputs
        [count: 1]                                                 | [count: 1]
        [count: [value: 1]]                                        | [count: 1]
        [count: 1, labels: [[value: "a"], "b"]]                    | [count: 1, labels: ["a", "b"]]
        [count: 1, bbox: [bbox: [0, 0, 1, 1]]]                     | [count: 1, bbox: [bbox: [0, 0, 1, 1]]]
        [count: 1, bbox: [value: [bbox: [0, 0, 1, 1]]]]            | [count: 1, bbox: [bbox: [0, 0, 1, 1]]]
        // an object value is a qualified value (Requirement 22, NOTE 3), also if the wrapper
        // matches the object schema itself
        [count: 1, options: [value: [a: 1]]]                       | [count: 1, options: [a: 1]]
        [count: 1, options: [value: [a: 1], mediaType: 'application/json']] | [count: 1, options: [a: 1]]
        // tolerated: an object value without the wrapper
        [count: 1, options: [a: 1]]                                | [count: 1, options: [a: 1]]
    }

    def "a synchronous execution whose job #status fails"() {
        when:
        executor.executeSync("api", testProcess, execute([count: 1]), 1000)

        then:
        1 * jobs.push(_) >> job(Status.ACCEPTED, [:])
        1 * jobs.waitFor("job-1") >> CompletableFuture.completedFuture(job(status, [:], errors))
        IllegalStateException e = thrown()
        e.message == message

        where:
        status           | errors   | message
        Status.FAILED    | ["boom"] | "The execution of process 'TestProcess' ended with job status FAILED: boom"
        Status.DISMISSED | []       | "The execution of process 'TestProcess' ended with job status DISMISSED."
    }

    def "a synchronous execution whose job failed with a stated client error answers it"() {
        when:
        executor.executeSync("api", testProcess, execute([count: 1]), 1000)

        then:
        1 * jobs.push(_) >> job(Status.ACCEPTED, [:])
        1 * jobs.waitFor("job-1") >> CompletableFuture.completedFuture(
                job(Status.FAILED, JobFailure.of(422, "urn:example:invalid", "The input is invalid.").asOutputs(), ["The input is invalid."]))
        TypedClientErrorException e = thrown()
        e.response.status == 422
        e.message == "The input is invalid."
        e.type == "urn:example:invalid"
    }

    def "a synchronous execution whose job failed with a stated server error is unexpected"() {
        when:
        executor.executeSync("api", testProcess, execute([count: 1]), 1000)

        then:
        1 * jobs.push(_) >> job(Status.ACCEPTED, [:])
        1 * jobs.waitFor("job-1") >> CompletableFuture.completedFuture(
                job(Status.FAILED, JobFailure.of(500, "Disk full.").asOutputs(), ["Disk full."]))
        IllegalStateException e = thrown()
        e.message == "The execution of process 'TestProcess' ended with job status FAILED: Disk full."
    }

    def "a synchronous execution that exceeds the timeout is cancelled"() {
        when:
        executor.executeSync("api", testProcess, execute([count: 1]), 10)

        then:
        1 * jobs.push(_) >> job(Status.ACCEPTED, [:])
        1 * jobs.waitFor("job-1") >> new CompletableFuture<Job>()
        1 * jobs.cancel("job-1")
        WebApplicationException e = thrown()
        e.response.status == 504
    }

    private static def execute(Map<String, Object> inputs, Map<String, Object> outputs = null) {
        def builder = new ImmutableOgcExecute.Builder().inputs(inputs)
        if (outputs != null) {
            builder.outputSelections(outputs.collectEntries { k, v -> [(k): new de.ii.ogcapi.processes.domain.model.ImmutableOgcFormat.Builder().build()] })
        }
        builder.build()
    }

    private static Job job(Status status, Map<String, Object> outputs, List<String> errors = []) {
        new Job("job-1", "oap:TestProcess", 0L, -1L, -1L, -1L, 1000, new JobProgress(0, 1, 0, null), status, errors,
                [:], null, null, [:], outputs, Optional.empty(), Optional.empty(), [], Optional.empty(),
                Optional.empty())
    }
}
