/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestAuthorizer;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ApiSecurity;
import de.ii.ogcapi.foundation.domain.EndpointExtension;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.mcp.domain.ImmutableMcpTool;
import de.ii.ogcapi.mcp.domain.McpConfiguration;
import de.ii.ogcapi.mcp.domain.McpConfiguration.McpIncludeExclude;
import de.ii.ogcapi.mcp.domain.McpTool;
import de.ii.ogcapi.processes.domain.ProcessesCoreConfiguration;
import de.ii.ogcapi.processes.domain.ProcessesExecutor;
import de.ii.ogcapi.processes.domain.model.ImmutableOgcExecute;
import de.ii.ogcapi.processes.domain.model.OgcDescriptionType;
import de.ii.ogcapi.processes.domain.model.OgcExecute;
import de.ii.ogcapi.processes.domain.model.OgcProcessSummary.JobControlOptions;
import de.ii.ogcapi.processes.domain.model.OgcSchemaAndOccurrences;
import de.ii.ogcapi.processes.domain.model.ProcessData;
import de.ii.ogcapi.processes.domain.model.ProcessRepository;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The processes that can be executed synchronously, a tool per process. Processes are only tools if
 * they are listed in {@code included.processes}. A call executes the process like a request to its
 * execution endpoint, with the same validation of the inputs and the same permissions.
 */
final class McpToolsProcesses implements McpToolSource {

  static final String PREFIX = "process_";

  private static final String EXECUTE_OPERATION = "executeProcess";
  private static final String ANY = "*";

  private final ExtensionRegistry extensionRegistry;
  private final ProcessRepository processRepository;
  private final ProcessesExecutor processesExecutor;
  private final ApiRequestAuthorizer apiRequestAuthorizer;
  private final ObjectMapper objectMapper;

  McpToolsProcesses(
      ExtensionRegistry extensionRegistry,
      ProcessRepository processRepository,
      ProcessesExecutor processesExecutor,
      ApiRequestAuthorizer apiRequestAuthorizer,
      ObjectMapper objectMapper) {
    this.extensionRegistry = extensionRegistry;
    this.processRepository = processRepository;
    this.processesExecutor = processesExecutor;
    this.apiRequestAuthorizer = apiRequestAuthorizer;
    this.objectMapper = objectMapper;
  }

  @Override
  public String getPrefix() {
    return PREFIX;
  }

  @Override
  public List<McpTool> getTools(OgcApi api, McpConfiguration configuration) {
    OgcApiDataV2 apiData = api.getData();
    if (!isEnabled(apiData)) {
      return List.of();
    }

    List<String> included =
        configuration.getIncluded().map(McpIncludeExclude::getProcesses).orElse(List.of());
    List<String> excluded =
        configuration.getExcluded().map(McpIncludeExclude::getProcesses).orElse(List.of());

    return processRepository.getAll(apiData.getId()).values().stream()
        .filter(process -> isIncluded(process, included, excluded))
        .filter(McpToolsProcesses::canExecuteSync)
        .map(process -> toTool(apiData, process))
        .toList();
  }

  // the process descriptions are values that can change without a change of the API
  @Override
  public int getFingerprint(OgcApi api) {
    return isEnabled(api.getData())
        ? processRepository.getAll(api.getData().getId()).hashCode()
        : 0;
  }

  @Override
  public Map<String, Object> call(
      OgcApi api,
      McpTool tool,
      Map<String, Object> arguments,
      Optional<ApiRequestContext> mcpRequest)
      throws IOException {
    OgcApiDataV2 apiData = api.getData();
    String processId = tool.getId().substring(PREFIX.length());
    ProcessData process =
        processRepository
            .get(apiData.getId(), processId)
            .filter(found -> isEnabled(apiData))
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "The process '%s' is not available.".formatted(processId)));

    // the inputs are validated by the executor, like the inputs of a request to the endpoint
    Map<String, Object> inputs = new LinkedHashMap<>();
    if (Objects.nonNull(arguments)) {
      arguments.forEach(
          (inputId, value) -> {
            if (Objects.nonNull(value)) {
              inputs.put(inputId, value);
            }
          });
    }
    OgcExecute executeRequest = new ImmutableOgcExecute.Builder().inputs(inputs).build();

    authorize(api, mcpRequest, executeRequest);

    long timeoutMillis =
        apiData.getExtension(ProcessesCoreConfiguration.class).orElseThrow().getSyncTimeoutMillis();

    return new LinkedHashMap<>(
        processesExecutor.executeSync(apiData.getId(), process, executeRequest, timeoutMillis));
  }

  private static boolean isEnabled(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(ProcessesCoreConfiguration.class)
        .filter(ExtensionConfiguration::isEnabled)
        .isPresent();
  }

  static boolean isIncluded(ProcessData process, List<String> included, List<String> excluded) {
    return (included.contains(ANY) || matches(process, included)) && !matches(process, excluded);
  }

  private static boolean matches(ProcessData process, List<String> ids) {
    return ids.contains(process.getId()) || process.getTitle().filter(ids::contains).isPresent();
  }

  // as for a request to the execution endpoint, a process without job control options can be
  // executed synchronously
  static boolean canExecuteSync(ProcessData process) {
    List<JobControlOptions> options = process.getJobControlOptions();
    return options.isEmpty() || options.contains(JobControlOptions.SYNC_EXECUTE);
  }

  // the MCP endpoint only requires the permission to read the API, executing a process requires
  // the permission of the execution endpoint
  private void authorize(
      OgcApi api, Optional<ApiRequestContext> mcpRequest, OgcExecute executeRequest)
      throws IOException {
    OgcApiDataV2 apiData = api.getData();
    if (apiData.getAccessControl().filter(ApiSecurity::isEnabled).isEmpty()) {
      return;
    }

    ApiRequestContext requestContext =
        mcpRequest.orElseThrow(() -> new NotAuthorizedException("Bearer realm=\"ldproxy\""));
    ApiOperation operation =
        executeOperation(apiData)
            .orElseThrow(
                () -> new NotFoundException("The execution of processes is not available."));

    apiRequestAuthorizer.checkAuthorization(
        requestContext,
        operation,
        requestContext.getUser(),
        Optional.of(objectMapper.writeValueAsBytes(executeRequest)));
  }

  private Optional<ApiOperation> executeOperation(OgcApiDataV2 apiData) {
    return extensionRegistry.getExtensionsForType(EndpointExtension.class).stream()
        .filter(endpoint -> endpoint.isEnabledForApi(apiData))
        .flatMap(endpoint -> endpoint.getDefinition(apiData).getResources().values().stream())
        .flatMap(resource -> resource.getOperations().values().stream())
        .filter(operation -> operation.getOperationId().contains(EXECUTE_OPERATION))
        .findFirst();
  }

  McpTool toTool(OgcApiDataV2 apiData, ProcessData process) {
    ObjectSchema inputSchema = new ObjectSchema();
    process
        .getInputs()
        .forEach(
            (inputId, input) -> {
              inputSchema.addProperty(inputId, toSchema(input, input));
              if (input.getMinOccurs() > 0) {
                inputSchema.addRequiredItem(inputId);
              }
            });

    // without required outputs: the structured content of a result is validated against the
    // schema, and a process may omit an output
    ObjectSchema outputSchema = new ObjectSchema();
    process
        .getOutputs()
        .forEach(
            (outputId, output) -> outputSchema.addProperty(outputId, toSchema(output, output)));

    return new ImmutableMcpTool.Builder()
        .id(PREFIX + process.getId())
        .name("Process - " + process.getTitle().orElse(process.getId()))
        // what the tool is for first, clients may only show the beginning
        .description(
            process.getDescription().or(process::getTitle).orElse("Process " + process.getId())
                + apiData.getDescription().map(desc -> System.lineSeparator() + desc).orElse(""))
        .inputSchema(inputSchema)
        .outputSchema(outputSchema)
        // a process may change data or have other effects
        .isReadOnly(false)
        .build();
  }

  // the schema of an input or output; with more than one value, the schema of an array of values
  private Schema<?> toSchema(OgcSchemaAndOccurrences occurrences, OgcDescriptionType description) {
    Schema<?> schema =
        Json.mapper()
            .convertValue(
                objectMapper.convertValue(occurrences.getSchema(), Map.class), Schema.class);

    if (occurrences.getMaxOccurs() > 1) {
      ArraySchema array = new ArraySchema();
      array.setItems(schema);
      array.setMaxItems(occurrences.getMaxOccurs());
      if (occurrences.getMinOccurs() > 1) {
        array.setMinItems(occurrences.getMinOccurs());
      }
      schema = array;
    }

    description.getTitle().ifPresent(schema::setTitle);
    description.getDescription().ifPresent(schema::setDescription);

    return schema;
  }
}
