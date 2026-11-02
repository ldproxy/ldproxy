/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app

import de.ii.ogcapi.processes.domain.OapJob
import de.ii.ogcapi.processes.domain.model.ImmutableOgcExecute
import de.ii.ogcapi.processes.domain.model.ImmutableProcessData
import de.ii.ogcapi.processes.domain.model.ImmutableProcessJobOptions
import de.ii.ogcapi.processes.domain.model.OgcProcessSummary.JobControlOptions
import de.ii.ogcapi.processes.domain.model.ProcessData
import de.ii.xtralink.jobs.*
import de.ii.xtralink.jobs.internal.JobListener
import de.ii.xtralink.jobs.internal.JobProcessor
import de.ii.xtralink.jobs.internal.JobQueue
import de.ii.xtraplatform.xtralink.app.XtralinkLoaderImpl
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Pushes the jobs that {@link OapJob#of} builds from the job options of a process onto the real
 * xtralink memory backend, and records which partial kinds a processor gets in which order. The
 * scripted processor books the single unit of a regular partial on success, as the processors of
 * ldproxy do.
 */
class ProcessJobQueueSpec extends Specification {

    static final List<String> STEPS = ['setup', 'transform', 'execute', 'cleanup', 'a', 'b']

    // process id -> partial kinds in the order they were taken
    static final Map<String, List<String>> TAKEN = new ConcurrentHashMap<>()
    // process id -> partial kinds that are to fail
    static final Map<String, Set<String>> FAILING = new ConcurrentHashMap<>()
    // process id -> latch the partials of a parallel job wait on
    static final Map<String, CountDownLatch> RENDEZVOUS = new ConcurrentHashMap<>()

    @Shared
    JobProcessor.Registration registration

    @Shared
    JobListener.Registration listener

    @Shared
    int processCounter = 0

    def setupSpec() {
        new XtralinkLoaderImpl().load()
        JobQueue.start(new QueueConfiguration(4, 'spec-processes', Identifiers.Queue.LOCAL, Optional.empty(), List.of()))

        registration = JobProcessor.register({ partialJob, job ->
            process(partialJob.kind(), partialJob.id(), job)
        } as JobProcessor)
        listener = JobListener.register({ j -> } as JobListener)
    }

    def cleanupSpec() {
        listener?.close()
        registration?.close()
        JobQueue.stop()
    }

    private static JobResult process(String kind, String partialJobId, Job job) {
        String processId = kind.split(':')[1]
        String step = kind.split(':')[2]
        TAKEN.computeIfAbsent(processId, { new CopyOnWriteArrayList<String>() }).add(step)

        CountDownLatch rendezvous = RENDEZVOUS.get(processId)
        if (rendezvous != null && step in ['a', 'b']) {
            rendezvous.countDown()
            // both partials must be running at the same time to get past this point in time
            if (!rendezvous.await(5, TimeUnit.SECONDS)) {
                return new JobResult(Identifiers.Result.FAILURE, List.of('the other partial did not run in parallel'))
            }
        }

        if (FAILING.getOrDefault(processId, Set.of()).contains(step)) {
            return new JobResult(Identifiers.Result.FAILURE, List.of("${step} failed".toString()))
        }

        if (step != 'setup' && step != 'cleanup') {
            JobQueue.updatePartial(partialJobId, 1)
        }
        return new JobResult(Identifiers.Result.SUCCESS, List.of())
    }

    private String register() {
        String processId = "P${++processCounter}"
        STEPS.each { step -> JobQueue.register(OapJob.kind(processId, step), 1000, registration) }
        return processId
    }

    private Job push(String processId, Map options) {
        def builder = new ImmutableProcessJobOptions.Builder()
        if (options.containsKey('setup')) builder.setup(options.setup as boolean)
        if (options.containsKey('partials')) builder.partials(options.partials as List<String>)
        if (options.containsKey('sequential')) builder.sequential(options.sequential as boolean)
        if (options.containsKey('cleanup')) builder.cleanup(options.cleanup as boolean)

        ProcessData process = new ImmutableProcessData.Builder()
                .id(processId)
                .version('1.0.0')
                .jobControlOptions([JobControlOptions.ASYNC_EXECUTE])
                .job(builder.build())
                .build()
        JobConfiguration config = OapJob.of('api', process,
                new ImmutableOgcExecute.Builder().inputs([:]).build(), [:], 3600)

        Job accepted = JobQueue.push(config, listener)
        return awaitTerminal(accepted.id())
    }

    def "by default only the execute partial runs and the job ends at 1 of 1"() {
        given:
        def id = register()

        when:
        Job done = push(id, [:])

        then:
        done.status() == Identifiers.Status.SUCCESSFUL
        TAKEN[id] == ['execute']
        done.progress().current() == 1
        done.progress().total() == 1
    }

    def "setup, sequential partials and cleanup run in this order and the total counts the partials"() {
        given:
        def id = register()

        when:
        Job done = push(id, [setup: true, partials: ['transform', 'execute'], sequential: true, cleanup: true])

        then:
        done.status() == Identifiers.Status.SUCCESSFUL
        TAKEN[id] == ['setup', 'transform', 'execute', 'cleanup']
        done.progress().current() == done.progress().total()
        done.progress().total() == 2
    }

    def "in a sequential job a failed partial stops the later ones, and cleanup still runs"() {
        given:
        def id = register()
        FAILING[id] = ['transform'] as Set

        when:
        Job done = push(id, [partials: ['transform', 'execute'], sequential: true, cleanup: true])

        then:
        done.status() == Identifiers.Status.FAILED
        done.errors().contains('transform failed')
        TAKEN[id] == ['transform', 'cleanup']
        // observed: neither the failed nor the skipped partial is booked, the job ends at 0 of 2
        done.progress().current() == 0
        done.progress().total() == 2
    }

    def "in a parallel job a failed partial does not stop the others, and cleanup still runs"() {
        given:
        def id = register()
        FAILING[id] = ['a'] as Set

        when:
        Job done = push(id, [partials: ['a', 'b'], cleanup: true])

        then:
        done.status() == Identifiers.Status.FAILED
        done.errors().contains('a failed')
        TAKEN[id] as Set == ['a', 'b', 'cleanup'] as Set
        TAKEN[id].last() == 'cleanup'
        done.progress().current() == done.progress().total()
        done.progress().total() == 2
    }

    def "a failed setup queues no partials, and cleanup still runs"() {
        given:
        def id = register()
        FAILING[id] = ['setup'] as Set

        when:
        Job done = push(id, [setup: true, partials: ['transform', 'execute'], cleanup: true])

        then:
        done.status() == Identifiers.Status.FAILED
        TAKEN[id] == ['setup', 'cleanup']
    }

    def "the partials of a job that is not sequential run in parallel"() {
        given:
        def id = register()
        RENDEZVOUS[id] = new CountDownLatch(2)

        when:
        Job done = push(id, [partials: ['a', 'b']])

        then:
        done.status() == Identifiers.Status.SUCCESSFUL
        TAKEN[id] as Set == ['a', 'b'] as Set
        done.progress().current() == 2
        done.progress().total() == 2
    }

    def "the partials of a sequential job do not run in parallel"() {
        given:
        def id = register()
        RENDEZVOUS[id] = new CountDownLatch(2)

        when:
        Job done = push(id, [partials: ['a', 'b'], sequential: true])

        then:
        done.status() == Identifiers.Status.FAILED
        done.errors().contains('the other partial did not run in parallel')
        TAKEN[id] == ['a']
    }

    private static Job awaitTerminal(String jobId) {
        long deadline = System.currentTimeMillis() + 20_000
        Job last = null
        while (System.currentTimeMillis() < deadline) {
            Optional<Job> job = JobQueue.get(jobId)
            if (job.isPresent()) {
                last = job.get()
                boolean cleanupPending = last.cleanup()
                        .map { it.finishedAt() == null || it.finishedAt() <= 0 }
                        .orElse(false)
                if (last.finishedAt() > 0 && !cleanupPending
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
