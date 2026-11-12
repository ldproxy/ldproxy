/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import java.util.Map;
import java.util.Optional;

/**
 * The values that runtime expressions refer to while a step of a workflow runs.
 *
 * @param inputs the inputs of the current workflow ({@code $inputs})
 * @param outputs the outputs of the current workflow evaluated so far ({@code $outputs})
 * @param steps the outputs of the steps of the current workflow by step id ({@code $steps})
 * @param workflows the inputs and outputs of the workflows that ran, by workflow id ({@code
 *     $workflows})
 * @param sources the urls of the source descriptions by name ({@code $sourceDescriptions})
 * @param exchange the request and the response of the current step, if any
 */
record ExpressionContext(
    Map<String, Object> inputs,
    Map<String, Object> outputs,
    Map<String, Map<String, Object>> steps,
    Map<String, Map<String, Object>> workflows,
    Map<String, String> sources,
    Optional<Exchange> exchange) {

  ExpressionContext withExchange(Exchange current) {
    return new ExpressionContext(
        inputs, outputs, steps, workflows, sources, Optional.ofNullable(current));
  }

  /** The context of the outputs of a step that calls a workflow: the outputs of that workflow. */
  ExpressionContext withOutputs(Map<String, Object> workflowOutputs) {
    return new ExpressionContext(inputs, workflowOutputs, steps, workflows, sources, exchange);
  }

  /** The request and the response of a step. */
  record Exchange(StepRequest request, StepResponse response) {}
}
