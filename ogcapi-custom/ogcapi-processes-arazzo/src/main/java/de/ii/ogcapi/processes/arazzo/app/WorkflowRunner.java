/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import de.ii.ogcapi.processes.arazzo.app.ExpressionContext.Exchange;
import de.ii.ogcapi.processes.arazzo.app.StepDispatcher.Operation;
import de.ii.ogcapi.processes.arazzo.domain.ArazzoAction;
import de.ii.ogcapi.processes.arazzo.domain.ArazzoComponents;
import de.ii.ogcapi.processes.arazzo.domain.ArazzoDocument;
import de.ii.ogcapi.processes.arazzo.domain.ArazzoParameter;
import de.ii.ogcapi.processes.arazzo.domain.ArazzoReplacement;
import de.ii.ogcapi.processes.arazzo.domain.ArazzoRequestBody;
import de.ii.ogcapi.processes.arazzo.domain.ArazzoSource;
import de.ii.ogcapi.processes.arazzo.domain.ArazzoStep;
import de.ii.ogcapi.processes.arazzo.domain.ArazzoWorkflow;
import de.ii.ogcapi.processes.arazzo.domain.ImmutableArazzoParameter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Executes a workflow of an Arazzo description: the steps in order, the success and failure actions
 * ({@code end}, {@code goto}, {@code retry}), nested workflows and the outputs. One runner executes
 * one process execution; the limits apply to the whole execution.
 */
final class WorkflowRunner {

  static final int MAXIMUM_DEPTH = 8;
  // a longer pause does not fit a synchronous execution and blocks a job processor thread
  static final double MAXIMUM_RETRY_AFTER_SECONDS = 60;

  private static final String SOURCE_DESCRIPTIONS = "$sourceDescriptions.";
  // the next step after an action; other values are the index of a step, empty ends the workflow
  private static final int NEXT = -1;
  private static final Pattern OPERATION_PATH =
      Pattern.compile("^\\{\\$sourceDescriptions\\.([A-Za-z0-9_\\-]+)\\.url}#(/.*)$");

  private final StepDispatcher dispatcher;
  private final ExternalApis externalApis;
  private final Function<String, Optional<ArazzoDocument>> storedDocuments;
  private final String apiId;
  private final int maximumSteps;
  private final int maximumResponseSize;
  private final Pause pause;
  private final Map<String, Map<String, Object>> workflows;
  private final List<Map<String, Object>> trace;
  // the external APIs of the sources by URL, each document is read once per execution
  private final Map<String, ExternalApi> externalSources;
  private int stepsRun;

  /** Pauses before a retry; the default waits, a test does not. */
  @FunctionalInterface
  interface Pause {
    void pause(long millis);
  }

  /** The result of an execution: the outputs of the workflow and what each step returned. */
  record Result(Map<String, Object> outputs, List<Map<String, Object>> steps) {}

  /**
   * @param storedDocuments the stored Arazzo descriptions of the API by id, the targets of sources
   *     of the type {@code arazzo}
   */
  WorkflowRunner(
      StepDispatcher dispatcher,
      ExternalApis externalApis,
      Function<String, Optional<ArazzoDocument>> storedDocuments,
      String apiId,
      int maximumSteps,
      int maximumResponseSize,
      Pause pause) {
    this.dispatcher = dispatcher;
    this.externalApis = externalApis;
    this.storedDocuments = storedDocuments;
    this.apiId = apiId;
    this.maximumSteps = maximumSteps;
    this.maximumResponseSize = maximumResponseSize;
    this.pause = pause;
    this.workflows = new LinkedHashMap<>();
    this.trace = new ArrayList<>();
    this.externalSources = new HashMap<>();
  }

  // the pause of a retry; the job processor thread waits, as the specification requires
  @SuppressWarnings("PMD.DoNotUseThreads")
  static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new WorkflowException(
          WorkflowException.Kind.FAILURE, "The execution was interrupted.", e);
    }
  }

  /**
   * @throws WorkflowException if the description is invalid or the workflow failed
   */
  Result run(ArazzoDocument document, String workflowId, Map<String, Object> inputs) {
    List<String> problems = ArazzoChecker.problems(document);
    if (!problems.isEmpty()) {
      throw WorkflowException.problem("%s", String.join(" ", problems));
    }
    ArazzoWorkflow workflow =
        workflow(document, workflowId)
            .orElseThrow(
                () -> WorkflowException.problem("The workflow '%s' does not exist.", workflowId));
    Map<String, Object> outputs = run(document, workflow, inputs, 0);
    return new Result(outputs, List.copyOf(trace));
  }

  private static Optional<ArazzoWorkflow> workflow(ArazzoDocument document, String workflowId) {
    return document.getWorkflows().stream()
        .filter(workflow -> workflow.getWorkflowId().equals(workflowId))
        .findFirst();
  }

  // --- workflows

  private Map<String, Object> run(
      ArazzoDocument document, ArazzoWorkflow workflow, Map<String, Object> inputs, int depth) {
    if (depth > MAXIMUM_DEPTH) {
      throw WorkflowException.failure(
          "The workflows are nested more than %d levels deep.", MAXIMUM_DEPTH);
    }
    Map<String, Object> actualInputs = inputs(document, workflow, inputs);
    State state = new State(document, workflow, actualInputs, depth);
    List<ArazzoStep> steps = workflow.getSteps();
    int index = 0;
    while (index < steps.size()) {
      ArazzoStep step = steps.get(index);
      StepOutcome outcome = execute(state, step);
      Optional<Integer> next =
          outcome.success() ? onSuccess(state, step, outcome) : onFailure(state, step, outcome);
      if (next.isEmpty()) {
        break;
      }
      index = next.get() == NEXT ? index + 1 : next.get();
    }
    return outputs(state);
  }

  /** The state of a running workflow. */
  private static final class State {
    final ArazzoDocument document;
    final ArazzoWorkflow workflow;
    final Map<String, Object> inputs;
    final Map<String, Object> outputs = new LinkedHashMap<>();
    final Map<String, Map<String, Object>> steps = new LinkedHashMap<>();
    final Map<String, Integer> retries = new HashMap<>();
    final int depth;

    State(ArazzoDocument document, ArazzoWorkflow workflow, Map<String, Object> inputs, int depth) {
      this.document = document;
      this.workflow = workflow;
      this.inputs = inputs;
      this.depth = depth;
    }
  }

  private ExpressionContext context(State state) {
    Map<String, String> sources = new LinkedHashMap<>();
    state.document.getSourceDescriptions().forEach(s -> sources.put(s.getName(), s.getUrl()));
    return new ExpressionContext(
        state.inputs, state.outputs, state.steps, workflows, sources, Optional.empty());
  }

  // the inputs with the defaults of the schema; the required inputs must have a value
  @SuppressWarnings("unchecked")
  private static Map<String, Object> inputs(
      ArazzoDocument document, ArazzoWorkflow workflow, Map<String, Object> inputs) {
    Map<String, Object> actual = new LinkedHashMap<>(inputs);
    Map<String, Object> schema = inputSchema(document, workflow);
    if (schema.get("properties") instanceof Map<?, ?> properties) {
      properties.forEach(
          (name, property) -> {
            if (property instanceof Map<?, ?> definition
                && definition.containsKey("default")
                && !actual.containsKey(String.valueOf(name))) {
              actual.put(String.valueOf(name), definition.get("default"));
            }
          });
    }
    if (schema.get("required") instanceof List<?> required) {
      List<String> missing =
          required.stream()
              .map(String::valueOf)
              .filter(name -> Objects.isNull(actual.get(name)))
              .toList();
      if (!missing.isEmpty()) {
        throw WorkflowException.problem(
            "The workflow '%s' needs the inputs %s.", workflow.getWorkflowId(), missing);
      }
    }
    return actual;
  }

  /** The JSON Schema of the inputs of a workflow, a reference to the components resolved. */
  @SuppressWarnings("unchecked")
  static Map<String, Object> inputSchema(ArazzoDocument document, ArazzoWorkflow workflow) {
    Object schema = workflow.getInputs();
    if (schema instanceof Map<?, ?> map && map.get("$ref") instanceof String reference) {
      schema =
          document
              .getComponents()
              .map(ArazzoComponents::getInputs)
              .map(
                  inputs ->
                      inputs.get(reference.substring(ArazzoChecker.COMPONENTS_INPUTS.length())))
              .orElse(null);
    }
    return schema instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  private Map<String, Object> outputs(State state) {
    ExpressionContext context = context(state);
    state
        .workflow
        .getOutputs()
        .forEach(
            (name, value) -> state.outputs.put(name, RuntimeExpressions.resolve(value, context)));
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("inputs", state.inputs);
    result.put("outputs", state.outputs);
    workflows.put(state.workflow.getWorkflowId(), result);
    return state.outputs;
  }

  // --- steps

  private record StepOutcome(boolean success, Optional<Exchange> exchange) {}

  private StepOutcome execute(State state, ArazzoStep step) {
    stepsRun++;
    if (stepsRun > maximumSteps) {
      throw WorkflowException.failure(
          "The execution needs more than %d steps, the maximum of an execution.", maximumSteps);
    }
    ExpressionContext context = context(state);
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("workflowId", state.workflow.getWorkflowId());
    entry.put("stepId", step.getStepId());
    trace.add(entry);

    Optional<Exchange> exchange = Optional.empty();
    boolean success;
    if (step.getWorkflowId().isPresent()) {
      success = runNested(state, step, context);
    } else {
      StepRequest request = request(state, step, context);
      StepResponse response =
          request.server().isPresent()
              ? externalApis.dispatch(request, maximumResponseSize)
              : dispatcher.dispatch(request, maximumResponseSize);
      exchange = Optional.of(new Exchange(request, response));
      entry.put("statusCode", response.statusCode());
      context = context.withExchange(exchange.get());
      success =
          step.getSuccessCriteria().isEmpty()
              ? response.isSuccessful()
              : Criteria.all(step.getSuccessCriteria(), context);
    }
    if (success && step.getWorkflowId().isPresent() && !step.getSuccessCriteria().isEmpty()) {
      success = Criteria.all(step.getSuccessCriteria(), context);
    }
    entry.put("success", success);
    if (success) {
      ExpressionContext outputsContext = context;
      Map<String, Object> outputs = new LinkedHashMap<>();
      step.getOutputs()
          .forEach(
              (name, value) ->
                  outputs.put(name, RuntimeExpressions.resolve(value, outputsContext)));
      state.steps.put(step.getStepId(), outputs);
      if (!outputs.isEmpty()) {
        entry.put("outputs", outputs);
      }
    }
    return new StepOutcome(success, exchange);
  }

  private boolean runNested(State state, ArazzoStep step, ExpressionContext context) {
    String reference = step.getWorkflowId().orElseThrow();
    ArazzoDocument targetDocument = targetDocument(state.document, reference);
    ArazzoWorkflow target = targetWorkflow(targetDocument, reference);
    Map<String, Object> inputs = new LinkedHashMap<>();
    parameters(state, step)
        .forEach(
            parameter ->
                inputs.put(
                    parameter.getName().orElseThrow(),
                    RuntimeExpressions.resolve(parameter.getValue(), context)));
    try {
      run(targetDocument, target, inputs, state.depth + 1);
      return true;
    } catch (WorkflowException e) {
      if (e.getKind() == WorkflowException.Kind.PROBLEM) {
        throw e;
      }
      return false;
    }
  }

  private ArazzoDocument targetDocument(ArazzoDocument document, String reference) {
    if (!reference.startsWith(SOURCE_DESCRIPTIONS)) {
      return document;
    }
    String rest = reference.substring(SOURCE_DESCRIPTIONS.length());
    int dot = rest.indexOf('.');
    ArazzoSource source = source(document, dot < 0 ? rest : rest.substring(0, dot));
    if (!ArazzoSource.ARAZZO.equals(source.getType().orElse(ArazzoSource.OPENAPI))) {
      throw WorkflowException.problem(
          "The source description '%s' is not an Arazzo description.", source.getName());
    }
    String id = storedDocumentId(source.getUrl());
    ArazzoDocument target =
        storedDocuments
            .apply(id)
            .orElseThrow(
                () ->
                    WorkflowException.problem(
                        "The Arazzo description '%s' is not a stored description of the API.",
                        source.getUrl()));
    List<String> problems = ArazzoChecker.problems(target);
    if (!problems.isEmpty()) {
      throw WorkflowException.problem(
          "The Arazzo description '%s' is invalid: %s", id, String.join(" ", problems));
    }
    return target;
  }

  private static ArazzoWorkflow targetWorkflow(ArazzoDocument document, String reference) {
    String workflowId =
        reference.startsWith(SOURCE_DESCRIPTIONS)
            ? reference.substring(reference.lastIndexOf('.') + 1)
            : reference;
    return workflow(document, workflowId)
        .orElseThrow(
            () -> WorkflowException.problem("The workflow '%s' does not exist.", reference));
  }

  // the id of a stored description: the name of the file without the extension
  static String storedDocumentId(String url) {
    String name = url.replaceAll("[?#].*$", "");
    name = name.substring(name.lastIndexOf('/') + 1);
    return name.replaceAll("\\.(ya?ml|json)$", "");
  }

  private static ArazzoSource source(ArazzoDocument document, String name) {
    return document.getSourceDescriptions().stream()
        .filter(source -> source.getName().equals(name))
        .findFirst()
        .orElseThrow(
            () -> WorkflowException.problem("The source description '%s' does not exist.", name));
  }

  // --- requests

  private StepRequest request(State state, ArazzoStep step, ExpressionContext context) {
    Operation operation = operation(state.document, step);
    if (!operation.isSupported()) {
      throw WorkflowException.problem(
          "The operation '%s' (%s %s) cannot be a step of a workflow.",
          operation.operationId(), operation.method(), operation.path());
    }
    Map<String, String> pathParameters = new LinkedHashMap<>();
    Map<String, String> query = new LinkedHashMap<>();
    Map<String, String> headers = new LinkedHashMap<>();
    for (ArazzoParameter parameter : parameters(state, step)) {
      String value = parameterValue(RuntimeExpressions.resolve(parameter.getValue(), context));
      String name = parameter.getName().orElseThrow();
      switch (parameter.getIn().orElse("")) {
        case ArazzoParameter.PATH -> pathParameters.put(name, value);
        case ArazzoParameter.HEADER -> headers.put(name.toLowerCase(Locale.ROOT), value);
        default -> query.put(name, value);
      }
    }
    String path = path(operation, pathParameters, step);
    Optional<Object> body = step.getRequestBody().map(requestBody -> body(requestBody, context));
    return new StepRequest(
        operation.apiId(),
        operation.operationId(),
        operation.method(),
        path,
        pathParameters,
        query,
        headers,
        body,
        operation.server());
  }

  private static String path(
      Operation operation, Map<String, String> pathParameters, ArazzoStep step) {
    Matcher matcher = Pattern.compile("\\{([^}]+)}").matcher(operation.path());
    StringBuilder path = new StringBuilder();
    while (matcher.find()) {
      String value = pathParameters.get(matcher.group(1));
      if (Objects.isNull(value)) {
        throw WorkflowException.problem(
            "The step '%s' has no value for the path parameter '%s'.",
            step.getStepId(), matcher.group(1));
      }
      matcher.appendReplacement(
          path,
          Matcher.quoteReplacement(
              URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")));
    }
    matcher.appendTail(path);
    return path.toString();
  }

  private static Object body(ArazzoRequestBody requestBody, ExpressionContext context) {
    String contentType = requestBody.getContentType().orElse("application/json");
    if (!contentType.contains("json")) {
      throw WorkflowException.problem(
          "The content type '%s' is not supported, only JSON.", contentType);
    }
    Object payload = RuntimeExpressions.resolve(requestBody.getPayload(), context);
    for (ArazzoReplacement replacement : requestBody.getReplacements()) {
      try {
        JsonPointers.set(
            payload,
            replacement.getTarget(),
            RuntimeExpressions.resolve(replacement.getValue(), context));
      } catch (IllegalArgumentException e) {
        throw new WorkflowException(WorkflowException.Kind.PROBLEM, e.getMessage(), e);
      }
    }
    return payload;
  }

  static String parameterValue(Object value) {
    if (value instanceof List<?> list) {
      return list.stream().map(WorkflowRunner::parameterValue).collect(Collectors.joining(","));
    }
    return RuntimeExpressions.stringify(value);
  }

  // the parameters of the workflow and of the step, a parameter of the step replaces one of the
  // workflow with the same name and location; references to components are resolved
  private static List<ArazzoParameter> parameters(State state, ArazzoStep step) {
    Map<String, ArazzoParameter> parameters = new LinkedHashMap<>();
    Stream.concat(state.workflow.getParameters().stream(), step.getParameters().stream())
        .map(parameter -> resolve(state.document, parameter))
        .forEach(
            parameter ->
                parameters.put(
                    parameter.getIn().orElse("") + ":" + parameter.getName().orElse(""),
                    parameter));
    return List.copyOf(parameters.values());
  }

  private static ArazzoParameter resolve(ArazzoDocument document, ArazzoParameter parameter) {
    if (parameter.getReference().isEmpty()) {
      return parameter;
    }
    String key =
        parameter.getReference().get().substring(ArazzoChecker.COMPONENTS_PARAMETERS.length());
    ArazzoParameter component =
        document.getComponents().map(c -> c.getParameters().get(key)).orElseThrow();
    return Objects.isNull(parameter.getValue())
        ? component
        : ImmutableArazzoParameter.copyOf(component).withValue(parameter.getValue());
  }

  private Operation operation(ArazzoDocument document, ArazzoStep step) {
    if (step.getOperationPath().isPresent()) {
      return operationByPath(document, step.getOperationPath().get());
    }
    String operationId = step.getOperationId().orElseThrow();
    if (operationId.startsWith(SOURCE_DESCRIPTIONS)) {
      return operationOfSource(document, operationId);
    }
    return operationOfAnySource(document, operationId);
  }

  // {$sourceDescriptions.<name>.url}#/paths/<path>/<method>
  private Operation operationByPath(ArazzoDocument document, String operationPath) {
    Matcher matcher = OPERATION_PATH.matcher(operationPath);
    if (!matcher.matches()) {
      throw WorkflowException.problem(
          "The operation path '%s' must be '{$sourceDescriptions.<name>.url}#/paths/...'.",
          operationPath);
    }
    String sourceApi = openApiSource(document, matcher.group(1));
    List<String> tokens = JsonPointers.tokens(matcher.group(2));
    if (tokens.size() != 3 || !"paths".equals(tokens.get(0))) {
      throw WorkflowException.problem(
          "The operation path '%s' does not point to an operation.", operationPath);
    }
    String method = tokens.get(2).toUpperCase(Locale.ROOT);
    String path = tokens.get(1);
    return Optional.ofNullable(externalSources.get(sourceApi))
        .map(api -> api.operation(method, path))
        .orElseGet(() -> dispatcher.operation(sourceApi, method, path))
        .orElseThrow(
            () -> WorkflowException.problem("The operation '%s' does not exist.", operationPath));
  }

  // $sourceDescriptions.<name>.<operationId>
  private Operation operationOfSource(ArazzoDocument document, String operationId) {
    String rest = operationId.substring(SOURCE_DESCRIPTIONS.length());
    int dot = rest.indexOf('.');
    if (dot < 0) {
      throw WorkflowException.problem("The operation id '%s' is invalid.", operationId);
    }
    String sourceApi = openApiSource(document, rest.substring(0, dot));
    return operation(sourceApi, rest.substring(dot + 1))
        .orElseThrow(
            () -> WorkflowException.problem("The operation '%s' does not exist.", operationId));
  }

  // a bare operation id: the API of the only OpenAPI source, or of this API without sources
  private Operation operationOfAnySource(ArazzoDocument document, String operationId) {
    List<String> apis =
        document.getSourceDescriptions().stream()
            .filter(s -> ArazzoSource.OPENAPI.equals(s.getType().orElse(ArazzoSource.OPENAPI)))
            .map(s -> openApiSource(document, s.getName()))
            .toList();
    List<Operation> candidates =
        (apis.isEmpty() ? List.of(apiId) : apis)
            .stream().map(api -> operation(api, operationId)).flatMap(Optional::stream).toList();
    if (candidates.size() != 1) {
      throw WorkflowException.problem(
          candidates.isEmpty()
              ? "The operation '%s' does not exist."
              : "The operation '%s' exists in several sources, use"
                  + " '$sourceDescriptions.<name>.<operationId>'.",
          operationId);
    }
    return candidates.get(0);
  }

  private Optional<Operation> operation(String sourceApi, String operationId) {
    return Optional.ofNullable(externalSources.get(sourceApi))
        .map(api -> api.operation(operationId))
        .orElseGet(() -> dispatcher.operation(sourceApi, operationId));
  }

  // the id of an API of this server, or the URL of an external API
  private String openApiSource(ArazzoDocument document, String name) {
    ArazzoSource source = source(document, name);
    if (!ArazzoSource.OPENAPI.equals(source.getType().orElse(ArazzoSource.OPENAPI))) {
      throw WorkflowException.problem(
          "The source description '%s' is not an OpenAPI document.", name);
    }
    String url = source.getUrl();
    Optional<String> api = dispatcher.apiOfSource(url, apiId);
    if (api.isPresent()) {
      return api.get();
    }
    if (!isAbsolute(url)) {
      throw WorkflowException.problem(
          "The source description '%s' (%s) is not an API of this server.", name, url);
    }
    if (!externalSources.containsKey(url)) {
      externalSources.put(url, externalApis.api(url, maximumResponseSize));
    }
    return url;
  }

  private static boolean isAbsolute(String url) {
    try {
      return new URI(url).isAbsolute();
    } catch (URISyntaxException e) {
      return false;
    }
  }

  // --- actions

  private Optional<Integer> onSuccess(State state, ArazzoStep step, StepOutcome outcome) {
    state.retries.keySet().removeIf(key -> key.startsWith(step.getStepId() + "#"));
    ExpressionContext context = exchangeContext(state, outcome);
    for (ArazzoAction action :
        actions(state, step.getOnSuccess(), state.workflow.getSuccessActions(), true)) {
      if (Criteria.all(action.getCriteria(), context)) {
        return transfer(state, action);
      }
    }
    return Optional.of(NEXT);
  }

  private Optional<Integer> onFailure(State state, ArazzoStep step, StepOutcome outcome) {
    ExpressionContext context = exchangeContext(state, outcome);
    List<ArazzoAction> actions =
        actions(state, step.getOnFailure(), state.workflow.getFailureActions(), false);
    for (int i = 0; i < actions.size(); i++) {
      ArazzoAction action = actions.get(i);
      if (!Criteria.all(action.getCriteria(), context)) {
        continue;
      }
      if (!ArazzoAction.RETRY.equals(action.getType().orElse(""))) {
        return transfer(state, action);
      }
      String key = step.getStepId() + "#" + i;
      int done = state.retries.getOrDefault(key, 0);
      // the retries of an action are exhausted before the following failure actions apply
      if (done < action.getRetryLimit().orElse(1)) {
        state.retries.put(key, done + 1);
        pause.pause(retryAfterMillis(action, outcome));
        runTargetOfRetry(state, action);
        return Optional.of(index(state.workflow, step.getStepId()));
      }
    }
    throw WorkflowException.failure(
        "The step '%s' of the workflow '%s' failed%s.",
        step.getStepId(),
        state.workflow.getWorkflowId(),
        outcome
            .exchange()
            .map(exchange -> " with status " + exchange.response().statusCode() + detail(exchange))
            .orElse(""));
  }

  private static String detail(Exchange exchange) {
    return exchange
        .response()
        .body()
        .filter(Map.class::isInstance)
        .map(body -> ((Map<?, ?>) body).get("detail"))
        .map(detail -> ": " + String.valueOf(detail).replaceAll("\\.$", ""))
        .orElse("");
  }

  private ExpressionContext exchangeContext(State state, StepOutcome outcome) {
    ExpressionContext context = context(state);
    return outcome.exchange().map(context::withExchange).orElse(context);
  }

  // the actions of the step, then those of the workflow; references to components are resolved
  private static List<ArazzoAction> actions(
      State state,
      List<ArazzoAction> stepActions,
      List<ArazzoAction> workflowActions,
      boolean success) {
    return Stream.concat(stepActions.stream(), workflowActions.stream())
        .map(
            action ->
                action
                    .getReference()
                    .map(
                        reference -> {
                          String prefix =
                              success
                                  ? ArazzoChecker.COMPONENTS_SUCCESS_ACTIONS
                                  : ArazzoChecker.COMPONENTS_FAILURE_ACTIONS;
                          String key = reference.substring(prefix.length());
                          return state
                              .document
                              .getComponents()
                              .map(
                                  c ->
                                      success
                                          ? c.getSuccessActions().get(key)
                                          : c.getFailureActions().get(key))
                              .orElseThrow();
                        })
                    .orElse(action))
        .toList();
  }

  // end, or goto a step or another workflow; after another workflow, the current one ends
  private Optional<Integer> transfer(State state, ArazzoAction action) {
    if (ArazzoAction.END.equals(action.getType().orElse(""))) {
      return Optional.empty();
    }
    if (action.getStepId().isPresent()) {
      return Optional.of(index(state.workflow, action.getStepId().get()));
    }
    String reference = action.getWorkflowId().orElseThrow();
    ArazzoDocument targetDocument = targetDocument(state.document, reference);
    run(targetDocument, targetWorkflow(targetDocument, reference), Map.of(), state.depth + 1);
    return Optional.empty();
  }

  // before a retry, the step or workflow of the action runs
  private void runTargetOfRetry(State state, ArazzoAction action) {
    if (action.getStepId().isPresent()) {
      ArazzoStep target =
          state.workflow.getSteps().get(index(state.workflow, action.getStepId().get()));
      execute(state, target);
    } else if (action.getWorkflowId().isPresent()) {
      String reference = action.getWorkflowId().get();
      ArazzoDocument targetDocument = targetDocument(state.document, reference);
      run(targetDocument, targetWorkflow(targetDocument, reference), Map.of(), state.depth + 1);
    }
  }

  private static long retryAfterMillis(ArazzoAction action, StepOutcome outcome) {
    // the header of the response takes precedence
    Optional<Double> header =
        outcome
            .exchange()
            .map(exchange -> exchange.response().headers().get("retry-after"))
            .flatMap(WorkflowRunner::seconds);
    double seconds = header.or(action::getRetryAfter).orElse(0.0);
    return Math.round(Math.min(seconds, MAXIMUM_RETRY_AFTER_SECONDS) * 1000);
  }

  private static Optional<Double> seconds(String value) {
    try {
      return Optional.of(Double.parseDouble(value.trim()));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private static int index(ArazzoWorkflow workflow, String stepId) {
    for (int i = 0; i < workflow.getSteps().size(); i++) {
      if (workflow.getSteps().get(i).getStepId().equals(stepId)) {
        return i;
      }
    }
    throw WorkflowException.problem(
        "The step '%s' does not exist in the workflow '%s'.", stepId, workflow.getWorkflowId());
  }
}
