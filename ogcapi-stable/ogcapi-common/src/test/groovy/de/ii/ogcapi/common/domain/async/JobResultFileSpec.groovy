/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain.async

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Path
import spock.lang.Specification

class JobResultFileSpec extends Specification {

    static final ObjectMapper MAPPER = new ObjectMapper()

    def "a result file persisted as job outputs is recognized and restored"() {
        given:
        JobResultFile resultFile = JobResultFile.of("api/result_job-1.json", "application/geo+json")

        when:
        Map<String, Object> outputs = MAPPER.readValue(MAPPER.writeValueAsBytes(resultFile), Map)

        then:
        JobResultFile.is(outputs)
        JobResultFile.from(outputs) == Optional.of(resultFile)
    }

    def "the status of a result file persisted as job outputs is restored: #resultFile.resultStatus"() {
        when:
        Map<String, Object> outputs = MAPPER.readValue(MAPPER.writeValueAsBytes(resultFile), Map)

        then:
        outputs.containsKey("resultStatus") == (resultFile.resultStatus != null)
        JobResultFile.from(outputs) == Optional.of(resultFile)
        JobResultFile.from(outputs).get().status() == status

        where:
        resultFile                                                   || status
        JobResultFile.of("api/result_job-1.json", "application/json")      || 200
        JobResultFile.of("api/result_job-1.json", "application/json", 422) || 422
    }

    def "outputs without a result file are not recognized: #outputs"() {
        expect:
        !JobResultFile.is(outputs)
        JobResultFile.from(outputs).isEmpty()

        where:
        outputs << [
                [:],
                [resultPath: "api/result_job-1.json"],
                [resultMediaType: "application/json"],
                [resultPath: 1, resultMediaType: "application/json"],
                [resultPath: "api/result_job-1.json", resultMediaType: null],
                [resultPaths: ["api/result_job-1/001.result.json"]],
        ]
    }

    def "the path is relative to the job store"() {
        expect:
        JobResultFile.of("api/result_job-1.json", "application/json").path() == Path.of("api", "result_job-1.json")
    }
}
