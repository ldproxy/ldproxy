/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app;

import de.ii.ogcapi.processes.domain.OapJob;
import de.ii.xtralink.jobs.Job;
import de.ii.xtralink.jobs.JobResult;
import de.ii.xtralink.jobs.PartialJob;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import de.ii.xtraplatform.xtralink.domain.JobProcessing;
import de.ii.xtraplatform.xtralink.domain.JobProcessorSimple;
import java.util.Map;

/** Executes one of the test processes until real processors are available. */
abstract class MockOapJobProcessor extends JobProcessorSimple<OapJob> {

  MockOapJobProcessor(String processId, ResourceStore resourceStore) {
    super(OapJob.kind(processId), resourceStore);
  }

  protected abstract Map<String, Object> execute(Map<String, Object> inputs);

  @Override
  public Class<OapJob> getInputsClass() {
    return OapJob.class;
  }

  @Override
  public JobResult setup(PartialJob partialJob, Job job, OapJob inputs, JobProcessing jobs) {
    return jobs.success();
  }

  @Override
  public JobResult execute(PartialJob partialJob, Job job, OapJob inputs, JobProcessing jobs) {
    jobs.outputs(job.id(), execute(inputs.getInputs()));
    jobs.update(partialJob.id(), 1);

    return jobs.success();
  }

  @Override
  public JobResult cleanup(PartialJob partialJob, Job job, OapJob inputs, JobProcessing jobs) {
    return jobs.success();
  }
}
