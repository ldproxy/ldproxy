/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.infra;

import static de.ii.ogcapi.processes.domain.ExecutionQueriesHandler.GROUP_PROCESSES_EXECUTE;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiResourceAuxiliary;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.processes.app.ProcessesCoreBuildingBlock;
import de.ii.ogcapi.processes.domain.ExecutionQueriesHandler;
import de.ii.ogcapi.processes.domain.ImmutableQueryInputExecution;
import de.ii.ogcapi.processes.domain.ProcessesCoreConfiguration;
import de.ii.ogcapi.processes.domain.format.ExecuteFormatExtension;
import de.ii.ogcapi.processes.domain.format.ResultsFormatExtension;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// ToDo docs
/** */
@Singleton
@AutoBind
public class EndpointExecute extends Endpoint implements ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointExecute.class);
  private static final List<String> TAGS = ImmutableList.of("EXECUTE");

  private final ExecutionQueriesHandler queryHandler;
  private List<? extends FormatExtension> resourceFormats;
  private List<? extends FormatExtension> requestFormats;

  @Inject
  public EndpointExecute(
      ExtensionRegistry extensionRegistry, ExecutionQueriesHandler queryHandler) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {

    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("processes")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_EXECUTE);

    String path = "/processes/{processId}/execution";
    HttpMethods method = HttpMethods.POST;

    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);

    if (pathParameters.stream().noneMatch(param -> "processId".equals(param.getName()))) {
      LOGGER.error(
          "Path parameter 'processId' missing for resource at path '"
              + path
              + "'. The POST method will not be available.");
    } else {
      List<OgcApiQueryParameter> queryParameters =
          getQueryParameters(extensionRegistry, apiData, path);

      String operationSummary = "TODO SUMMARY";
      Optional<String> operationDescription = Optional.of("TODO DESCRIPTION");

      ImmutableOgcApiResourceAuxiliary.Builder resourceBuilder =
          new ImmutableOgcApiResourceAuxiliary.Builder().path(path).pathParameters(pathParameters);

      Map<MediaType, ApiMediaTypeContent> requestContent = getRequestContent(apiData);

      ApiOperation.of(
              path,
              HttpMethods.POST,
              requestContent,
              queryParameters,
              ImmutableList.of(),
              operationSummary,
              operationDescription,
              Optional.empty(),
              getOperationId("executeProcess"),
              GROUP_PROCESSES_EXECUTE,
              TAGS,
              ProcessesCoreBuildingBlock.MATURITY,
              ProcessesCoreBuildingBlock.SPEC,
              false)
          .ifPresent(operation -> resourceBuilder.putOperations(method.name(), operation));

      definitionBuilder.putResources(path, resourceBuilder.build());
    }

    return definitionBuilder.build();
  }

  // ToDo Docs
  @Path("/{processId}/execution")
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(
      summary = "Execute a process",
      description = "Start the execution of a process with specific inputs and output selection.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Process successfully started"),
  })
  public Response executeProcess(
      @PathParam("processId") String processId,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @Context HttpHeaders headers,
      InputStream requestBody) {

    if (!isEnabledForApi(api.getData()))
      throw new NotFoundException("Processes are not available in this API.");

    checkPathParameter(
        extensionRegistry,
        api.getData(),
        "/processes/{processId}/execution",
        "processId",
        processId);

    String prefer = headers.getHeaderString("Prefer");
    boolean preferAsync =
        prefer != null
            && Arrays.stream(prefer.split(","))
                .anyMatch(p -> ("respond-async".equals(p.trim()) || "wait".equals(p.trim())));

    ExecutionQueriesHandler.QueryInputExecution queryInput =
        new ImmutableQueryInputExecution.Builder()
            .from(getGenericQueryInput(api.getData()))
            .processId(processId)
            .requestBody(requestBody)
            .preferAsync(preferAsync)
            .build();

    return queryHandler.handle(ExecutionQueriesHandler.Query.EXECUTE, queryInput, requestContext);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProcessesCoreConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (resourceFormats == null)
      resourceFormats = extensionRegistry.getExtensionsForType(ResultsFormatExtension.class);
    return resourceFormats;
  }

  @Override
  public List<? extends FormatExtension> getRequestFormats() {
    if (requestFormats == null)
      requestFormats = extensionRegistry.getExtensionsForType(ExecuteFormatExtension.class);
    return requestFormats;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(ProcessesCoreConfiguration.class)
        .filter(ProcessesCoreConfiguration::isEnabled)
        .isPresent();
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler);
  }
}
