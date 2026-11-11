/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import de.ii.ogcapi.common.domain.async.JobContextAsync;
import de.ii.ogcapi.common.domain.async.JobFailure;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.processes.arazzo.domain.ProcessesArazzoConfiguration;
import de.ii.ogcapi.processes.domain.OapJob;
import de.ii.xtralink.jobs.Job;
import de.ii.xtralink.jobs.JobResult;
import de.ii.xtralink.jobs.PartialJob;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import de.ii.xtraplatform.entities.domain.EntityRegistry;
import de.ii.xtraplatform.xtralink.domain.JobContext.JobContextEntity;
import de.ii.xtraplatform.xtralink.domain.JobProcessing;
import de.ii.xtraplatform.xtralink.domain.JobProcessorSimple;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A step of a process that runs a workflow. The whole workflow runs in this step, the steps of the
 * workflow depend on each other. A workflow that is invalid or that fails is a client error of the
 * execution.
 */
abstract class ArazzoProcessor extends JobProcessorSimple<OapJob> {

  private final EntityRegistry entityRegistry;
  private final StepDispatcher dispatcher;
  private final WorkflowRepository workflowRepository;
  private final WorkflowSlots workflowSlots;

  ArazzoProcessor(
      String kind,
      ResourceStore resourceStore,
      EntityRegistry entityRegistry,
      StepDispatcher dispatcher,
      WorkflowRepository workflowRepository,
      WorkflowSlots workflowSlots) {
    super(kind, resourceStore);
    this.entityRegistry = entityRegistry;
    this.dispatcher = dispatcher;
    this.workflowRepository = workflowRepository;
    this.workflowSlots = workflowSlots;
  }

  /**
   * @return the outputs of the process
   * @throws WorkflowException if the workflow is invalid or failed
   * @throws IllegalArgumentException if the inputs are invalid
   */
  protected abstract Map<String, Object> process(
      String apiId, String processId, Map<String, Object> inputs, WorkflowRunner runner);

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
    String apiId = JobContextEntity.from(job.context());
    Optional<OgcApi> api = entityRegistry.getEntity(OgcApi.class, apiId);
    if (api.isEmpty()) {
      return jobs.failure(String.format("API with id '%s' not found", apiId));
    }
    Optional<ProcessesArazzoConfiguration> configuration =
        api.get()
            .getData()
            .getExtension(ProcessesArazzoConfiguration.class)
            .filter(ExtensionConfiguration::isEnabled);
    if (configuration.isEmpty()) {
      // a process uses the step, but the building block is not enabled; a configuration error
      return jobs.failure(
          String.format("The building block PROCESSES_ARAZZO is not enabled in API '%s'.", apiId));
    }
    String processId = JobContextAsync.processIdFrom(job.context()).orElse("");

    WorkflowRunner runner =
        new WorkflowRunner(
            dispatcher,
            documentId -> workflowRepository.getDocument(apiId, documentId),
            apiId,
            Objects.requireNonNullElse(
                configuration.get().getMaximumSteps(), ProcessesArazzoBuildingBlock.MAXIMUM_STEPS),
            Objects.requireNonNullElse(
                configuration.get().getMaximumResponseSize(),
                ProcessesArazzoBuildingBlock.MAXIMUM_RESPONSE_SIZE),
            WorkflowRunner::sleep);

    // the workflow holds its job slot while it runs; one slot stays free for its processes
    Optional<String> noSlot = workflowSlots.enterWorkflow();
    if (noSlot.isPresent()) {
      return clientFailure(job, jobs, 429, noSlot.get());
    }
    Map<String, Object> outputs;
    try {
      outputs = process(apiId, processId, inputs.getInputs(), runner);
    } catch (WorkflowException e) {
      return clientFailure(job, jobs, e.getStatus(), e.getMessage());
    } catch (IllegalArgumentException e) {
      return clientFailure(job, jobs, 400, e.getMessage());
    } finally {
      workflowSlots.exitWorkflow();
    }

    jobs.outputs(job.id(), outputs);
    jobs.update(partialJob.id(), 1);
    return jobs.success();
  }

  @Override
  public JobResult cleanup(PartialJob partialJob, Job job, OapJob inputs, JobProcessing jobs) {
    return jobs.success();
  }

  private static JobResult clientFailure(Job job, JobProcessing jobs, int status, String detail) {
    JobFailure failure = JobFailure.of(status, detail);
    jobs.outputs(job.id(), failure.asOutputs());
    return jobs.failure(failure.getDetail());
  }
}
