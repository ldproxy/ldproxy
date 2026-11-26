/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import de.ii.ogcapi.processes.domain.OapJob
import de.ii.ogcapi.processes.domain.model.ImmutableProcessJobOptions
import de.ii.ogcapi.processes.domain.model.ProcessData
import de.ii.ogcapi.processes.domain.model.ProcessJobOptions
import de.ii.xtraplatform.xtralink.domain.JobProcessorSimple
import spock.lang.Specification

class ProcessJobOptionsSpec extends Specification {

    static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory()).findAndRegisterModules()

    static ProcessJobOptions options(String yaml) {
        YAML.readValue(yaml, ProcessJobOptions)
    }

    def "an empty job object yields the defaults"() {
        when:
        def job = options("{}")

        then:
        !job.setup
        job.partials == ["execute"]
        !job.sequential
        !job.cleanup
    }

    def "configured partials replace the default instead of being added to it"() {
        when:
        def job = options("partials: [transform]")

        then:
        job.partials == ["transform"]
    }

    def "all options are read from YAML"() {
        when:
        def job = options("""\
            setup: true
            partials: [transform, execute]
            sequential: true
            cleanup: true
            """.stripIndent())

        then:
        job.setup
        job.partials == ["transform", "execute"]
        job.sequential
        job.cleanup
    }

    def "an empty list of partials in YAML is rejected"() {
        when:
        options("partials: []")

        then:
        def e = thrown(Exception)
        rootCause(e) instanceof IllegalStateException
    }

    def "a process description without job gets the default job options"() {
        when:
        def process = YAML.readValue("""\
            id: TestProcess
            version: 1.0.0
            jobControlOptions: [SYNC_EXECUTE]
            """.stripIndent(), ProcessData)

        then:
        process.job.partials == ["execute"]
        !process.job.setup && !process.job.cleanup && !process.job.sequential
    }

    def "a process description reads the nested job options"() {
        when:
        def process = YAML.readValue("""\
            id: TestProcess
            version: 1.0.0
            jobControlOptions: [ASYNC_EXECUTE]
            job:
              setup: true
              partials: [transform, execute]
            """.stripIndent(), ProcessData)

        then:
        process.job.setup
        process.job.partials == ["transform", "execute"]
    }

    // what the processors of ldproxy (JobProcessorSimple) make of the qualified partial kinds

    def "JobProcessorSimple only knows the phases setup, execute, cleanup and finalize"() {
        when:
        JobProcessorSimple.Phase.fromPartialKind(OapJob.kind("TestProcess", "transform"))

        then:
        thrown(IllegalArgumentException)
    }

    def "a partial named like a phase is processed as that phase by JobProcessorSimple"() {
        expect:
        JobProcessorSimple.Phase.fromPartialKind(OapJob.kind("TestProcess", partial)) == phase

        where:
        partial    | phase
        "setup"    | JobProcessorSimple.Phase.SETUP
        "cleanup"  | JobProcessorSimple.Phase.CLEANUP
        "finalize" | JobProcessorSimple.Phase.FINALIZE
    }

    // partials with these names would be processed as the phases of the job, so they are rejected

    def "a partial with the name of a phase of every job is rejected"() {
        when:
        new ImmutableProcessJobOptions.Builder().partials(["execute", partial]).build()

        then:
        def e = thrown(IllegalStateException)
        e.message.startsWith("'${partial}' cannot be a partial of a process job")

        where:
        partial << ["setup", "cleanup", "finalize"]
    }

    def "a blank partial is rejected"() {
        when:
        new ImmutableProcessJobOptions.Builder().partials([partial]).build()

        then:
        def e = thrown(IllegalStateException)
        e.message == "The partials of a process job must not be blank."

        where:
        partial << ["", " "]
    }

    def "a reserved partial in YAML is rejected"() {
        when:
        options("partials: [execute, cleanup]")

        then:
        def e = thrown(Exception)
        rootCause(e) instanceof IllegalStateException
    }

    private static Throwable rootCause(Throwable e) {
        Throwable cause = e
        while (cause.cause != null && cause.cause != cause) {
            cause = cause.cause
        }
        cause
    }
}
