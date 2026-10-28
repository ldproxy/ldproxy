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
import de.ii.ogcapi.common.domain.async.JobContextAsync
import de.ii.ogcapi.common.domain.async.JobResultFile
import de.ii.ogcapi.common.domain.async.JobResultsSelector
import de.ii.ogcapi.common.domain.async.OgcApiJobStatus.EntityType
import de.ii.ogcapi.foundation.domain.TypedClientErrorException
import de.ii.xtralink.jobs.Identifiers.Status
import de.ii.xtralink.jobs.Job
import de.ii.xtralink.jobs.JobProgress
import de.ii.xtraplatform.base.domain.Jackson
import de.ii.xtraplatform.blobs.domain.ResourceStore
import de.ii.xtraplatform.web.domain.Http
import de.ii.xtraplatform.xtralink.domain.JobContext.JobContextEntity
import de.ii.xtraplatform.xtralink.domain.Jobs
import jakarta.ws.rs.BadRequestException
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

        asyncJobs = new AsyncJobsImpl(jobs, jackson, Stub(Http), noSelectors, root)
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

    def "the results of a failed job are a bad request"() {
        given:
        stored["job-1"] = job(Status.FAILED, asyncContext(EntityType.OGC_API_FEATURES), [:])

        when:
        asyncJobs.getResults("job-1")

        then:
        thrown(BadRequestException)
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
    }

    def "the result document is streamed from the store of the job kind"() {
        given:
        stored["job-1"] = job(Status.SUCCESSFUL, asyncContext(EntityType.OGC_API_FEATURES),
                outputs(JobResultFile.of(RESULT, "application/geo+json")))
        files[Path.of(RESULT)] = '{"type":"FeatureCollection"}'.getBytes(StandardCharsets.UTF_8)

        when:
        AsyncJobs.ResultDocument document = asyncJobs.getResultDocument("job-1").orElseThrow()

        then:
        document.mediaType == new MediaType("application", "geo+json")
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

    def "dismissing a #status job cancels it: #cancelled"() {
        given:
        stored["job-1"] = job(status, asyncContext(EntityType.OGC_API_FEATURES), [:])

        when:
        asyncJobs.dismissJob("job-1")

        then:
        (cancelled ? 1 : 0) * jobs.cancel("job-1")

        where:
        status            || cancelled
        Status.ACCEPTED   || true
        Status.RUNNING    || true
        Status.SUCCESSFUL || false
        Status.FAILED     || false
        Status.DISMISSED  || false
    }

    def "dismissing an unknown job is not found"() {
        when:
        asyncJobs.dismissJob("unknown")

        then:
        thrown(NotFoundException)
        0 * jobs.cancel(_)
    }

    // --- fixtures ------------------------------------------------------------------------------

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

    private static Job job(Status status, Map<String, Object> context, Map<String, Object> outputs) {
        new Job("job-1", "feature-query", 0L, -1L, -1L, -1L, 1000, new JobProgress(0, 1, 0, null), status, [],
                context, null, null, [:], outputs, Optional.empty(), Optional.empty(), [], Optional.empty(),
                Optional.empty())
    }

    private static String content(AsyncJobs.ResultDocument document) {
        def out = new ByteArrayOutputStream()
        document.content.write(out)
        out.toString(StandardCharsets.UTF_8)
    }
}
