/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.processes.arazzo.domain.ArazzoDocument;
import de.ii.ogcapi.processes.arazzo.domain.ArazzoWorkflow;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import de.ii.xtraplatform.entities.domain.EntityRegistry;
import de.ii.xtraplatform.xtralink.domain.JobProcessorBase;
import de.ii.xtraplatform.xtralink.domain.Jobs;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The step {@code arazzo-document:execute} of a process that runs a workflow of an Arazzo
 * description ad hoc. The inputs are {@code document} (the description as a JSON object), {@code
 * workflowId} (optional, if the description has one workflow) and {@code inputs} (the inputs of the
 * workflow). The outputs are {@code outputs} (the outputs of the workflow) and {@code steps} (the
 * status code, the success and the outputs of each step that ran).
 */
@Singleton
@AutoBind(interfaces = JobProcessorBase.class)
public class ArazzoDocumentProcessor extends ArazzoProcessor {

  static final String KIND = "arazzo-document";

  @Inject
  ArazzoDocumentProcessor(
      ResourceStore resourceStore,
      EntityRegistry entityRegistry,
      StepDispatcher dispatcher,
      WorkflowRepository workflowRepository,
      WorkflowSlots workflowSlots) {
    super(KIND, resourceStore, entityRegistry, dispatcher, workflowRepository, workflowSlots);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected Map<String, Object> process(
      String apiId, String processId, Map<String, Object> inputs, WorkflowRunner runner) {
    Object documentInput = inputs.get("document");
    if (!(documentInput instanceof Map<?, ?>)) {
      throw new IllegalArgumentException(
          "The input 'document' is required, an Arazzo description as a JSON object.");
    }
    ArazzoDocument document;
    try {
      document = Jobs.DEFAULT_MAPPER.convertValue(documentInput, ArazzoDocument.class);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "The input 'document' is not an Arazzo description: " + e.getMessage(), e);
    }
    String workflowId = workflowId(document, inputs.get("workflowId"));
    Object workflowInputs = inputs.getOrDefault("inputs", Map.of());
    if (!(workflowInputs instanceof Map<?, ?>)) {
      throw new IllegalArgumentException("The input 'inputs' must be an object.");
    }

    WorkflowRunner.Result result =
        runner.run(document, workflowId, (Map<String, Object>) workflowInputs);
    Map<String, Object> outputs = new LinkedHashMap<>();
    outputs.put("outputs", result.outputs());
    outputs.put("steps", result.steps());
    return outputs;
  }

  private static String workflowId(ArazzoDocument document, Object workflowId) {
    if (workflowId instanceof String id) {
      return id;
    }
    if (document.getWorkflows().size() == 1) {
      return document.getWorkflows().get(0).getWorkflowId();
    }
    throw new IllegalArgumentException(
        String.format(
            "The input 'workflowId' is required, the description has the workflows %s.",
            document.getWorkflows().stream().map(ArazzoWorkflow::getWorkflowId).toList()));
  }
}
