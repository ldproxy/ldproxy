/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app

import spock.lang.Specification

class WorkflowSlotsSpec extends Specification {

    def "one job stays free for the processes: with #maxConcurrent jobs, #allowed workflows run"() {
        given:
        def slots = new WorkflowSlotsImpl(maxConcurrent)

        when:
        def results = (1..5).collect { slots.enterWorkflow() }

        then:
        results.count { it.isEmpty() } == allowed
        results.findAll { it.isPresent() }.every { it.get().contains("Retry later") }

        where:
        maxConcurrent || allowed
        4             || 3
        2             || 1
        1             || 1
    }

    def "a workflow that ended releases its slot"() {
        given:
        def slots = new WorkflowSlotsImpl(2)
        slots.enterWorkflow()

        expect:
        slots.enterWorkflow().isPresent()

        when:
        slots.exitWorkflow()

        then:
        slots.enterWorkflow().isEmpty()
    }

    def "with a single job, a step cannot execute a process"() {
        expect:
        new WorkflowSlotsImpl(1).executeProcess("aggregate-features").get().contains("at least 2 are required")
        new WorkflowSlotsImpl(2).executeProcess("aggregate-features").isEmpty()
    }
}
