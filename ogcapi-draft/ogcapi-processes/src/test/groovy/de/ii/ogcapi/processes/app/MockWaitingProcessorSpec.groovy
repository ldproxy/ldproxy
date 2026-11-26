/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app

import de.ii.ogcapi.common.domain.async.JobFailure
import de.ii.ogcapi.processes.domain.ImmutableOapJob
import de.ii.ogcapi.processes.domain.OapJob
import de.ii.xtralink.jobs.BaseJob
import de.ii.xtralink.jobs.Identifiers
import de.ii.xtralink.jobs.Job
import de.ii.xtralink.jobs.PartialJob
import de.ii.xtralink.jobs.PartialJobConfiguration
import de.ii.xtraplatform.blobs.domain.ResourceStore
import de.ii.xtraplatform.xtralink.domain.JobContext
import de.ii.xtraplatform.xtralink.domain.JobInputs
import de.ii.xtraplatform.xtralink.domain.JobProcessing
import spock.lang.Specification

class MockWaitingProcessorSpec extends Specification {

    RecordingJobs jobs = new RecordingJobs()
    MockWaitingProcessor processor = new MockWaitingProcessor(Stub(ResourceStore))

    def "a wait longer than the maximum is rejected with the input as the reason, without waiting"() {
        when:
        def result = execute([seconds: MockWaitingProcessor.MAX_SECONDS + 1])

        then:
        result.status() == Identifiers.Result.FAILURE
        result.messages() == ["The input 'seconds' must be at most 600, found 601."]
        jobs.outputs == JobFailure.of(422, "The input 'seconds' must be at most 600, found 601.").asOutputs()
        jobs.updates.isEmpty()
    }

    def "a job without a wait succeeds"() {
        when:
        def result = execute([seconds: 0])

        then:
        result.status() == Identifiers.Result.SUCCESS
        jobs.outputs == [waited: 0]
        jobs.updates == [1]
    }

    private def execute(Map<String, Object> inputs) {
        OapJob oapJob = new ImmutableOapJob.Builder().inputs(inputs).build()
        processor.execute(partialJob(), job(), oapJob, jobs)
    }

    private static Job job() {
        new Job("job-1", OapJob.kind("WaitingProcess"), null, null, null, null, null, null, null, [], [:], null, null,
                [:], [:], Optional.empty(), Optional.empty(), [], [], Optional.empty(), Optional.empty())
    }

    private static PartialJob partialJob() {
        new PartialJob("partial-1", OapJob.kind("WaitingProcess", "execute"), null, null, null, null, null, null, null,
                [], [:], "job-1", null, null, [], Optional.empty())
    }

    static class RecordingJobs implements JobProcessing {
        Object outputs
        List<Integer> updates = []

        PartialJob push(PartialJobConfiguration partialJob) { null }

        PartialJob repush(String id) { null }

        void init(String jobId, int progressTotal, Map<String, ?> progressDetails) {}

        void update(String partialJobId, int delta) { updates << delta }

        void outputs(String id, Object outputs) { this.outputs = outputs }

        def <T extends JobInputs> T getInputs(Job job, Class<T> contextClass) { null }

        def <T extends JobContext> T getContext(BaseJob job, Class<T> contextClass) { null }

        boolean isCancelled(String jobId) { false }
    }
}
