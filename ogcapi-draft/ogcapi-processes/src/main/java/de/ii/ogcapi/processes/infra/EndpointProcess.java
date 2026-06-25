/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.infra;

import static de.ii.ogcapi.processes.domain.ProcessesQueriesHandler.GROUP_PROCESSES_READ;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
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
import de.ii.ogcapi.processes.domain.ImmutableQueryInputProcess;
import de.ii.ogcapi.processes.domain.ProcessDescriptionFormatExtension;
import de.ii.ogcapi.processes.domain.ProcessesCoreConfiguration;
import de.ii.ogcapi.processes.domain.ProcessesQueriesHandler;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// ToDo Docs
// Note: The path parameter is processID not processID, see
// https://docs.ogc.org/DRAFTS/18-062r3.html#sc_process_description
/**
 * @title Process
 * @path processes/{processID}
 * @langEn Returns the full details of a process.
 * @langDe Liefer die gesamten Details eines Prozesses.
 * @ref:formats {@link de.ii.ogcapi.processes.domain.ProcessDescriptionFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointProcess extends Endpoint implements ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointProcess.class);
  private static final List<String> TAGS = ImmutableList.of("Process");

  private final ProcessesQueriesHandler queryHandler;

  @Inject
  public EndpointProcess(
      ExtensionRegistry extensionRegistry, ProcessesQueriesHandler queryHandler) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProcessesCoreConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats = extensionRegistry.getExtensionsForType(ProcessDescriptionFormatExtension.class);
    return formats;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(ProcessesCoreConfiguration.class)
        .filter(ProcessesCoreConfiguration::isEnabled)
        .isPresent();
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("processes")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_PROCESS);
    String path = "/processes/{processID}";
    HttpMethods method = HttpMethods.GET;
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, path);
    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);
    if (pathParameters.stream().noneMatch(param -> "processID".equals(param.getName()))) {
      LOGGER.error(
          "WIP Path parameter 'processID' missing for resource at path '"
              + path
              + "'. The GET method will not be available.");
    } else {
      String operationSummary = "Get the full description of a process WIP";
      Optional<String> operationDescription = Optional.of("WIP.");
      ImmutableOgcApiResourceAuxiliary.Builder resourceBuilder =
          new ImmutableOgcApiResourceAuxiliary.Builder().path(path).pathParameters(pathParameters);
      ApiOperation.getResource(
              apiData,
              path,
              false,
              queryParameters,
              ImmutableList.of(),
              getResponseContent(apiData),
              operationSummary,
              operationDescription,
              Optional.empty(),
              getOperationId("getProcess"),
              GROUP_PROCESSES_READ,
              TAGS,
              ProcessesCoreBuildingBlock.MATURITY,
              ProcessesCoreBuildingBlock.SPEC)
          .ifPresent(operation -> resourceBuilder.putOperations(method.name(), operation));
      definitionBuilder.putResources(path, resourceBuilder.build());
    }

    return definitionBuilder.build();
  }

  /**
   * retrieve one specific process by id
   *
   * @param processID the local identifier of a specific process
   * @return the full process description
   */
  @Path("/{processID}")
  @GET
  public Response getProcess(
      @PathParam("processID") String processID,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext) {

    if (!isEnabledForApi(api.getData()))
      throw new NotFoundException("Processes are not available in this API.");

    checkPathParameter(
        extensionRegistry, api.getData(), "/processes/{processID}", "processID", processID);

    ProcessesQueriesHandler.QueryInputProcess queryInput =
        new ImmutableQueryInputProcess.Builder()
            .from(getGenericQueryInput(api.getData()))
            .processId(processID)
            .build();

    return queryHandler.handle(ProcessesQueriesHandler.Query.PROCESS, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler);
  }
}
