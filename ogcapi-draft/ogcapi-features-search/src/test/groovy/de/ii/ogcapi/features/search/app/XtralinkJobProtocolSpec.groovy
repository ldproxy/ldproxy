/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app

import de.ii.xtralink.jobs.Identifiers
import de.ii.xtralink.jobs.InitProgress
import de.ii.xtralink.jobs.Job
import de.ii.xtralink.jobs.JobConfiguration
import de.ii.xtralink.jobs.JobProgress
import de.ii.xtralink.jobs.JobResult
import de.ii.xtralink.jobs.PartialJobConfiguration
import de.ii.xtralink.jobs.QueueConfiguration
import de.ii.xtralink.jobs.internal.JobListener
import de.ii.xtralink.jobs.internal.JobProcessor
import de.ii.xtralink.jobs.internal.JobQueue
import de.ii.xtraplatform.xtralink.app.XtralinkLoaderImpl
import spock.lang.Shared
import spock.lang.Specification

/**
 * Integration test against the real xtralink memory backend for the progress protocol used by the
 * search job processor. The backend finalizes a job only when its progress current equals the
 * total exactly, and it handles a permanently failed partial by topping the parent up by
 * {@code partial.total - partial.current} — a partial whose counters were driven beyond its own
 * total would therefore push the parent's progress back down and leave the job unfinished forever.
 *
 * <p>The protocol under test: fine-grained progress goes into the job's details only (init with a
 * total delta of zero), the execute partial's single unit is booked on success only, and a failure
 * leaves the top-up to the backend. Both outcomes must reach a terminal state after several
 * progress updates.
 */
class XtralinkJobProtocolSpec extends Specification {

    static final List<String> PHASES = [':setup', ':execute', ':cleanup']

    @Shared
    JobProcessor.Registration registration

    @Shared
    JobListener.Registration listener

    def setupSpec() {
        new XtralinkLoaderImpl().load()
        JobQueue.start(new QueueConfiguration(2, 'spec', Identifiers.Queue.LOCAL, Optional.empty(), List.of()))

        registration = JobProcessor.register({ partialJob, job ->
            process(partialJob.kind(), partialJob.id(), job)
        } as JobProcessor)
        listener = JobListener.register({ j -> } as JobListener)

        ['spec-fail', 'spec-ok'].each { base ->
            PHASES.each { phase -> JobQueue.register(base + phase, 1000, registration) }
        }
    }

    def cleanupSpec() {
        listener?.close()
        registration?.close()
        JobQueue.stop()
    }

    private static JobResult process(String kind, String partialJobId, Job job) {
        if (kind.endsWith(':setup')) {
            // mirrors JobProcessorSimple.presetup: job total 1, execute partial with total 1
            JobQueue.init(job.id(), new InitProgress(1, null))
            JobQueue.pushPartial(new PartialJobConfiguration(
                    kind.replace(':setup', ':execute'), job.priority(), job.id(),
                    new JobProgress(0, 1, 0, null), List.of(), Optional.empty(), Map.of()))
            return new JobResult(Identifiers.Result.SUCCESS, List.of())
        }
        if (kind.endsWith(':cleanup')) {
            return new JobResult(Identifiers.Result.SUCCESS, List.of())
        }

        // execute: several fine-grained progress updates via the details, counters untouched
        (1..3).each { step ->
            JobQueue.init(job.id(), new InitProgress(0,
                    [featuresProcessed: step * 10L, featuresTotal: 30] as Map<String, Object>))
        }

        if (kind.startsWith('spec-fail')) {
            // no partial update — the backend tops the failed partial up itself
            return new JobResult(Identifiers.Result.FAILURE, List.of('boom'))
        }

        JobQueue.updatePartial(partialJobId, 1)
        return new JobResult(Identifiers.Result.SUCCESS, List.of())
    }

    def 'a failed execute partial finalizes the job after several progress updates'() {

        given:

        JobConfiguration config = jobConfiguration('spec-fail')

        when:

        Job accepted = JobQueue.push(config, listener)
        Job done = awaitTerminal(accepted.id())

        then:

        done.status() == Identifiers.Status.FAILED
        done.errors().contains('boom')
        done.progress().current() == done.progress().total()
        done.finishedAt() > 0
        done.progress().details().get('featuresProcessed') == 30

    }

    def 'a successful execute partial finalizes the job'() {

        given:

        JobConfiguration config = jobConfiguration('spec-ok')

        when:

        Job accepted = JobQueue.push(config, listener)
        Job done = awaitTerminal(accepted.id())

        then:

        done.status() == Identifiers.Status.SUCCESSFUL
        done.errors().isEmpty()
        done.progress().current() == done.progress().total()
        done.finishedAt() > 0

    }

    private static JobConfiguration jobConfiguration(String kind) {
        return new JobConfiguration(kind, 1, 'Spec job', '', Map.of(), Map.of(),
                new JobProgress(0, 0, 0, null), true, true, List.of())
    }

    private static Job awaitTerminal(String jobId) {
        long deadline = System.currentTimeMillis() + 10_000
        Job last = null
        while (System.currentTimeMillis() < deadline) {
            Optional<Job> job = JobQueue.get(jobId)
            if (job.isPresent()) {
                last = job.get()
                if (last.finishedAt() > 0
                        && (last.status() == Identifiers.Status.FAILED
                                || last.status() == Identifiers.Status.SUCCESSFUL)) {
                    return last
                }
            }
            Thread.sleep(100)
        }
        throw new AssertionError((Object) ('job did not reach a terminal state: ' + last))
    }
}
