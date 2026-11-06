/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app

import static de.ii.ogcapi.processes.app.ProcessFixtures.process
import static de.ii.ogcapi.processes.domain.model.OgcProcessSummary.JobControlOptions.ASYNC_EXECUTE
import static de.ii.ogcapi.processes.domain.model.OgcProcessSummary.JobControlOptions.SYNC_EXECUTE

import spock.lang.Specification

class ExecutionModeSpec extends Specification {

    def "a process with #options is executed async: #async (async supported: #asyncSupported, prefer async: #preferAsync)"() {
        expect:
        ExecutionQueriesHandlerImpl.isExecutedAsync(process(options), asyncSupported, preferAsync) == async

        where:
        options                       | asyncSupported | preferAsync || async
        [SYNC_EXECUTE]                | true           | false       || false
        [SYNC_EXECUTE]                | true           | true        || false
        [ASYNC_EXECUTE]               | true           | false       || true
        [ASYNC_EXECUTE]               | true           | true        || true
        [SYNC_EXECUTE, ASYNC_EXECUTE] | true           | false       || false
        [SYNC_EXECUTE, ASYNC_EXECUTE] | true           | true        || true
        [SYNC_EXECUTE, ASYNC_EXECUTE] | false          | true        || false
        []                            | true           | true        || false
    }

    def "an async-only process cannot be executed if async is not supported"() {
        when:
        ExecutionQueriesHandlerImpl.isExecutedAsync(process([ASYNC_EXECUTE]), false, true)

        then:
        thrown(IllegalArgumentException)
    }
}
