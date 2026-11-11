/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import de.ii.xtraplatform.entities.domain.EntityRegistry;
import de.ii.xtraplatform.web.domain.Http;
import de.ii.xtraplatform.xtralink.domain.JobProcessorBase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;

/**
 * The step {@code arazzo-workflow:execute} of the processes that are stored workflows: it runs the
 * workflow with the id of the process, the inputs of the process are the inputs of the workflow and
 * the outputs of the workflow are the outputs of the process.
 */
@Singleton
@AutoBind(interfaces = JobProcessorBase.class)
public class ArazzoWorkflowProcessor extends ArazzoProcessor {

  static final String KIND = "arazzo-workflow";

  private final WorkflowRepository workflowRepository;

  @Inject
  ArazzoWorkflowProcessor(
      ResourceStore resourceStore,
      EntityRegistry entityRegistry,
      StepDispatcher dispatcher,
      Http http,
      WorkflowRepository workflowRepository,
      WorkflowSlots workflowSlots) {
    super(KIND, resourceStore, entityRegistry, dispatcher, http, workflowRepository, workflowSlots);
    this.workflowRepository = workflowRepository;
  }

  @Override
  protected Map<String, Object> process(
      String apiId, String processId, Map<String, Object> inputs, WorkflowRunner runner) {
    return runner
        .run(
            workflowRepository
                .getByWorkflow(apiId, processId)
                .orElseThrow(
                    () ->
                        WorkflowException.problem(
                            "The workflow '%s' is no longer available.", processId)),
            processId,
            inputs)
        .outputs();
  }
}
