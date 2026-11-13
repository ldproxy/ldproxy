/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.processes.arazzo.domain.ArazzoDocument;
import de.ii.ogcapi.processes.arazzo.domain.ArazzoWorkflow;
import de.ii.ogcapi.processes.arazzo.domain.ProcessesArazzoConfiguration;
import de.ii.ogcapi.processes.domain.model.ImmutableOgcInputDescription;
import de.ii.ogcapi.processes.domain.model.ImmutableOgcOutputDescription;
import de.ii.ogcapi.processes.domain.model.ImmutableOgcSchema;
import de.ii.ogcapi.processes.domain.model.ImmutableProcessData;
import de.ii.ogcapi.processes.domain.model.ImmutableProcessJobOptions;
import de.ii.ogcapi.processes.domain.model.OgcProcessSummary.JobControlOptions;
import de.ii.ogcapi.processes.domain.model.OgcSchema;
import de.ii.ogcapi.processes.domain.model.ProcessContribution;
import de.ii.ogcapi.processes.domain.model.ProcessData;
import de.ii.xtraplatform.entities.domain.EntityRegistry;
import de.ii.xtraplatform.values.domain.Identifier;
import de.ii.xtraplatform.values.domain.ValueStore;
import de.ii.xtraplatform.values.domain.Values;
import de.ii.xtraplatform.xtralink.domain.Jobs;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Each workflow of a stored Arazzo description in {@code values/workflows/{apiId}} is a process of
 * the API, if the building block PROCESSES_ARAZZO is enabled: the id is the workflow id, the inputs
 * are the properties of the input schema, the outputs those of the workflow, and the process runs
 * the workflow with the step {@code arazzo-workflow:execute}. Only the workflows in {@code
 * publishedWorkflows} are processes, if there are any, and the inputs that the server sets are not
 * inputs of the process. A description that is invalid or outside the supported subset is not
 * published, the problems are logged.
 */
@Singleton
@AutoBind
public class WorkflowProcesses implements ProcessContribution, WorkflowRepository {

  private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowProcesses.class);

  static final String STEP = ArazzoWorkflowProcessor.KIND + ":execute";

  private static final String ITEMS = "items";
  private static final String PROPERTIES = "properties";

  // the keywords of JSON Schema that process descriptions support; annotations are dropped
  private static final Set<String> SCHEMA_KEYWORDS =
      Set.of(
          "title",
          "description",
          "type",
          "enum",
          "format",
          "contentMediaType",
          "maximum",
          "exclusiveMaximum",
          "minimum",
          "exclusiveMinimum",
          "pattern",
          "maxItems",
          "minItems",
          ITEMS,
          "required",
          PROPERTIES);
  private static final Set<String> PROPERTY_KEYWORDS =
      Set.of(
          "title",
          "description",
          "type",
          "enum",
          "format",
          "contentMediaType",
          "maximum",
          "exclusiveMaximum",
          "minimum",
          "exclusiveMinimum",
          "pattern",
          "maxItems",
          "minItems",
          ITEMS);
  private static final Set<String> ITEMS_KEYWORDS = Set.of("type", "required", PROPERTIES);
  private static final Set<String> ANNOTATIONS =
      Set.of("default", "examples", "example", "$comment", "deprecated", "readOnly", "writeOnly");

  private final Values<ArazzoDocument> store;
  private final EntityRegistry entityRegistry;
  // the processes derived from a description for an API, as long as the description and the
  // configuration of the API do not change
  private final Map<Key, Derived> cache;

  private record Key(String apiId, Identifier identifier) {}

  // the published workflows and the inputs set by the server
  record Selection(List<String> published, Set<String> serverInputs) {
    static final Selection ALL = new Selection(List.of(), Set.of());

    boolean isPublished(String workflowId) {
      return published.isEmpty() || published.contains(workflowId);
    }
  }

  private record Derived(
      ArazzoDocument document, Selection selection, Map<String, ProcessData> processes) {}

  @Inject
  public WorkflowProcesses(ValueStore valueStore, EntityRegistry entityRegistry) {
    this.store = valueStore.forType(ArazzoDocument.class);
    this.entityRegistry = entityRegistry;
    this.cache = new ConcurrentHashMap<>();
  }

  @Override
  public Map<String, ProcessData> getProcesses(String apiId) {
    Optional<Selection> selection = selection(apiId);
    if (selection.isEmpty()) {
      return Map.of();
    }
    Map<String, ProcessData> processes = new LinkedHashMap<>();
    for (Identifier identifier : identifiers(apiId)) {
      derived(apiId, identifier, selection.get())
          .forEach(
              (id, process) -> {
                if (processes.putIfAbsent(id, process) != null && LOGGER.isWarnEnabled()) {
                  LOGGER.warn(
                      "The workflow '{}' of API '{}' is defined more than once, '{}' is ignored.",
                      id,
                      apiId,
                      identifier.asPath());
                }
              });
    }
    return processes;
  }

  @Override
  public Optional<ArazzoDocument> getByWorkflow(String apiId, String workflowId) {
    Selection selection = selection(apiId).orElse(Selection.ALL);
    return identifiers(apiId).stream()
        .filter(identifier -> derived(apiId, identifier, selection).containsKey(workflowId))
        .findFirst()
        .map(store::get);
  }

  @Override
  public Optional<ArazzoDocument> getDocument(String apiId, String documentId) {
    return identifiers(apiId).stream()
        .filter(
            identifier ->
                WorkflowRunner.storedDocumentId(identifier.id())
                    .equals(WorkflowRunner.storedDocumentId(documentId)))
        .findFirst()
        .map(store::get);
  }

  // empty, if the building block is not enabled
  private Optional<Selection> selection(String apiId) {
    return entityRegistry
        .getEntity(OgcApi.class, apiId)
        .flatMap(api -> api.getData().getExtension(ProcessesArazzoConfiguration.class))
        .filter(ExtensionConfiguration::isEnabled)
        .map(
            configuration ->
                new Selection(
                    configuration.getPublishedWorkflows(),
                    configuration.getServerInputs().keySet()));
  }

  // the descriptions of the API first, then the global ones
  private List<Identifier> identifiers(String apiId) {
    List<Identifier> identifiers = new ArrayList<>();
    store.identifiers().stream()
        .filter(
            identifier -> identifier.path().size() == 1 && identifier.path().get(0).equals(apiId))
        .forEach(identifiers::add);
    store.identifiers().stream()
        .filter(identifier -> identifier.path().isEmpty())
        .forEach(identifiers::add);
    return identifiers;
  }

  private Map<String, ProcessData> derived(
      String apiId, Identifier identifier, Selection selection) {
    ArazzoDocument document = store.get(identifier);
    if (Objects.isNull(document)) {
      return Map.of();
    }
    Key key = new Key(apiId, identifier);
    Derived derived = cache.get(key);
    if (Objects.isNull(derived)
        || !derived.document().equals(document)
        || !derived.selection().equals(selection)) {
      derived = new Derived(document, selection, derive(identifier, document, selection));
      cache.put(key, derived);
    }
    return derived.processes();
  }

  static Map<String, ProcessData> derive(
      Identifier identifier, ArazzoDocument document, Selection selection) {
    List<String> problems = new ArrayList<>(ArazzoChecker.problems(document));
    Map<String, ProcessData> processes = new LinkedHashMap<>();
    for (ArazzoWorkflow workflow : document.getWorkflows()) {
      if (!selection.isPublished(workflow.getWorkflowId())) {
        continue;
      }
      try {
        processes.put(
            workflow.getWorkflowId(), toProcess(document, workflow, selection.serverInputs()));
      } catch (IllegalArgumentException e) {
        problems.add(e.getMessage());
      }
    }
    if (!problems.isEmpty()) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn(
            "The workflows in '{}' are not published as processes: {}",
            identifier.asPath(),
            String.join(" ", problems));
      }
      return Map.of();
    }
    return processes;
  }

  /**
   * @throws IllegalArgumentException if the inputs cannot be described as inputs of a process
   */
  static ProcessData toProcess(ArazzoDocument document, ArazzoWorkflow workflow) {
    return toProcess(document, workflow, Set.of());
  }

  /**
   * @param serverInputs the inputs that the server sets, they are not inputs of the process
   * @throws IllegalArgumentException if the inputs cannot be described as inputs of a process
   */
  static ProcessData toProcess(
      ArazzoDocument document, ArazzoWorkflow workflow, Set<String> serverInputs) {
    ImmutableProcessData.Builder builder =
        new ImmutableProcessData.Builder()
            .id(workflow.getWorkflowId())
            .version(document.getInfo().getVersion())
            .title(workflow.getSummary().orElse(workflow.getWorkflowId()))
            .description(workflow.getDescription().or(() -> document.getInfo().getDescription()))
            .addJobControlOptions(JobControlOptions.SYNC_EXECUTE, JobControlOptions.ASYNC_EXECUTE)
            .job(new ImmutableProcessJobOptions.Builder().partials(List.of(STEP)).build())
            .readOnly(workflow.isReadOnly());

    Map<String, Object> schema = WorkflowRunner.inputSchema(document, workflow);
    checkKeywords(schema, SCHEMA_KEYWORDS, workflow, "inputs");
    List<?> required = schema.get("required") instanceof List<?> list ? list : List.of();
    if (schema.get(PROPERTIES) instanceof Map<?, ?> properties) {
      properties.forEach(
          (name, property) -> {
            String input = String.valueOf(name);
            if (serverInputs.contains(input)) {
              return;
            }
            Map<String, Object> definition = asMap(property, workflow, input);
            OgcSchema inputSchema = toSchema(definition, workflow, input);
            builder.putInputs(
                input,
                new ImmutableOgcInputDescription.Builder()
                    .title(inputSchema.getTitle())
                    .description(inputSchema.getDescription())
                    .schema(inputSchema)
                    .minOccurs(required.contains(input) ? 1 : 0)
                    .maxOccurs(1)
                    .build());
          });
    }

    // the outputs of a workflow have no schema, their values have any type
    workflow
        .getOutputs()
        .keySet()
        .forEach(
            output ->
                builder.putOutputs(
                    output,
                    new ImmutableOgcOutputDescription.Builder()
                        .schema(new ImmutableOgcSchema.Builder().build())
                        .minOccurs(0)
                        .build()));
    return builder.build();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value, ArazzoWorkflow workflow, String input) {
    if (!(value instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException(
          String.format(
              "The schema of the input '%s' of the workflow '%s' is not an object.",
              input, workflow.getWorkflowId()));
    }
    return (Map<String, Object>) map;
  }

  private static OgcSchema toSchema(
      Map<String, Object> definition, ArazzoWorkflow workflow, String input) {
    Map<String, Object> schema = withoutAnnotations(definition);
    checkKeywords(schema, SCHEMA_KEYWORDS, workflow, input);
    if (schema.get(PROPERTIES) instanceof Map<?, ?> properties) {
      Map<String, Object> cleaned = new LinkedHashMap<>();
      properties.forEach(
          (name, property) -> {
            Map<String, Object> propertySchema =
                withoutAnnotations(asMap(property, workflow, input + "." + name));
            checkKeywords(propertySchema, PROPERTY_KEYWORDS, workflow, input + "." + name);
            checkItems(propertySchema, workflow, input + "." + name);
            cleaned.put(String.valueOf(name), propertySchema);
          });
      schema.put(PROPERTIES, cleaned);
    }
    checkItems(schema, workflow, input);
    try {
      return Jobs.DEFAULT_MAPPER.convertValue(schema, OgcSchema.class);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format(
              "The schema of the input '%s' of the workflow '%s' is invalid: %s",
              input, workflow.getWorkflowId(), e.getMessage()),
          e);
    }
  }

  private static void checkItems(
      Map<String, Object> schema, ArazzoWorkflow workflow, String input) {
    if (schema.containsKey(ITEMS)) {
      Map<String, Object> items = withoutAnnotations(asMap(schema.get(ITEMS), workflow, input));
      checkKeywords(items, ITEMS_KEYWORDS, workflow, input + "[]");
      if (items.get(PROPERTIES) instanceof Map<?, ?> properties) {
        Map<String, Object> cleaned = new LinkedHashMap<>();
        properties.forEach(
            (name, property) -> {
              String where = input + "[]." + name;
              Map<String, Object> propertySchema =
                  withoutAnnotations(asMap(property, workflow, where));
              checkKeywords(propertySchema, PROPERTY_KEYWORDS, workflow, where);
              checkItems(propertySchema, workflow, where);
              cleaned.put(String.valueOf(name), propertySchema);
            });
        items.put(PROPERTIES, cleaned);
      }
      schema.put(ITEMS, items);
    }
  }

  private static Map<String, Object> withoutAnnotations(Map<String, Object> schema) {
    Map<String, Object> result = new LinkedHashMap<>(schema);
    result.keySet().removeAll(ANNOTATIONS);
    return result;
  }

  // a keyword that a process description cannot express would change the validation of the inputs
  private static void checkKeywords(
      Map<String, Object> schema, Set<String> keywords, ArazzoWorkflow workflow, String where) {
    List<String> unsupported =
        schema.keySet().stream()
            .filter(key -> !keywords.contains(key) && !ANNOTATIONS.contains(key))
            .toList();
    if (!unsupported.isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "The schema of '%s' of the workflow '%s' uses the keywords %s, which process"
                  + " descriptions do not support.",
              where, workflow.getWorkflowId(), unsupported));
    }
  }
}
