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

    def "a callback '#callback' below the allowed URI '#allowed' is accepted"() {
        expect:
        ExecutionQueriesHandlerImpl.isAllowedCallbackUri(callback, [allowed])

        where:
        allowed                          | callback
        "https://example.com/callbacks/" | "https://example.com/callbacks/done"
        "https://example.com/callbacks"  | "https://example.com/callbacks/done"
        "https://example.com/callbacks"  | "https://example.com/callbacks"
        "https://example.com"            | "https://example.com/any/path?job=1"
        "https://example.com/"           | "https://EXAMPLE.com:443/done"
        "http://localhost:8765/"         | "http://localhost:8765/progress"
        "https://example.com/callbacks/" | "https://user:secret@example.com/callbacks/done"
        "https://example.com/callbacks/" | "https://example.com/other/../callbacks/done"
    }

    def "a callback '#callback' that is not below the allowed URI '#allowed' is rejected"() {
        expect:
        !ExecutionQueriesHandlerImpl.isAllowedCallbackUri(callback, [allowed])

        where:
        allowed                          | callback
        "https://example.com/callbacks/" | "https://example.com/other"
        "https://example.com/callbacks"  | "https://example.com/callbacks-other/done"
        "https://example.com/callbacks/" | "https://example.com/callbacks/../admin"
        "https://example.com/callbacks/" | "https://example.com/callbacks/%2e%2e/admin"
        "https://example.com/callbacks/" | "https://example.com/callbacks%2F..%2Fadmin"
        "https://example.com/"           | "http://example.com/"
        "https://example.com/"           | "https://example.com:8443/"
        "https://example.com/"           | "https://example.com.example.org/"
        "https://example.com/"           | "https://example.com@example.org/"
        "https://example.com/"           | "http://localhost:7081/api/tasks/log-level"
        "example.com"                    | "https://example.com/"
        "https://"                       | "https://example.com/"
    }

    def "the wildcard accepts the callback '#callback'"() {
        expect:
        ExecutionQueriesHandlerImpl.isAllowedCallbackUri(callback, ["*"])

        where:
        callback << ["https://example.com/callbacks/done", "http://localhost:8765/progress", "http://10.0.0.5:7081/api/tasks/log-level"]
    }

    def "the wildcard does not accept the callback '#callback' that is not an HTTP URI"() {
        expect:
        !ExecutionQueriesHandlerImpl.isAllowedCallbackUri(callback, ["*"])

        where:
        callback << ["ftp://example.com/callback", "mailto:someone@example.com", "relative/path", "not a uri"]
    }

    def "an asynchronous execution with a subscriber is rejected, if the API sends no callbacks"() {
        when:
        ExecutionQueriesHandlerImpl.validateCallbacks(execute(null, "https://example.com/callbacks/done"), [])

        then:
        thrown(IllegalArgumentException)
    }

    def "an asynchronous execution without a callback is accepted, also if the API sends no callbacks"() {
        when:
        ExecutionQueriesHandlerImpl.validateCallbacks(request, [])

        then:
        noExceptionThrown()

        where:
        request << [execute(null, null), new ImmutableOgcExecute.Builder().subscriber(new ImmutableAsyncSubscriber.Builder().build()).build()]
    }

    def "a subscriber whose callbacks are all allowed is accepted"() {
        when:
        ExecutionQueriesHandlerImpl.validateCallbacks(subscribed("https://example.com/callbacks/failed"),
                ["https://example.com/callbacks/"])

        then:
        noExceptionThrown()
    }

    def "a subscriber with one callback that is not allowed is rejected"() {
        when:
        ExecutionQueriesHandlerImpl.validateCallbacks(subscribed("http://localhost:7081/api/tasks/log-level"),
                ["https://example.com/callbacks/"])

        then:
        thrown(IllegalArgumentException)
    }

    private static def subscribed(String failedUri) {
        def subscriber = new ImmutableAsyncSubscriber.Builder()
                .successUri("https://example.com/callbacks/done")
                .failedUri(failedUri)
                .build()
        new ImmutableOgcExecute.Builder().subscriber(subscriber).build()
    }

    private static def execute(String process, String callback) {
        def builder = new ImmutableOgcExecute.Builder().process(Optional.ofNullable(process))
        if (callback != null) {
            builder.subscriber(new ImmutableAsyncSubscriber.Builder().inProgressUri(callback).build())
        }
        builder.build()
    }
}
