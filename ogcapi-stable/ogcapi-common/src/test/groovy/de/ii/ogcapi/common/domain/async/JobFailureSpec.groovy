/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain.async

import spock.lang.Specification

class JobFailureSpec extends Specification {

    def "the reason for the failure of a job is a client or an upstream error, not: #status"() {
        when:
        JobFailure.of(status, "The input is invalid.")

        then:
        IllegalStateException e = thrown()
        e.message == "The reason for the failure of a job is a client error (4xx) or an upstream error (502, 503, 504), found status ${status}.".toString()

        where:
        status << [200, 399, 500, 501, 505]
    }

    def "an upstream error is a stated reason: #status"() {
        expect:
        JobFailure.from(JobFailure.of(status, "The external API cannot be reached.").asOutputs()).get().status == status

        where:
        status << [502, 503, 504]
    }

    def "a stated reason is read from the job outputs"() {
        given:
        Map<String, Object> outputs = JobFailure.of(422, "urn:example:invalid", "The input is invalid.")
                .addTo([deliveryId: "d1"])

        expect:
        JobFailure.from(outputs) == Optional.of(JobFailure.of(422, "urn:example:invalid", "The input is invalid."))
    }

    def "a status of no client or upstream error in the job outputs is no stated reason: #status"() {
        expect: 'as written by a processor outside of ldproxy'
        JobFailure.from([failure: [status: status, detail: "The input is invalid."]]).isEmpty()

        where:
        status << [200, 500, 501]
    }
}
