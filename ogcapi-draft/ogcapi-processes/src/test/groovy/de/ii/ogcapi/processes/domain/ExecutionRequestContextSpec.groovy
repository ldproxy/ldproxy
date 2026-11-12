/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain

import com.fasterxml.jackson.databind.ObjectMapper
import de.ii.ogcapi.processes.domain.model.ImmutableOgcExecute
import de.ii.ogcapi.processes.domain.model.ImmutableProcessData
import de.ii.ogcapi.processes.domain.model.OgcProcessSummary.JobControlOptions
import spock.lang.Specification

class ExecutionRequestContextSpec extends Specification {

    static final ObjectMapper MAPPER = new ObjectMapper()

    static def process() {
        new ImmutableProcessData.Builder().id('p').version('1.0.0').jobControlOptions([JobControlOptions.SYNC_EXECUTE]).build()
    }

    static def execute() {
        new ImmutableOgcExecute.Builder().inputs([a: 1]).build()
    }

    def 'the job carries the request context of the execution to its steps'() {
        when:
        def job = OapJob.of('api', process(), execute(), ExecutionRequestContext.ofProfiles(['rel-as-key-title']), [a: 1], 60)

        then:
        ExecutionRequestContext.from(job.context(), MAPPER).profiles == ['rel-as-key-title']
        job.inputs() == [a: 1]
    }

    def 'a job without a request context has none'() {
        when:
        def job = OapJob.of('api', process(), execute(), [a: 1], 60)

        then:
        ExecutionRequestContext.from(job.context(), MAPPER) == ExecutionRequestContext.NONE
        ExecutionRequestContext.from([:], MAPPER).profiles.isEmpty()
    }
}
