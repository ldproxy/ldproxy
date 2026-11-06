/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.async.app

import com.fasterxml.jackson.databind.ObjectMapper
import dagger.Lazy
import de.ii.ogcapi.common.domain.async.ImmutableAsyncSubscriber
import de.ii.ogcapi.common.domain.async.JobContextAsync
import de.ii.ogcapi.common.domain.async.JobFailure
import de.ii.ogcapi.common.domain.async.JobResultFile
import de.ii.ogcapi.common.domain.async.JobResultsSelector
import de.ii.ogcapi.common.domain.async.OgcApiJobStatus.EntityType
import de.ii.ogcapi.foundation.domain.I18n
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApi
import de.ii.ogcapi.foundation.domain.TypedClientErrorException
import de.ii.xtralink.jobs.Identifiers.Status
import de.ii.xtralink.jobs.Job
import de.ii.xtralink.jobs.JobProgress
import de.ii.xtraplatform.base.domain.Jackson
import de.ii.xtraplatform.blobs.domain.ResourceStore
import de.ii.xtraplatform.entities.domain.EntityRegistry
import de.ii.xtraplatform.services.domain.ServicesContext
import de.ii.xtraplatform.web.domain.Http
import de.ii.xtraplatform.web.domain.HttpClient
import de.ii.xtraplatform.xtralink.domain.JobContext.JobContextEntity
import de.ii.xtraplatform.xtralink.domain.Jobs
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.core.MediaType
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import spock.lang.Specification

class AsyncJobsImplSpec extends Specification {

    static final String RESULT = "api/result_job-1.json"

    Map<String, Job> stored = [:]

    /** The store below jobs/{kind}. */
    Map<Path, byte[]> files = [:]

    List<Object> storeScope = []

    Jobs jobs = Mock()
    AsyncJobsImpl asyncJobs

    def setup() {
        jobs.get(_ as String) >> { String id -> Optional.ofNullable(stored[id]) }

        ResourceStore store = Stub()
        store.has(_ as Path) >> { Path p -> files.containsKey(p) }
        store.content(_ as Path) >> { Path p -> Optional.ofNullable(files[p]).map { new ByteArrayInputStream(it) } }
        ResourceStore root = Stub()
        root.with(*_) >> { args -> storeScope = args.flatten(); store }

        Jackson jackson = Stub() {
            getDefaultObjectMapper() >> new ObjectMapper()
        }
        Lazy<Set<JobResultsSelector>> noSelectors = { -> [] as Set } as Lazy

        asyncJobs = new AsyncJobsImpl(jobs, jackson, Stub(Http), noSelectors, root, Stub(EntityRegistry), servicesContext(), Stub(I18n))
    }

    def "the results of an unknown job are not found"() {
        when:
        asyncJobs.getResults("unknown")

        then:
        thrown(NotFoundException)
    }

    def "a job that was not submitted asynchronously is not found"() {
        given:
        stored["job-1"] = job(Status.SUCCESSFUL, entityContext(), [value: 1])

        when:
        asyncJobs.getResults("job-1")

        then:
        thrown(NotFoundException)

        and:
        asyncJobs.getStatusInfo("job-1").isEmpty()
    }

    def "the results of a #status #entityType job are not ready yet"() {
        given:
        stored["job-1"] = job(status, asyncContext(entityType), [:])

        when:
        asyncJobs.getResults("job-1")

        then:
        TypedClientErrorException e = thrown()
        e.response.status == 404
        e.type == expectedType

        where:
        status          | entityType                   || expectedType
        Status.ACCEPTED | EntityType.OGC_API_FEATURES  || "http://www.opengis.net/def/exceptions/ogcapi-features-11/1.0/result-not-ready"
        Status.RUNNING  | EntityType.OGC_API_FEATURES  || "http://www.opengis.net/def/exceptions/ogcapi-features-11/1.0/result-not-ready"
        Status.ACCEPTED | EntityType.OGC_API_PROCESSES || "https://www.opengis.net/def/exceptions/ogcapi-processes-1/1.0/result-not-ready"
        Status.RUNNING  | EntityType.OGC_API_PROCESSES || "https://www.opengis.net/def/exceptions/ogcapi-processes-1/1.0/result-not-ready"
    }

    def "the result of a rejected transaction is its response document with the status of the rejection"() {
        given:
        stored["job-1"] = job(Status.FAILED, asyncContext(EntityType.OGC_API_FEATURES),
                outputs(JobResultFile.of(RESULT, "application/json", 422)), ["rejected"])
        files[Path.of(RESULT)] = '{"exceptions":[]}'.getBytes(StandardCharsets.UTF_8)

        when:
        AsyncJobs.ResultDocument document = asyncJobs.getResultDocument("job-1").orElseThrow()

        then:
        document.status == 422
        document.mediaType == MediaType.APPLICATION_JSON_TYPE
        content(document) == '{"exceptions":[]}'
    }

    def "the result of a failed job without response document is a problem with the errors of the job: #jobOutputs"() {
        given:
        stored["job-1"] = job(Status.FAILED, asyncContext(EntityType.OGC_API_FEATURES), jobOutputs, ["Query failed with status 400"])

        when:
        AsyncJobs.ResultDocument document = asyncJobs.getResultDocument("job-1").orElseThrow()

        then:
        document.status == 500
        document.mediaType == new MediaType("application", "problem+json")
        new ObjectMapper().readValue(content(document), Map) ==
                [status: 500, title: "Internal Server Error", detail: "Job 'job-1' failed: Query failed with status 400"]

        where:
        jobOutputs << [[:], outputs(JobResultFile.of(RESULT, "application/json"))]
    }

    def "the result of a failed job with a stated reason is a problem with its status, type and detail: #reason"() {
        given:
        stored["job-1"] = job(Status.FAILED, asyncContext(EntityType.OGC_API_PROCESSES), reason.asOutputs(), ["boom"])

        when:
        AsyncJobs.ResultDocument document = asyncJobs.getResultDocument("job-1").orElseThrow()

        then:
        document.status == status
        document.mediaType == new MediaType("application", "problem+json")
        new ObjectMapper().readValue(content(document), Map) == problem

        where:
        reason                                                          || status | problem
        JobFailure.of(422, "Failed as requested.")                      || 422    | [status: 422, title: "Unprocessable Entity", detail: "Failed as requested."]
        JobFailure.of(400, "https://example.org/bad-value", "Bad value.") || 400  | [type: "https://example.org/bad-value", status: 400, title: "Bad Request", detail: "Bad value."]
    }

    def "the result of a failed job with a stated status of no client error is an unexpected failure: #jobOutputs"() {
        given: 'as stated by a processor outside of ldproxy'
        stored["job-1"] = job(Status.FAILED, asyncContext(EntityType.OGC_API_PROCESSES), jobOutputs, ["Disk full."])

        when:
        AsyncJobs.ResultDocument document = asyncJobs.getResultDocument("job-1").orElseThrow()

        then:
        document.status == 500
        document.mediaType == new MediaType("application", "problem+json")
        new ObjectMapper().readValue(content(document), Map) ==
                [status: 500, title: "Internal Server Error", detail: "Job 'job-1' failed: Disk full."]

        where:
        jobOutputs << [
                [failure: [status: 200, detail: "Everything is fine."]],
                [failure: [status: 503, detail: "Try again later."]],
                outputs(JobResultFile.of(RESULT, "application/json", 200))]
    }

    def "the outputs of a #status job are not available"() {
        given:
        stored["job-1"] = job(status, asyncContext(EntityType.OGC_API_PROCESSES), [:])

        when:
        asyncJobs.getResults("job-1")

        then:
        TypedClientErrorException e = thrown()
        e.response.status == 404
        e.type == "https://www.opengis.net/def/exceptions/ogcapi-processes-1/1.0/result-not-available"

        where:
        status << [Status.FAILED, Status.DISMISSED]
    }

    def "the results of a successful job are its outputs"() {
        given:
        stored["job-1"] = job(Status.SUCCESSFUL, asyncContext(EntityType.OGC_API_PROCESSES), [value: 1])

        expect:
        asyncJobs.getResults("job-1") == [value: 1]
    }

    def "a job without named outputs has no specific results"() {
        given:
        stored["job-1"] = job(Status.SUCCESSFUL, asyncContext(EntityType.OGC_API_FEATURES), [value: [1, 2]])

        when:
        retrieve(asyncJobs)

        then:
        thrown(NotFoundException)

        where:
        retrieve << [
                { AsyncJobs it -> it.getResultsSpecific("job-1", "value") },
                { AsyncJobs it -> it.getResultsSpecificN("job-1", "value", 0) },
        ]
    }

    def "a processes job has specific results"() {
        given:
        stored["job-1"] = job(Status.SUCCESSFUL, asyncContext(EntityType.OGC_API_PROCESSES), [value: [1, 2]])

        expect:
        asyncJobs.getResultsSpecific("job-1", "value") == [1, 2]
        asyncJobs.getResultsSpecificN("job-1", "value", 1) == 2
        asyncJobs.isMultiValued("job-1", "value")
    }

    def "a value beyond the values of an output is not found"() {
        given:
        stored["job-1"] = job(Status.SUCCESSFUL, asyncContext(EntityType.OGC_API_PROCESSES), [value: [1, 2]])

        when:
        asyncJobs.getResultsSpecificN("job-1", "value", 2)

        then:
        thrown(NotFoundException)
    }

    def "the result document is streamed from the store of the job kind"() {
        given:
        stored["job-1"] = job(Status.SUCCESSFUL, asyncContext(EntityType.OGC_API_FEATURES),
                outputs(JobResultFile.of(RESULT, "application/geo+json")))
        files[Path.of(RESULT)] = '{"type":"FeatureCollection"}'.getBytes(StandardCharsets.UTF_8)

        when:
        AsyncJobs.ResultDocument document = asyncJobs.getResultDocument("job-1").orElseThrow()

        then:
        document.status == 200
        document.mediaType == new MediaType("application", "geo+json")
        document.fileExtension == Optional.of("json")
        content(document) == '{"type":"FeatureCollection"}'
        storeScope == [Jobs.RESOURCE_TYPE, "feature-query"]
    }

    def "a result document that is no longer in the store is not found"() {
        given:
        stored["job-1"] = job(Status.SUCCESSFUL, asyncContext(EntityType.OGC_API_FEATURES),
                outputs(JobResultFile.of(RESULT, "application/geo+json")))

        when:
        asyncJobs.getResultDocument("job-1")

        then:
        thrown(NotFoundException)
    }

    def "a job with plain outputs has no result document"() {
        given:
        stored["job-1"] = job(Status.SUCCESSFUL, asyncContext(EntityType.OGC_API_PROCESSES), [value: 1])

        expect:
        asyncJobs.getResultDocument("job-1").isEmpty()
    }

    def "dismissing a #status job cancels it: #cancelled, and removes it"() {
        given:
        stored["job-1"] = job(status, asyncContext(EntityType.OGC_API_FEATURES), [:])

        when:
        def statusInfo = asyncJobs.dismissJob("job-1")

        then:
        (cancelled ? 1 : 0) * jobs.cancel("job-1")
        1 * jobs.delete("job-1")
        statusInfo.get().getStatus() == Status.DISMISSED

        where:
        status            || cancelled
        Status.ACCEPTED   || true
        Status.RUNNING    || true
        Status.SUCCESSFUL || false
        Status.FAILED     || false
        Status.DISMISSED  || false
    }

    def "a failed cancellation does not prevent the removal"() {
        given:
        stored["job-1"] = job(Status.RUNNING, asyncContext(EntityType.OGC_API_FEATURES), [:])

        when:
        asyncJobs.dismissJob("job-1")

        then:
        1 * jobs.cancel("job-1") >> { throw new IllegalStateException("cancel failed") }
        1 * jobs.delete("job-1")
        noExceptionThrown()
    }

    def "dismissing an unknown job is not found"() {
        when:
        asyncJobs.dismissJob("unknown")

        then:
        thrown(NotFoundException)
        0 * jobs.cancel(_)
        0 * jobs.delete(_)
    }

    def "a running job sends the in-progress callback only while it has no errors: #errors, #percent %"() {
        given:
        HttpClient httpClient = Mock()
        Http http = Stub() {
            getDefaultClient() >> httpClient
        }
        Jackson jackson = Stub() {
            getDefaultObjectMapper() >> Jobs.DEFAULT_MAPPER
        }
        def callbacks = new AsyncJobsImpl(jobs, jackson, http, { -> [] as Set } as Lazy, Stub(ResourceStore), Stub(EntityRegistry), servicesContext(), Stub(I18n))
        def subscriber = new ImmutableAsyncSubscriber.Builder()
                .inProgressUri("http://localhost/progress")
                .failedUri("http://localhost/failed")
                .build()
        def context = toMap(new JobContextAsync("api", EntityType.OGC_API_PROCESSES, Optional.empty(), Optional.of(subscriber)))

        when:
        callbacks.onProgress(job(Status.RUNNING, context, [:], errors, percent))

        then:
        posts * httpClient.postAsInputStream("http://localhost/progress", *_) >> new ByteArrayInputStream(new byte[0])
        0 * httpClient.postAsInputStream("http://localhost/failed", *_)

        where:
        errors   | percent || posts
        []       | 0       || 1
        []       | 50      || 1
        ["boom"] | 0       || 0
        // also when the job is about to finish: the client may have subscribed to this callback only
        []       | 100     || 1
    }

    def "the status info of a callback links the job to monitor and its results below the URL of the API"() {
        given:
        HttpClient httpClient = Mock()
        Http http = Stub() {
            getDefaultClient() >> httpClient
        }
        Jackson jackson = Stub() {
            getDefaultObjectMapper() >> Jobs.DEFAULT_MAPPER
        }
        OgcApi api = Stub() {
            getData() >> new ImmutableOgcApiDataV2.Builder().id('api').serviceType('OGC_API').build()
        }
        EntityRegistry entityRegistry = Stub() {
            getEntity(OgcApi, 'api') >> Optional.of(api)
        }
        def callbacks = new AsyncJobsImpl(jobs, jackson, http, { -> [] as Set } as Lazy, Stub(ResourceStore),
                entityRegistry, servicesContext(), Stub(I18n))
        def subscriber = new ImmutableAsyncSubscriber.Builder()
                .failedUri("http://localhost/failed")
                .build()
        def context = toMap(new JobContextAsync("api", EntityType.OGC_API_PROCESSES, Optional.empty(), Optional.of(subscriber)))
        byte[] posted = null

        when:
        callbacks.onProgress(job(Status.FAILED, context, [:], ["boom"]))

        then:
        1 * httpClient.postAsInputStream("http://localhost/failed", *_) >> { args ->
            posted = args[1] as byte[]
            new ByteArrayInputStream(new byte[0])
        }
        // the serialized links are sorted, the order of the generator does not matter here
        def links = new ObjectMapper().readValue(posted, Map).links.collect { [it.rel, it.type, it.href] } as Set
        links == [
                ['monitor', null, 'https://example.org/rest/services/api/jobs/job-1'],
                ['https://www.opengis.net/def/rel/ogc/1.0/results', 'application/problem+json', 'https://example.org/rest/services/api/jobs/job-1/results']
        ] as Set
    }

    // --- fixtures ------------------------------------------------------------------------------

    private static ServicesContext servicesContext() {
        return { -> URI.create('https://example.org/rest/services') } as ServicesContext
    }

    private static Map<String, Object> asyncContext(EntityType entityType) {
        toMap(new JobContextAsync("api", entityType, Optional.empty(), Optional.empty()))
    }

    private static Map<String, Object> entityContext() {
        toMap(new JobContextEntity("api"))
    }

    private static Map<String, Object> outputs(JobResultFile resultFile) {
        toMap(resultFile)
    }

    private static Map<String, Object> toMap(Object value) {
        Jobs.DEFAULT_MAPPER.readValue(Jobs.DEFAULT_MAPPER.writeValueAsBytes(value), Jobs.MAP_TYPE)
    }

    private static Job job(Status status, Map<String, Object> context, Map<String, Object> outputs, List<String> errors = [], int percent = 0) {
        new Job("job-1", "feature-query", 0L, -1L, -1L, -1L, 1000, new JobProgress(0, 1, percent, null), status, errors,
                context, null, null, [:], outputs, Optional.empty(), Optional.empty(), [], [], Optional.empty(),
                Optional.empty())
    }

    private static String content(AsyncJobs.ResultDocument document) {
        def out = new ByteArrayOutputStream()
        document.content.write(out)
        out.toString(StandardCharsets.UTF_8)
    }
}
