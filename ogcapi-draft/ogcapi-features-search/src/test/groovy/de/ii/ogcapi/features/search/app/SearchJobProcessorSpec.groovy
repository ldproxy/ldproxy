/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app

import de.ii.ogcapi.common.domain.async.JobResultFile
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders
import de.ii.ogcapi.features.search.domain.ImmutableSearchJob
import de.ii.ogcapi.features.search.domain.SearchConfiguration
import de.ii.ogcapi.features.search.domain.SearchJob
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler.Query
import de.ii.ogcapi.foundation.domain.OgcApi
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.xtralink.jobs.BaseJob
import de.ii.xtralink.jobs.Identifiers
import de.ii.xtralink.jobs.Job
import de.ii.xtralink.jobs.PartialJob
import de.ii.xtralink.jobs.PartialJobConfiguration
import de.ii.xtraplatform.base.domain.AppContext
import de.ii.xtraplatform.blobs.domain.ResourceStore
import de.ii.xtraplatform.crs.domain.OgcCrs
import de.ii.xtraplatform.entities.domain.EntityRegistry
import de.ii.xtraplatform.xtralink.domain.JobContext
import de.ii.xtraplatform.xtralink.domain.JobInputs
import de.ii.xtraplatform.xtralink.domain.JobProcessing
import de.ii.xtraplatform.xtralink.domain.Jobs
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.StreamingOutput
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import spock.lang.Specification
import spock.lang.TempDir

class SearchJobProcessorSpec extends Specification {

    static final String FEATURES = '{"type":"FeatureCollection","features":[]}'

    @TempDir
    Path tmpDir

    /** The resource store below jobs/feature-query. */
    Map<Path, byte[]> files = [:]

    List<Object> storeScope = []

    /** The response of the query handler. */
    Response response = Response.ok(streaming(FEATURES)).build()

    int queries = 0

    RecordingJobs jobs = new RecordingJobs()
    SearchJobProcessor processor

    def setup() {
        ResourceStore store = Stub()
        store.put(_ as Path, _ as InputStream) >> { Path p, InputStream content -> files[p] = content.readAllBytes() }
        ResourceStore root = Stub()
        root.with(*_) >> { args -> storeScope = args.flatten(); store }

        AppContext appContext = Stub() {
            getTmpDir() >> tmpDir
            getPathPrefix() >> []
        }

        FeaturesCoreConfiguration coreConfiguration = Stub() {
            getDefaultEpsgCrs() >> OgcCrs.CRS84
        }
        OgcApiDataV2 data = Stub() {
            getExtension(FeaturesCoreConfiguration) >> Optional.of(coreConfiguration)
            getExtension(SearchConfiguration) >> Optional.empty()
        }
        OgcApi api = Stub() {
            getId() >> "api"
            getData() >> data
        }
        EntityRegistry entities = Stub() {
            getEntity(OgcApi, "api") >> Optional.of(api)
            getEntity(OgcApi, _) >> Optional.empty()
        }

        SearchQueriesHandler queryHandler = Stub() {
            handle(Query.QUERY, _, _) >> { queries++; response }
        }

        processor = new SearchJobProcessor(appContext, entities, root, Stub(FeaturesCoreProviders), queryHandler)
    }

    def "the result is written as an output file of the job and referenced as the job outputs"() {
        when:
        def result = execute()

        then:
        result.status() == Identifiers.Result.SUCCESS
        storeScope == [Jobs.RESOURCE_TYPE, SearchJob.KIND]
        text("api/result_job-1.json") == FEATURES
        files[Path.of(".outputs/job-1/api/result_job-1.json")] == new byte[0]
        jobs.outputs == JobResultFile.of("api/result_job-1.json", "application/geo+json")
        jobs.updates == [1]
    }

    def "the file extension of the result is the format parameter"() {
        when:
        execute("api", "text/csv", "csv")

        then:
        files.keySet() == [Path.of("api/result_job-1.csv"), Path.of(".outputs/job-1/api/result_job-1.csv")] as Set
        jobs.outputs == JobResultFile.of("api/result_job-1.csv", "text/csv")
    }

    def "a result that is not streamed is written as text"() {
        given:
        response = Response.ok(FEATURES).build()

        when:
        execute()

        then:
        text("api/result_job-1.json") == FEATURES
    }

    def "a failed query writes no result"() {
        given:
        response = Response.status(400).entity("bad query").build()

        when:
        def result = execute()

        then:
        result.status() == Identifiers.Result.FAILURE
        result.messages() == ["Query failed with status 400"]
        files.isEmpty()
        jobs.outputs == null
        jobs.updates.isEmpty()
    }

    def "a job for an unknown API is rejected without a query"() {
        when:
        def result = execute("unknown")

        then:
        result.status() == Identifiers.Result.FAILURE
        result.messages() == ["API with id 'unknown' not found"]
        queries == 0
        files.isEmpty()
    }

    // --- fixtures ------------------------------------------------------------------------------

    private def execute(String apiId = "api", String mediaType = "application/geo+json", String parameter = "json") {
        SearchJob inputs = new ImmutableSearchJob.Builder()
                .apiId(apiId)
                .requestUri("http://localhost/api/search")
                .queryExpression('{"collections":["c1"]}')
                .isStoredQuery(false)
                .mediaType(mediaType)
                .mediaTypeLabel("label")
                .mediaTypeParameter(parameter)
                .build()
        processor.execute(partialJob(), job(), inputs, jobs)
    }

    private static Job job() {
        new Job("job-1", SearchJob.KIND, null, null, null, null, null, null, null, [], [:], null, null, [:], [:],
                Optional.empty(), Optional.empty(), [], Optional.empty(), Optional.empty())
    }

    private static PartialJob partialJob() {
        new PartialJob("partial-1", SearchJob.KIND + ":execute", null, null, null, null, null, null, null, [], [:],
                "job-1", null, null, [], Optional.empty())
    }

    private static StreamingOutput streaming(String content) {
        { OutputStream out -> out.write(content.getBytes(StandardCharsets.UTF_8)) } as StreamingOutput
    }

    private String text(String path) {
        new String(files[Path.of(path)], StandardCharsets.UTF_8)
    }

    /** Records outputs and progress; success() and failure() keep their default implementations. */
    static class RecordingJobs implements JobProcessing {
        Object outputs
        List<Integer> updates = []

        PartialJob push(PartialJobConfiguration partialJob) { null }

        PartialJob repush(String id) { null }

        void init(String jobId, int progressTotal, Map<String, ?> progressDetails) {}

        void update(String partialJobId, int delta) { updates << delta }

        void outputs(String id, Object outputs) { this.outputs = outputs }

        def <T extends JobInputs> T getInputs(Job job, Class<T> contextClass) { null }

        def <T extends JobContext> T getContext(BaseJob job, Class<T> contextClass) { null }

        boolean isCancelled(String jobId) { false }
    }
}
