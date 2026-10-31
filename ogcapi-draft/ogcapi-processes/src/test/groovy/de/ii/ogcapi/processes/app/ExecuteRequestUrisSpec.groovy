/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app

import de.ii.ogcapi.common.domain.async.ImmutableAsyncSubscriber
import de.ii.ogcapi.processes.domain.model.ImmutableOgcExecute
import spock.lang.Specification

class ExecuteRequestUrisSpec extends Specification {

    def "a process URI '#process' and a callback URI '#callback' are accepted"() {
        when:
        ExecutionQueriesHandlerImpl.validateUris(execute(process, callback))

        then:
        noExceptionThrown()

        where:
        process                                       | callback
        null                                          | null
        "https://example.org/api/processes/Echo"      | "http://127.0.0.1:8765/progress"
        "urn:example:process"                         | "https://example.org/callbacks/failed"
    }

    def "a process '#process' that is not a URI is rejected"() {
        when:
        ExecutionQueriesHandlerImpl.validateUris(execute(process, null))

        then:
        thrown(IllegalArgumentException)

        where:
        process << ["", "EchoProcess", "not a uri"]
    }

    def "a callback '#callback' that is not an HTTP URI is rejected"() {
        when:
        ExecutionQueriesHandlerImpl.validateUris(execute(null, callback))

        then:
        thrown(IllegalArgumentException)

        where:
        callback << ["", "relative/path", "ftp://example.org/callback", "mailto:someone@example.org", "http:///no-host"]
    }

    private static def execute(String process, String callback) {
        def builder = new ImmutableOgcExecute.Builder().process(Optional.ofNullable(process))
        if (callback != null) {
            builder.subscriber(new ImmutableAsyncSubscriber.Builder().inProgressUri(callback).build())
        }
        builder.build()
    }
}
