/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app

import de.ii.ogcapi.common.domain.async.JobFailure
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders
import de.ii.ogcapi.features.search.domain.SearchConfiguration
import de.ii.ogcapi.features.search.domain.SearchJob
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler.Query
import de.ii.ogcapi.foundation.domain.ApiRequestContext
import de.ii.ogcapi.foundation.domain.OgcApi
import de.ii.ogcapi.foundation.domain.SchemaValidator
import de.ii.xtralink.jobs.Identifiers
import de.ii.xtralink.jobs.Job
import de.ii.xtralink.jobs.PartialJob
import de.ii.xtraplatform.base.domain.AppContext
import de.ii.xtraplatform.blobs.domain.ResourceStore
import de.ii.xtraplatform.cql.app.CqlImpl
import de.ii.xtraplatform.crs.domain.OgcCrs
import de.ii.xtraplatform.entities.domain.EntityRegistry
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.StreamingOutput
import spock.lang.TempDir

import java.nio.charset.StandardCharsets
import java.nio.file.Path

// A search that runs as a step of a job of another kind, e.g. a process with the step
// feature-query:execute, takes the call of the stored query from a file that an earlier step names
// in the job output queryPath.
class SearchJobStepSpec extends StoredQueryFixture {

    static final String QUERY_PATH = "deliveries/ready/d1/job.json"
    static final String FEATURES = '<wfs:FeatureCollection/>'

    @TempDir
    Path tmpDir

    /** The resource store below jobs/feature-query. */
    Map<Path, byte[]> files = [:]

    Response response = Response.ok(streaming(FEATURES)).build()
    int queries = 0
    ApiRequestContext requestContext

    SearchJobProcessorSpec.RecordingJobs jobs = new SearchJobProcessorSpec.RecordingJobs()
    SearchJobProcessor processor

    def setup() {
        ResourceStore store = Stub()
        store.put(_ as Path, _ as InputStream) >> { Path p, InputStream content -> files[p] = content.readAllBytes() }
        store.content(_ as Path) >> { Path p ->
            files.containsKey(p) ? Optional.of(new ByteArrayInputStream(files[p])) : Optional.empty()
        }
        ResourceStore root = Stub()
        root.with(*_) >> store

        AppContext appContext = Stub() {
            getTmpDir() >> tmpDir
            getPathPrefix() >> []
        }

        FeaturesCoreConfiguration coreConfiguration = Stub() {
            getDefaultEpsgCrs() >> OgcCrs.CRS84
        }
        apiData.getExtension(FeaturesCoreConfiguration) >> Optional.of(coreConfiguration)
        apiData.getExtension(SearchConfiguration) >> Optional.empty()

        EntityRegistry entities = Stub() {
            getEntity(OgcApi, 'demo') >> Optional.of(api)
            getEntity(OgcApi, _) >> Optional.empty()
        }

        SearchQueriesHandler queryHandler = Stub() {
            handle(Query.QUERY, _, _) >> { query, input, ApiRequestContext context ->
                queries++
                requestContext = context
                response
            }
        }

        processor = new SearchJobProcessor(appContext, entities, root, Stub(FeaturesCoreProviders), queryHandler,
                extensionRegistry, repository, Stub(SchemaValidator), new CqlImpl())
    }

    def "the inputs of a job of another kind are not read as those of a search"() {
        when:
        SearchJob inputs = processor.getInputs(job([:]), jobs)

        then:
        inputs.getApiId() == ''
        inputs.getQueryId() == null
    }

    def "a step runs the stored query of the file in queryPath and keeps the outputs of the earlier steps"() {
        given:
        marker([f: 'xml', beginn: '2026-10-01T00:00:00Z'])
        def earlier = [queryPath: queryPath, deliveryId: [value: 'd1', href: '', kind: '']]

        when:
        def result = execute(earlier)

        then:
        result.status() == Identifiers.Result.SUCCESS
        queries == 1
        requestContext.getUriCustomizer().getPath() == '/demo/search/changes'
        requestContext.getMediaType().parameter() == 'xml'

        and: 'the result is stored without a marker for the finalize phase of search jobs'
        files.keySet() == [Path.of(QUERY_PATH), Path.of('demo/result_job-1.xml')] as Set
        new String(files[Path.of('demo/result_job-1.xml')], StandardCharsets.UTF_8) == FEATURES

        and: 'the outputs are those of the earlier steps and the result of the search, not as a result file'
        jobs.outputs == earlier + [searchResultPath: 'demo/result_job-1.xml', searchResultMediaType: 'application/gml+xml']
        jobs.updates == [1]

        where:
        queryPath << [[value: QUERY_PATH, href: '', kind: ''], QUERY_PATH]
    }

    def "a step without the output queryPath fails"() {
        when:
        def result = execute([deliveryId: 'd1'])

        then:
        result.status() == Identifiers.Result.FAILURE
        result.messages() == ["The job has no output 'queryPath' with the path of the query to execute"]
        queries == 0
    }

    def "a query path outside of the store is rejected"() {
        when:
        def result = execute([queryPath: queryPath])

        then:
        result.status() == Identifiers.Result.FAILURE
        result.messages() == ["The path of the query '${queryPath}' is outside of the store".toString()]
        queries == 0

        where:
        queryPath << ['../other/job.json', '/etc/job.json', 'changes/../../job.json']
    }

    def "a missing query file fails the step"() {
        when:
        def result = execute([queryPath: QUERY_PATH])

        then:
        result.status() == Identifiers.Result.FAILURE
        result.messages() == ["The query '${QUERY_PATH}' does not exist".toString()]
    }

    def "a query file that is not JSON fails the step"() {
        given:
        files[Path.of(QUERY_PATH)] = 'not json {'.getBytes(StandardCharsets.UTF_8)

        when:
        def result = execute([queryPath: QUERY_PATH])

        then:
        result.status() == Identifiers.Result.FAILURE
        result.messages() == ["The query '${QUERY_PATH}' is not valid JSON".toString()]
    }

    def "invalid parameters in the query file fail the step with the message of the parameter"() {
        given:
        marker([f: 'xml', beginn: 'yesterday'])

        when:
        def result = execute([queryPath: QUERY_PATH])

        then:
        result.status() == Identifiers.Result.FAILURE
        result.messages().any { it.contains("Invalid value 'yesterday' for 'beginn'.") }
        queries == 0
    }

    def "a failed query of a step adds the reason to the outputs of the earlier steps"() {
        given:
        marker([f: 'xml'])
        response = Response.status(400).entity("bad query").build()

        when:
        def result = execute([queryPath: QUERY_PATH, deliveryId: 'd1'])

        then:
        result.status() == Identifiers.Result.FAILURE
        jobs.outputs == JobFailure.of(400, "Query failed with status 400").addTo([queryPath: QUERY_PATH, deliveryId: 'd1'])
        jobs.updates.isEmpty()
    }

    // --- fixtures ------------------------------------------------------------------------------

    /** The document of an earlier step with the call of the stored query. */
    private void marker(Map<String, String> parameters) {
        def json = groovy.json.JsonOutput.toJson([
                id       : 'd1',
                recipient: 'r',
                ticket   : 'd1/ticket.json',
                priority : 1,
                query    : [api: 'demo', queryId: 'changes', parameters: parameters],
                submitted: '2026-11-02T00:00:00Z'])
        files[Path.of(QUERY_PATH)] = json.getBytes(StandardCharsets.UTF_8)
    }

    private def execute(Map<String, Object> outputs) {
        Job job = job(outputs)
        processor.execute(partialJob(), job, processor.getInputs(job, jobs), jobs)
    }

    private static Job job(Map<String, Object> outputs) {
        new Job("job-1", "oap:Delivery", null, null, null, null, null, null, null, [], [:], null, null,
                [recipient: 'r'], outputs, Optional.empty(), Optional.empty(), [], [], Optional.empty(), Optional.empty())
    }

    private static PartialJob partialJob() {
        new PartialJob("partial-1", SearchJob.KIND + ":execute", null, null, null, null, null, null, null, [], [:],
                "job-1", null, null, [], Optional.empty())
    }

    private static StreamingOutput streaming(String content) {
        { OutputStream out -> out.write(content.getBytes(StandardCharsets.UTF_8)) } as StreamingOutput
    }
}
