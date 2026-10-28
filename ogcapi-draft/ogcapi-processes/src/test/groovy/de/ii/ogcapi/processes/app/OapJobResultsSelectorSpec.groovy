/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app

import static de.ii.ogcapi.processes.app.ProcessFixtures.output
import static de.ii.ogcapi.processes.app.ProcessFixtures.process
import static de.ii.ogcapi.processes.app.ProcessFixtures.schema
import static de.ii.ogcapi.processes.domain.model.OgcProcessSummary.JobControlOptions.ASYNC_EXECUTE

import de.ii.ogcapi.foundation.domain.TypedClientErrorException
import de.ii.ogcapi.processes.domain.OapJob
import de.ii.ogcapi.processes.domain.OapJob.OapJobContext
import de.ii.ogcapi.processes.domain.model.ImmutableOgcExecute
import de.ii.ogcapi.processes.domain.model.ImmutableOgcFormat
import de.ii.ogcapi.processes.domain.model.OgcExecute
import de.ii.ogcapi.processes.domain.model.OgcFormat
import de.ii.ogcapi.processes.domain.model.OgcProcess
import de.ii.ogcapi.processes.domain.model.ProcessRepository
import de.ii.ogcapi.processes.domain.model.Property.Type
import de.ii.xtraplatform.base.domain.Jackson
import de.ii.xtraplatform.xtralink.domain.Jobs
import spock.lang.Specification

class OapJobResultsSelectorSpec extends Specification {

    static final String NO_SUCH_OUTPUT = "https://www.opengis.net/def/exceptions/ogcapi-processes-1/1.0/no-such-output"
    static final String RESULT_NOT_AVAILABLE = "https://www.opengis.net/def/exceptions/ogcapi-processes-1/1.0/result-not-available"
    static final String KIND = OapJob.kind("TestProcess")

    static final Map<String, Object> RESULTS = [single: "a", multi: [1, 2, 3]]

    OgcProcess testProcess = process([ASYNC_EXECUTE], [:],
            [single: output(schema(Type.STRING)), multi: output(schema(Type.INTEGER), 10)])

    OapJobResultsSelector selector

    def setup() {
        ProcessRepository repository = Stub() {
            getDirect("api", "TestProcess") >> testProcess
        }
        Jackson jackson = Stub() {
            getDefaultObjectMapper() >> Jobs.DEFAULT_MAPPER
        }
        selector = new OapJobResultsSelector(repository, jackson)
    }

    def "omitted outputs select all results"() {
        expect:
        OapJobResultsSelector.selectOutputs(RESULTS, Optional.empty()) == RESULTS
    }

    def "empty outputs select no results"() {
        expect:
        OapJobResultsSelector.selectOutputs(RESULTS, Optional.of([:])).isEmpty()
    }

    def "selected outputs select only these results"() {
        expect:
        OapJobResultsSelector.selectOutputs(RESULTS, selection("multi")) == [multi: [1, 2, 3]]
    }

    def "selecting an output that was not produced is not available"() {
        when:
        OapJobResultsSelector.selectOutputs([single: "a"], selection("multi"))

        then:
        TypedClientErrorException e = thrown()
        e.response.status == 404
        e.type == RESULT_NOT_AVAILABLE
    }

    def "selecting an output the process does not define is a bad request"() {
        when:
        OapJobResultsSelector.validateOutputSelections(testProcess, selection("unknown"))

        then:
        TypedClientErrorException e = thrown()
        e.response.status == 400
        e.type == NO_SUCH_OUTPUT
    }

    def "a single result honors the output selection of the execute request"() {
        expect:
        selector.selectSpecificResult(RESULTS, context(execute(selection("single"))), KIND, "single") == "a"
    }

    def "a single result that was not requested is not available"() {
        when:
        selector.selectSpecificResult(RESULTS, context(execute(selection("single"))), KIND, "multi")

        then:
        TypedClientErrorException e = thrown()
        e.response.status == 404
        e.type == RESULT_NOT_AVAILABLE
    }

    def "value #index of a multi-valued result is #value"() {
        expect:
        selector.selectSpecificResultN(RESULTS, context(execute()), KIND, "multi", index) == value

        where:
        index || value
        0     || 1
        2     || 3
    }

    def "value #index of a multi-valued result is out of bounds"() {
        when:
        selector.selectSpecificResultN(RESULTS, context(execute()), KIND, "multi", index)

        then:
        thrown(IllegalArgumentException)

        where:
        index << [3, 10]
    }

    def "a single-valued result has no indexed values"() {
        when:
        selector.selectSpecificResultN(RESULTS, context(execute()), KIND, "single", 0)

        then:
        thrown(IllegalArgumentException)
    }

    private static Optional<Map<String, OgcFormat>> selection(String... outputIds) {
        Optional.of(outputIds.collectEntries { [(it): new ImmutableOgcFormat.Builder().build()] })
    }

    private static OgcExecute execute(Optional<Map<String, OgcFormat>> outputSelections = Optional.empty()) {
        new ImmutableOgcExecute.Builder().outputSelections(outputSelections).build()
    }

    private static Map<String, Object> context(OgcExecute executeRequest) {
        def context = new OapJobContext("api", executeRequest, "TestProcess")
        Jobs.DEFAULT_MAPPER.readValue(Jobs.DEFAULT_MAPPER.writeValueAsBytes(context), Jobs.MAP_TYPE)
    }
}
