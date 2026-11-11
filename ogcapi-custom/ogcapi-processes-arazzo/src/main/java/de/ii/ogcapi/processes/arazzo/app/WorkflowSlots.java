/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import java.util.Optional;

/**
 * A workflow runs in a job and holds the job slot while it runs, also while a step waits for the
 * job of a process or pauses before a retry. If all slots ({@code jobs.maxConcurrent}) were held by
 * workflows, the jobs of the processes that they execute could not start. So at most {@code
 * jobs.maxConcurrent - 1} workflows run at the same time, one slot stays free for the processes; a
 * further workflow is rejected at once, the client retries later.
 */
public interface WorkflowSlots {

  /**
   * @return empty, if the workflow may run, then {@link #exitWorkflow()} must follow; else the
   *     reason why not
   */
  Optional<String> enterWorkflow();

  void exitWorkflow();

  /**
   * @return empty, if a step of a running workflow may execute the process; else the reason why not
   */
  Optional<String> executeProcess(String processId);
}
