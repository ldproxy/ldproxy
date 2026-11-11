/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.xtraplatform.base.domain.AppContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.Semaphore;

/** The workflow slots of this instance; with a shared job queue, the limit is per instance. */
@Singleton
@AutoBind
public class WorkflowSlotsImpl implements WorkflowSlots {

  private final int maxConcurrent;
  private final int workflows;
  private final Semaphore running;

  @Inject
  public WorkflowSlotsImpl(AppContext appContext) {
    this(appContext.getConfiguration().getJobConcurrency());
  }

  WorkflowSlotsImpl(int maxConcurrent) {
    this.maxConcurrent = maxConcurrent;
    // with a single job, a workflow still runs, but its steps cannot execute processes
    this.workflows = Math.max(1, maxConcurrent - 1);
    this.running = new Semaphore(workflows);
  }

  @Override
  public Optional<String> enterWorkflow() {
    if (running.tryAcquire()) {
      return Optional.empty();
    }
    return Optional.of(
        String.format(
            "%d workflows are already running, the maximum with jobs.maxConcurrent %d, one job"
                + " stays free for the processes that workflows execute. Retry later.",
            workflows, maxConcurrent));
  }

  @Override
  public void exitWorkflow() {
    running.release();
  }

  @Override
  public Optional<String> executeProcess(String processId) {
    if (maxConcurrent < 2) {
      return Optional.of(
          String.format(
              "A step of a workflow cannot execute the process '%s' with jobs.maxConcurrent %d,"
                  + " the workflow and the process each need a job; at least 2 are required.",
              processId, maxConcurrent));
    }
    return Optional.empty();
  }
}
