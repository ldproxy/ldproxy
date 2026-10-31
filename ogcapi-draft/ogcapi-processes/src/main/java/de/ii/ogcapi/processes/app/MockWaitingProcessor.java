/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.processes.domain.OapJob;
import de.ii.xtralink.jobs.Job;
import de.ii.xtralink.jobs.JobResult;
import de.ii.xtralink.jobs.PartialJob;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import de.ii.xtraplatform.xtralink.domain.JobProcessing;
import de.ii.xtraplatform.xtralink.domain.JobProcessorBase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;

/**
 * A test process that runs for a given number of seconds, so that a running job, its status and its
 * callbacks can be observed; with the input "fail" the job fails at the end. A dismissed job stops
 * within a second.
 */
@Singleton
@AutoBind(interfaces = JobProcessorBase.class)
public class MockWaitingProcessor extends MockOapJobProcessor {

  @Inject
  MockWaitingProcessor(ResourceStore resourceStore) {
    super("WaitingProcess", resourceStore);
  }

  @Override
  protected Map<String, Object> execute(Map<String, Object> inputs) {
    return Map.of("waited", seconds(inputs));
  }

  @Override
  public JobResult execute(PartialJob partialJob, Job job, OapJob inputs, JobProcessing jobs) {
    int seconds = seconds(inputs.getInputs());
    for (int i = 0; i < seconds; i++) {
      if (jobs.isCancelled(job.id())) {
        return jobs.failure("The job was cancelled");
      }
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return jobs.failure("The job was interrupted");
      }
    }

    if (Boolean.TRUE.equals(inputs.getInputs().get("fail"))) {
      return jobs.failure(
          "The process failed after %d seconds, as requested by the input 'fail'."
              .formatted(seconds));
    }

    return super.execute(partialJob, job, inputs, jobs);
  }

  private static int seconds(Map<String, Object> inputs) {
    return ((Number) inputs.get("seconds")).intValue();
  }
}
