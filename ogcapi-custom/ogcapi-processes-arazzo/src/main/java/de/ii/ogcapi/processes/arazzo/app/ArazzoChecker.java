/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import de.ii.ogcapi.processes.arazzo.domain.ArazzoAction;
import de.ii.ogcapi.processes.arazzo.domain.ArazzoComponents;
import de.ii.ogcapi.processes.arazzo.domain.ArazzoCriterion;
import de.ii.ogcapi.processes.arazzo.domain.ArazzoDocument;
import de.ii.ogcapi.processes.arazzo.domain.ArazzoParameter;
import de.ii.ogcapi.processes.arazzo.domain.ArazzoSource;
import de.ii.ogcapi.processes.arazzo.domain.ArazzoStep;
import de.ii.ogcapi.processes.arazzo.domain.ArazzoWorkflow;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * Checks that an Arazzo description stays within the supported subset. Members that are known, but
 * not supported, are problems; they are never ignored.
 */
final class ArazzoChecker {

  static final String COMPONENTS_PARAMETERS = "$components.parameters.";
  static final String COMPONENTS_SUCCESS_ACTIONS = "$components.successActions.";
  static final String COMPONENTS_FAILURE_ACTIONS = "$components.failureActions.";
  static final String COMPONENTS_INPUTS = "#/components/inputs/";

  private static final Pattern ID = Pattern.compile("[A-Za-z0-9_\\-]+");
  private static final Set<String> IN = Set.of("path", "query", "header");

  private final ArazzoDocument document;
  private final List<String> problems;

  private ArazzoChecker(ArazzoDocument document) {
    this.document = document;
    this.problems = new ArrayList<>();
  }

  /**
   * @return the problems of the description, empty if it can be executed
   */
  static List<String> problems(ArazzoDocument document) {
    ArazzoChecker checker = new ArazzoChecker(document);
    checker.check();
    return List.copyOf(checker.problems);
  }

  private void check() {
    if (!document.getArazzo().startsWith("1.0.") && !document.getArazzo().startsWith("1.1.")) {
      problem("The Arazzo version '%s' is not supported, only 1.0 and 1.1.", document.getArazzo());
    }
    Set<String> names = new HashSet<>();
    for (ArazzoSource source : document.getSourceDescriptions()) {
      checkId("source description", source.getName());
      if (!names.add(source.getName())) {
        problem("The source description name '%s' is not unique.", source.getName());
      }
      String type = source.getType().orElse(ArazzoSource.OPENAPI);
      if (!ArazzoSource.OPENAPI.equals(type) && !ArazzoSource.ARAZZO.equals(type)) {
        problem(
            "The source description '%s' has the type '%s', only 'openapi' and 'arazzo' are"
                + " supported.",
            source.getName(), type);
      }
    }
    if (document.getWorkflows().isEmpty()) {
      problem("The description has no workflows.");
    }
    Set<String> workflowIds = new HashSet<>();
    for (ArazzoWorkflow workflow : document.getWorkflows()) {
      checkId("workflow", workflow.getWorkflowId());
      if (!workflowIds.add(workflow.getWorkflowId())) {
        problem("The workflow id '%s' is not unique.", workflow.getWorkflowId());
      }
      checkWorkflow(workflow);
    }
    document.getComponents().ifPresent(this::checkComponents);
  }

  private void checkWorkflow(ArazzoWorkflow workflow) {
    String where = "workflow '" + workflow.getWorkflowId() + "'";
    if (!workflow.getDependsOn().isEmpty()) {
      problem("The %s uses 'dependsOn', which is not supported.", where);
    }
    if (workflow.getSteps().isEmpty()) {
      problem("The %s has no steps.", where);
    }
    checkInputs(workflow.getInputs(), where);
    workflow.getParameters().forEach(parameter -> checkParameter(parameter, where, false));
    Set<String> stepIds = new HashSet<>();
    workflow.getSteps().stream().map(ArazzoStep::getStepId).forEach(stepIds::add);
    if (stepIds.size() < workflow.getSteps().size()) {
      problem("The step ids of the %s are not unique.", where);
    }
    workflow
        .getSuccessActions()
        .forEach(action -> checkAction(action, where, stepIds, COMPONENTS_SUCCESS_ACTIONS));
    workflow
        .getFailureActions()
        .forEach(action -> checkAction(action, where, stepIds, COMPONENTS_FAILURE_ACTIONS));
    checkOutputs(workflow.getOutputs(), where);
    for (ArazzoStep step : workflow.getSteps()) {
      checkStep(step, where + ", step '" + step.getStepId() + "'", stepIds);
    }
  }

  private void checkStep(ArazzoStep step, String where, Set<String> stepIds) {
    checkId("step", step.getStepId());
    long targets =
        Stream.of(step.getOperationId(), step.getOperationPath(), step.getWorkflowId())
            .filter(Optional::isPresent)
            .count();
    if (targets != 1) {
      problem(
          "The %s must have exactly one of 'operationId', 'operationPath' or 'workflowId'.", where);
    }
    if (step.getChannelPath().isPresent()
        || step.getCorrelationId().isPresent()
        || step.getAction().isPresent()) {
      problem("The %s is an AsyncAPI step, which is not supported.", where);
    }
    if (Objects.nonNull(step.getTimeout())) {
      problem("The %s uses 'timeout', which is not supported.", where);
    }
    if (!step.getDependsOn().isEmpty()) {
      problem("The %s uses 'dependsOn', which is not supported.", where);
    }
    boolean isWorkflow = step.getWorkflowId().isPresent();
    step.getParameters().forEach(parameter -> checkParameter(parameter, where, isWorkflow));
    step.getRequestBody()
        .ifPresent(
            body -> {
              if (isWorkflow) {
                problem("The %s calls a workflow and must not have a request body.", where);
              }
              checkValue(body.getPayload(), where);
              body.getReplacements()
                  .forEach(
                      replacement -> {
                        Object selectorType = replacement.getTargetSelectorType();
                        if (Objects.nonNull(selectorType)
                            && !"jsonpointer".equals(Criteria.typeName(selectorType))) {
                          problem(
                              "The %s has a replacement with a target that is not a JSON Pointer,"
                                  + " which is not supported.",
                              where);
                        }
                        checkValue(replacement.getValue(), where);
                      });
            });
    step.getSuccessCriteria().forEach(criterion -> checkCriterion(criterion, where));
    step.getOnSuccess()
        .forEach(action -> checkAction(action, where, stepIds, COMPONENTS_SUCCESS_ACTIONS));
    step.getOnFailure()
        .forEach(action -> checkAction(action, where, stepIds, COMPONENTS_FAILURE_ACTIONS));
    checkOutputs(step.getOutputs(), where);
  }

  private void checkParameter(ArazzoParameter parameter, String where, boolean isWorkflow) {
    if (parameter.getReference().isPresent()) {
      String reference = parameter.getReference().get();
      if (!reference.startsWith(COMPONENTS_PARAMETERS)
          || parameter(reference.substring(COMPONENTS_PARAMETERS.length())) == null) {
        problem("The %s refers to the unknown parameter '%s'.", where, reference);
      }
      checkValue(parameter.getValue(), where);
      return;
    }
    if (parameter.getName().isEmpty()) {
      problem("A parameter of the %s has no name.", where);
    }
    // the location is ignored for the inputs of a workflow
    if (!isWorkflow) {
      String in = parameter.getIn().orElse("");
      if ("querystring".equals(in)) {
        problem("The %s has a parameter in 'querystring', which is not supported.", where);
      } else if (!IN.contains(in)) {
        problem(
            "The parameter '%s' of the %s must be in 'path', 'query' or 'header'.",
            parameter.getName().orElse(""), where);
      }
    }
    checkValue(parameter.getValue(), where);
  }

  private ArazzoParameter parameter(String key) {
    return document
        .getComponents()
        .map(components -> components.getParameters().get(key))
        .orElse(null);
  }

  private void checkAction(
      ArazzoAction action, String where, Set<String> stepIds, String componentsPrefix) {
    if (action.getReference().isPresent()) {
      checkActionReference(action.getReference().get(), where, componentsPrefix);
      return;
    }
    String type = action.getType().orElse("");
    boolean isRetry = ArazzoAction.RETRY.equals(type);
    if (!ArazzoAction.END.equals(type) && !ArazzoAction.GOTO.equals(type) && !isRetry) {
      problem("An action of the %s has the unknown type '%s'.", where, type);
    }
    if (isRetry && COMPONENTS_SUCCESS_ACTIONS.equals(componentsPrefix)) {
      problem("A success action of the %s has the type 'retry'.", where);
    }
    if (action.getStepId().isPresent() && action.getWorkflowId().isPresent()) {
      problem("An action of the %s has both 'stepId' and 'workflowId'.", where);
    }
    if (ArazzoAction.GOTO.equals(type)
        && action.getStepId().isEmpty()
        && action.getWorkflowId().isEmpty()) {
      problem("A 'goto' action of the %s has neither 'stepId' nor 'workflowId'.", where);
    }
    action
        .getStepId()
        .filter(stepId -> !stepIds.contains(stepId))
        .ifPresent(
            stepId ->
                problem("An action of the %s refers to the unknown step '%s'.", where, stepId));
    if (!action.getParameters().isEmpty()) {
      problem("An action of the %s has parameters, which are not supported.", where);
    }
    if (action.getRetryAfter().filter(seconds -> seconds < 0).isPresent()
        || action.getRetryLimit().filter(limit -> limit < 0).isPresent()) {
      problem("An action of the %s has a negative 'retryAfter' or 'retryLimit'.", where);
    }
    action.getCriteria().forEach(criterion -> checkCriterion(criterion, where));
  }

  private void checkActionReference(String reference, String where, String componentsPrefix) {
    Map<String, ArazzoAction> actions =
        document
            .getComponents()
            .map(
                components ->
                    COMPONENTS_SUCCESS_ACTIONS.equals(componentsPrefix)
                        ? components.getSuccessActions()
                        : components.getFailureActions())
            .orElse(Map.of());
    if (!reference.startsWith(componentsPrefix)
        || !actions.containsKey(reference.substring(componentsPrefix.length()))) {
      problem("The %s refers to the unknown action '%s'.", where, reference);
    }
  }

  private void checkCriterion(ArazzoCriterion criterion, String where) {
    String type = Criteria.type(criterion);
    switch (type) {
      case ArazzoCriterion.SIMPLE -> {
        try {
          SimpleCondition.parse(criterion.getCondition());
        } catch (IllegalArgumentException e) {
          problem("A criterion of the %s is invalid: %s", where, e.getMessage());
        }
      }
      case ArazzoCriterion.REGEX -> {
        if (criterion.getContext().isEmpty()) {
          problem("A regex criterion of the %s has no context.", where);
        }
        if (!criterion.getCondition().contains("{$")) {
          try {
            Pattern.compile(criterion.getCondition());
          } catch (PatternSyntaxException e) {
            problem(
                "The regex '%s' of the %s is invalid: %s",
                criterion.getCondition(), where, e.getDescription());
          }
        }
      }
      default ->
          problem(
              "A criterion of the %s has the type '%s', only 'simple' and 'regex' are supported.",
              where, type);
    }
  }

  private void checkOutputs(Map<String, Object> outputs, String where) {
    outputs.forEach(
        (name, value) -> {
          if (!(value instanceof String)) {
            problem(
                "The output '%s' of the %s is not a runtime expression; selector objects are not"
                    + " supported.",
                name, where);
          }
        });
  }

  private void checkInputs(Object inputs, String where) {
    if (Objects.isNull(inputs)) {
      return;
    }
    if (!(inputs instanceof Map<?, ?> schema)) {
      problem("The inputs of the %s are not a JSON Schema.", where);
      return;
    }
    if (schema.get("$ref") instanceof String reference
        && (!reference.startsWith(COMPONENTS_INPUTS)
            || document
                .getComponents()
                .map(ArazzoComponents::getInputs)
                .map(components -> components.get(reference.substring(COMPONENTS_INPUTS.length())))
                .isEmpty())) {
      problem("The inputs of the %s refer to the unknown schema '%s'.", where, reference);
    }
  }

  // selector objects of version 1.1 are not supported
  private void checkValue(Object value, String where) {
    if (value instanceof Map<?, ?> map) {
      if (map.size() == 3
          && map.containsKey("context")
          && map.containsKey("selector")
          && map.containsKey("type")) {
        problem("The %s uses a selector object, which is not supported.", where);
        return;
      }
      map.values().forEach(member -> checkValue(member, where));
    } else if (value instanceof List<?> list) {
      list.forEach(item -> checkValue(item, where));
    }
  }

  private void checkComponents(ArazzoComponents components) {
    components
        .getParameters()
        .forEach((key, parameter) -> checkParameter(parameter, "component '" + key + "'", false));
  }

  private void checkId(String kind, String id) {
    if (!ID.matcher(id).matches()) {
      problem("The %s id '%s' must only contain letters, digits, '_' and '-'.", kind, id);
    }
  }

  private void problem(String format, Object... arguments) {
    problems.add(String.format(format, arguments));
  }
}
