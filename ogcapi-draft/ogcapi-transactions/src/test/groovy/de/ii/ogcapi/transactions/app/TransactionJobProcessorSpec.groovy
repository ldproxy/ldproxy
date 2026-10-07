/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app

import de.ii.ogcapi.foundation.domain.OgcApi
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.transactions.app.CommandHandlerTransactions.QueryInputTransaction
import de.ii.ogcapi.transactions.domain.ImmutableTransactionJob
import de.ii.ogcapi.transactions.domain.TransactionJob
import de.ii.ogcapi.transactions.domain.TransactionsConfiguration
import de.ii.ogcapi.foundation.domain.HeaderPrefer
import de.ii.xtralink.jobs.BaseJob
import de.ii.xtralink.jobs.Identifiers
import de.ii.xtralink.jobs.Job
import de.ii.xtralink.jobs.PartialJob
import de.ii.xtralink.jobs.PartialJobConfiguration
import de.ii.xtraplatform.base.domain.AppContext
import de.ii.xtraplatform.blobs.domain.ResourceStore
import de.ii.xtraplatform.entities.domain.EntityRegistry
import de.ii.xtraplatform.xtralink.domain.JobContext
import de.ii.xtraplatform.xtralink.domain.JobInputs
import de.ii.xtraplatform.xtralink.domain.JobProcessing
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.function.BiPredicate
import java.util.zip.GZIPOutputStream
import spock.lang.Specification

class TransactionJobProcessorSpec extends Specification {

    static final String WFS = '<wfs:Transaction xmlns:wfs="http://www.opengis.net/wfs/2.0"/>'

    /** The resource store below jobs/feature-transaction. */
    Map<Path, byte[]> files = [:]

    /** Folders exist as entries of their own, as in a file system; false for an object store. */
    boolean folderEntries = true

    /** The documents handed to the transaction handler, decompressed. */
    List<String> applied = []

    /** The status of each subsequent transaction; 200 when the list is exhausted. */
    List<Integer> statuses = []

    RecordingJobs jobs = new RecordingJobs()
    TransactionJobProcessor processor

    def setup() {
        ResourceStore store = Stub()
        store.size(_ as Path) >> { Path p -> files.containsKey(p) ? (long) files[p].length : -1L }
        store.has(_ as Path) >> { Path p -> files.containsKey(p) || (folderEntries && files.keySet().any { it.parent == p }) }
        store.content(_ as Path) >> { Path p -> files.containsKey(p) ? Optional.of(new ByteArrayInputStream(files[p])) : Optional.empty() }
        store.walk(_ as Path, _ as Integer, _ as BiPredicate) >> { Path p, Integer depth, BiPredicate matcher ->
            files.keySet().findAll { it.parent == p }.collect { p.relativize(it) }.stream()
        }
        store.put(_ as Path, _ as InputStream) >> { Path p, InputStream content -> files[p] = content.readAllBytes() }
        ResourceStore root = Stub()
        root.with(*_) >> store

        TransactionsConfiguration configuration = Stub()
        configuration.isEnabled() >> true
        OgcApiDataV2 data = Stub()
        data.getExtension(TransactionsConfiguration) >> Optional.of(configuration)
        OgcApi api = Stub()
        api.getId() >> "api"
        api.getUri() >> URI.create("http://localhost/api")
        api.getData() >> data
        EntityRegistry entities = Stub()
        entities.getEntity(OgcApi, "api") >> Optional.of(api)

        QueryInputTransaction queryInput = Stub()
        queryInput.getContentType() >> MediaType.APPLICATION_XML_TYPE
        TransactionInputs inputs = Stub()
        inputs.createQueryInput(*_) >> { args ->
            applied << new String(((InputStream) args[6]).readAllBytes(), StandardCharsets.UTF_8)
            queryInput
        }

        CommandHandlerTransactions handler = Stub()
        handler.processTransaction(_, _) >> {
            int status = statuses.isEmpty() ? 200 : statuses.remove(0)
            response(status, """{"summary":{"document":${applied.size()}}}""")
        }

        processor = new TransactionJobProcessor(Stub(AppContext), entities, root, inputs, handler)
    }

    def "a single document is applied and its result is written to the result file of the job"() {
        given:
        files[Path.of("api/d1/001.xml")] = bytes(WFS)

        when:
        def result = execute("api/d1/001.xml")

        then:
        result.status() == Identifiers.Result.SUCCESS
        applied == [WFS]
        text("api/result_job-1.json") == '{"summary":{"document":1}}'
        jobs.outputs == [resultPath: "api/result_job-1.json"]
        jobs.updates == [1]
    }

    def "a compressed document is decompressed"() {
        given:
        files[Path.of("api/d1/001.xml.gz")] = gzip(WFS)

        when:
        def result = execute("api/d1/001.xml.gz")

        then:
        result.status() == Identifiers.Result.SUCCESS
        applied == [WFS]
    }

    def "the result of a rejected document is written as well"() {
        given:
        files[Path.of("api/d1/001.xml")] = bytes(WFS)
        statuses << 422

        when:
        def result = execute("api/d1/001.xml")

        then:
        result.status() == Identifiers.Result.FAILURE
        result.messages() == ["Transaction failed with status 422, see api/result_job-1.json"]
        text("api/result_job-1.json") == '{"summary":{"document":1}}'
        jobs.outputs == [resultPath: "api/result_job-1.json"]
        jobs.updates.isEmpty()
    }

    def "without result file, a rejection reports the response document in the message"() {
        given:
        files[Path.of("api/d1/001.xml")] = bytes(WFS)
        statuses << 422

        when:
        def result = execute("api/d1/001.xml", false)

        then:
        result.messages() == ['Transaction failed with status 422: {"summary":{"document":1}}']
        !files.containsKey(Path.of("api/result_job-1.json"))
        jobs.outputs == null
    }

    def "the documents of a folder are applied in name order and their results are written to the result folder of the job"() {
        given:
        files[Path.of("api/d1/002.xml.gz")] = gzip("<two/>")
        files[Path.of("api/d1/001.xml")] = bytes("<one/>")
        files[Path.of("api/d1/meta.json")] = bytes("{}")
        files[Path.of("api/d1/notes.txt")] = bytes("ignored")
        files[Path.of("api/d1/.hidden.xml")] = bytes("<hidden/>")

        when:
        def result = execute("api/d1")

        then:
        result.status() == Identifiers.Result.SUCCESS
        applied == ["<one/>", "<two/>"]
        text("api/result_job-1/001.result.json") == '{"summary":{"document":1}}'
        text("api/result_job-1/002.result.json") == '{"summary":{"document":2}}'
        jobs.outputs == [resultPaths: ["api/result_job-1/001.result.json", "api/result_job-1/002.result.json"]]
        files.keySet().findAll { it.startsWith("api/d1") }.size() == 5
        jobs.updates == [1]
    }

    def "the first rejected document of a folder stops the job"() {
        given:
        files[Path.of("api/d1/001.xml")] = bytes("<one/>")
        files[Path.of("api/d1/002.xml")] = bytes("<two/>")
        files[Path.of("api/d1/003.xml")] = bytes("<three/>")
        statuses.addAll([200, 422])

        when:
        def result = execute("api/d1")

        then:
        result.status() == Identifiers.Result.FAILURE
        result.messages() == ["Transaction api/d1/002.xml failed with status 422, see api/result_job-1/002.result.json"]
        applied == ["<one/>", "<two/>"]
        jobs.outputs == [resultPaths: ["api/result_job-1/001.result.json", "api/result_job-1/002.result.json"]]
        !files.containsKey(Path.of("api/result_job-1/003.result.json"))
        jobs.updates.isEmpty()
    }

    def "a folder without documents has nothing to apply: #store"() {
        given:
        folderEntries = entries
        files[Path.of("api/d1/meta.json")] = bytes("{}")
        files[Path.of("api/d1/outcome.json")] = bytes('{"status":"rejected"}')

        when:
        def result = execute("api/d1")

        then:
        result.status() == Identifiers.Result.SUCCESS
        applied.isEmpty()
        jobs.outputs == [resultPaths: []]
        jobs.updates == [1]

        where:
        store            | entries
        "file system"    | true
        "object store"   | false
    }

    def "the media type selects the documents of a folder"() {
        given:
        files[Path.of("api/d1/001.json")] = bytes('{"one":1}')
        files[Path.of("api/d1/002.json.gz")] = gzip('{"two":2}')
        files[Path.of("api/d1/003.xml")] = bytes("<three/>")

        when:
        execute("api/d1", true, "application/json")

        then:
        applied == ['{"one":1}', '{"two":2}']
    }

    def "a missing document or folder is reported"() {
        given:
        files[Path.of("api/d2/001.xml")] = bytes(WFS)

        when:
        def result = execute("api/d1")

        then:
        result.status() == Identifiers.Result.FAILURE
        result.messages() == ["Transaction document not found: api/d1"]
        applied.isEmpty()
    }

    def "a missing document path is reported as an invalid input"() {
        when:
        def result = execute("")

        then:
        result.status() == Identifiers.Result.FAILURE
        result.messages() == ["Document path must not be null or empty"]
    }

    // --- fixtures ------------------------------------------------------------------------------

    private def execute(String documentPath, boolean resultAsFile = true, String mediaType = "application/xml") {
        TransactionJob inputs = new ImmutableTransactionJob.Builder()
                .apiId("api")
                .documentPath(documentPath)
                .mediaType(mediaType)
                .handlingPrefer(HeaderPrefer.Handling.STRICT)
                .returnPrefer(HeaderPrefer.Return.MINIMAL)
                .resultAsFile(resultAsFile)
                .build()
        processor.execute(partialJob(), job(), inputs, jobs)
    }

    private static Job job() {
        new Job("job-1", TransactionJob.KIND, null, null, null, null, null, null, null, [], [:], null, null, [:], [:],
                Optional.empty(), Optional.empty(), [], Optional.empty(), Optional.empty())
    }

    private static PartialJob partialJob() {
        new PartialJob("partial-1", TransactionJob.KIND + ":execute", null, null, null, null, null, null, null, [], [:],
                "job-1", null, null, [], Optional.empty())
    }

    private Response response(int status, String body) {
        Response response = Stub()
        response.getStatus() >> status
        response.getEntity() >> body
        response
    }

    private String text(String path) {
        new String(files[Path.of(path)], StandardCharsets.UTF_8)
    }

    private static byte[] bytes(String text) {
        text.getBytes(StandardCharsets.UTF_8)
    }

    private static byte[] gzip(String text) {
        def out = new ByteArrayOutputStream()
        new GZIPOutputStream(out).withCloseable { it.write(bytes(text)) }
        out.toByteArray()
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
    }
}
