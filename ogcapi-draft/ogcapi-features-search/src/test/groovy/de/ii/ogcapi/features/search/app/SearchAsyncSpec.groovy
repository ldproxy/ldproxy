/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app

import de.ii.ogcapi.common.domain.async.JobResultFile
import de.ii.ogcapi.features.search.domain.ImmutableSearchConfiguration
import de.ii.ogcapi.features.search.domain.QueryExpression
import de.ii.ogcapi.foundation.domain.ApiRequestContext
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApi
import de.ii.xtralink.jobs.Identifiers.Status
import de.ii.xtralink.jobs.Job
import de.ii.xtraplatform.blobs.domain.ResourceStore
import de.ii.xtraplatform.web.domain.URICustomizer
import de.ii.xtraplatform.xtralink.domain.Jobs
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.StreamingOutput
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import spock.lang.Specification

class SearchAsyncSpec extends Specification {

    static final MediaType GEO_JSON = new MediaType("application", "geo+json")
    static final String RESULT = "api/result_job-1.json"
    static final String FEATURES = '{"type":"FeatureCollection","features":[]}'

    CompletableFuture<Job> finished = new CompletableFuture<>()
    Jobs jobs = Stub()
    ResourceStore resultStore = Mock()

    def setup() {
        jobs.push(*_) >> job(Status.ACCEPTED, [:])
        jobs.waitFor("job-1") >> finished
        resultStore.content(Path.of(RESULT)) >> Optional.of(new ByteArrayInputStream(FEATURES.getBytes(StandardCharsets.UTF_8)))
    }

    def "a job that finishes within the wait is answered synchronously and its stored result is deleted"() {
        given:
        finished.complete(job(Status.SUCCESSFUL, resultFile()))

        when:
        Response response = submit(["respond-async, wait=5"])

        then:
        response.status == 200
        response.mediaType == GEO_JSON
        response.getHeaderString("Preference-Applied") == "wait=5"
        0 * resultStore.delete(_)

        when:
        String content = content(response)

        then:
        content == FEATURES
        1 * resultStore.delete(Path.of(RESULT))
    }

    def "a job that does not finish within the wait is accepted"() {
        when:
        Response response = submit(["respond-async, wait=1"])

        then:
        accepted(response)
        response.getHeaderString("Preference-Applied") == "respond-async, wait=1"
    }

    def "a job that fails within the wait is accepted"() {
        given:
        finished.complete(job(Status.FAILED, [:]))

        when:
        Response response = submit(["respond-async, wait=5"])

        then:
        accepted(response)
        response.getHeaderString("Preference-Applied") == "respond-async, wait=5"
        0 * resultStore.content(_)
    }

    def "a wait beyond maxWait is reported as capped when the job finishes in time"() {
        given:
        finished.complete(job(Status.SUCCESSFUL, resultFile()))

        when:
        Response response = submit(["respond-async, wait=30"], 10)

        then:
        response.status == 200
        response.getHeaderString("Preference-Applied") == "wait=10"
    }

    def "a wait beyond maxWait is not claimed when the job does not finish in time"() {
        when:
        Response response = submit(["respond-async, wait=30"], 1)

        then:
        accepted(response)
        response.getHeaderString("Preference-Applied") == "respond-async"
    }

    def "without a wait, the job is accepted immediately: #prefer"() {
        when:
        Response response = submit(prefer)

        then:
        accepted(response)
        response.getHeaderString("Preference-Applied") == "respond-async"
        !finished.isDone()

        where:
        prefer << [["respond-async"], ["respond-async, wait=0"]]
    }

    // --- fixtures ------------------------------------------------------------------------------

    private Response submit(List<String> prefer, int maxWait = 60) {
        SearchAsync.submit(jobs, resultStore, requestContext(maxWait), prefer, query(), false)
    }

    private static boolean accepted(Response response) {
        assert response.status == 202
        assert response.location == URI.create("http://localhost/api/jobs/job-1")
        assert response.entity == [jobId: "job-1"]
        true
    }

    private ApiRequestContext requestContext(int maxWait) {
        def apiData = new ImmutableOgcApiDataV2.Builder()
                .id("api")
                .addExtensions(new ImmutableSearchConfiguration.Builder()
                        .enabled(true)
                        .async(true)
                        .maxWait(maxWait)
                        .build())
                .build()
        OgcApi api = Stub() {
            getData() >> apiData
        }
        Stub(ApiRequestContext) {
            getApi() >> api
            getUriCustomizer() >> new URICustomizer("http://localhost/api/search")
            getApiUriCustomizer() >> new URICustomizer("http://localhost/api")
            getMediaType() >> new ImmutableApiMediaType.Builder()
                    .type(GEO_JSON)
                    .label("GeoJSON")
                    .parameter("json")
                    .build()
            getLanguage() >> Optional.empty()
        }
    }

    private static QueryExpression query() {
        QueryExpression.of(new ByteArrayInputStream('{"collections":["c1"]}'.getBytes(StandardCharsets.UTF_8)))
    }

    private static Map<String, Object> resultFile() {
        Jobs.DEFAULT_MAPPER.convertValue(JobResultFile.of(RESULT, "application/geo+json"), Jobs.MAP_TYPE)
    }

    private static Job job(Status status, Map<String, Object> outputs) {
        new Job("job-1", "feature-query", 0L, -1L, -1L, -1L, 1000, null, status, [], [:], null, null, [:], outputs,
                Optional.empty(), Optional.empty(), [], Optional.empty(), Optional.empty())
    }

    private static String content(Response response) {
        def out = new ByteArrayOutputStream()
        ((StreamingOutput) response.entity).write(out)
        out.toString(StandardCharsets.UTF_8)
    }
}
