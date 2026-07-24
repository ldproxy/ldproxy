/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.infra;

import static de.ii.ogcapi.processes.domain.JobQueriesHandler.GROUP_JOBS_READ;

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
import de.ii.ogcapi.processes.domain.ImmutableQueryInputResults;
import de.ii.ogcapi.processes.domain.ImmutableQueryInputResultsSpecific;
import de.ii.ogcapi.processes.domain.ImmutableQueryInputResultsSpecificN;
import de.ii.ogcapi.processes.domain.JobQueriesHandler;
import de.ii.ogcapi.processes.domain.JobQueriesHandler.Query;
import de.ii.ogcapi.processes.domain.ProcessesCoreConfiguration;
import de.ii.ogcapi.processes.domain.format.ResultsFormatExtension;
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

// ToDo More Details in docs
/**
 * @title Job Results
 * @path jobs/{jobId}/results, jobs/{jobId}/results/{outputId}, jobs/{jobId}/results/{outputId}/{N}
 * @langEn Returns the complete or specific results of a specific job.
 * @langDe Gibt die Ergebnisse eines bestimmten Jobs zurück.
 */
@Singleton
@AutoBind
public class EndpointResults extends Endpoint implements ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointResults.class);
  private static final List<String> TAGS = ImmutableList.of("Jobs");

  private final JobQueriesHandler queryHandler;

  @Inject
  public EndpointResults(ExtensionRegistry extensionRegistry, JobQueriesHandler queryHandler) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {

    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("jobs")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_JOB_RESULTS);

    computeDefinitionResults(apiData, definitionBuilder);
    computeDefinitionResultsSpecific(apiData, definitionBuilder);
    computeDefinitionResultsSpecificN(apiData, definitionBuilder);

    return definitionBuilder.build();
  }

  private void computeDefinitionResults(
      OgcApiDataV2 apiData, ImmutableApiEndpointDefinition.Builder definitionBuilder) {
    String path = "/jobs/{jobId}/results";
    HttpMethods method = HttpMethods.GET;

    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);

    if (pathParameters.stream().noneMatch(param -> "jobId".equals(param.getName()))) {
      LOGGER.error(
          "Path parameter 'jobId' missing for resource at path '{}'. The GET method will not be available.",
          path);
    } else {
      List<OgcApiQueryParameter> queryParameters =
          getQueryParameters(extensionRegistry, apiData, path);

      String operationSummary = "Retrieve all requested processing results";
      Optional<String> operationDescription =
          Optional.of(
              """
                    Returns the results for the job with the specified by `jobId`.\
                    The response depends on the negotiated response type and the type of the results.
                    """);

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
              getOperationId("getJobResults"),
              GROUP_JOBS_READ,
              TAGS,
              ProcessesCoreBuildingBlock.MATURITY,
              ProcessesCoreBuildingBlock.SPEC)
          .ifPresent(operation -> resourceBuilder.putOperations(method.name(), operation));
      definitionBuilder.putResources(path, resourceBuilder.build());
    }
  }

  private void computeDefinitionResultsSpecific(
      OgcApiDataV2 apiData, ImmutableApiEndpointDefinition.Builder definitionBuilder) {
    String path = "/jobs/{jobId}/results/{outputId}";
    HttpMethods method = HttpMethods.GET;

    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);

    if (pathParameters.stream().noneMatch(param -> "jobId".equals(param.getName()))) {
      LOGGER.error(
          "Path parameter 'jobId' missing for resource at path '{}'. The GET method will not be available.",
          path);
    } else {
      List<OgcApiQueryParameter> queryParameters =
          getQueryParameters(extensionRegistry, apiData, path);

      String operationSummary = "Retrieve retrieve a specific processing result";
      Optional<String> operationDescription =
          Optional.of(
              """
                    Returns the a specific process output value identified by `outpudId`. \
                    The response depends on the negotiated response type and the type of the specific value.
                    """);

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
              getOperationId("getJobResultsSpecific"),
              GROUP_JOBS_READ,
              TAGS,
              ProcessesCoreBuildingBlock.MATURITY,
              ProcessesCoreBuildingBlock.SPEC)
          .ifPresent(operation -> resourceBuilder.putOperations(method.name(), operation));
      definitionBuilder.putResources(path, resourceBuilder.build());
    }
  }

  private void computeDefinitionResultsSpecificN(
      OgcApiDataV2 apiData, ImmutableApiEndpointDefinition.Builder definitionBuilder) {
    String path = "/jobs/{jobId}/results/{outputId}/{N}";
    HttpMethods method = HttpMethods.GET;

    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);

    if (pathParameters.stream().noneMatch(param -> "jobId".equals(param.getName()))) {
      LOGGER.error(
          "Path parameter 'jobId' missing for resource at path '{}'. The GET method will not be available.",
          path);
    } else {
      List<OgcApiQueryParameter> queryParameters =
          getQueryParameters(extensionRegistry, apiData, path);

      String operationSummary = "Retrieve the Nth value of a specific processing result";
      Optional<String> operationDescription =
          Optional.of(
              """
                    Returns the Nth value of a specific processing result identified by `outpudId` and `N`. \
                    The response depends on the negotiated response type and the type of the specific value.
                    """);

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
              getOperationId("getJobResultsSpecificN"),
              GROUP_JOBS_READ,
              TAGS,
              ProcessesCoreBuildingBlock.MATURITY,
              ProcessesCoreBuildingBlock.SPEC)
          .ifPresent(operation -> resourceBuilder.putOperations(method.name(), operation));
      definitionBuilder.putResources(path, resourceBuilder.build());
    }
  }

  @GET
  @Path("/{jobId}/results")
  public Response getJobResults(
      @PathParam("jobId") String jobId,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext) {

    if (!isEnabledForApi(api.getData()))
      throw new NotFoundException("Processes are not available in this API.");

    checkPathParameter(extensionRegistry, api.getData(), "/jobs/{jobId}/results", "jobId", jobId);

    JobQueriesHandler.QueryInputResults queryInput =
        new ImmutableQueryInputResults.Builder()
            .from(getGenericQueryInput(api.getData()))
            .jobId(jobId)
            .build();

    return queryHandler.handle(JobQueriesHandler.Query.RESULTS, queryInput, requestContext);
  }

  @GET
  @Path("/{jobId}/results/{outputId}")
  public Response getJobResultsSpecific(
      @PathParam("jobId") String jobId,
      @PathParam("outputId") String outputId,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext) {

    if (!isEnabledForApi(api.getData()))
      throw new NotFoundException("Processes are not available in this API.");

    checkPathParameter(extensionRegistry, api.getData(), "/jobs/{jobId}/results", "jobId", jobId);

    JobQueriesHandler.QueryInputResultsSpecific queryInput =
        new ImmutableQueryInputResultsSpecific.Builder()
            .from(getGenericQueryInput(api.getData()))
            .jobId(jobId)
            .outputId(outputId)
            .build();

    return queryHandler.handle(Query.RESULTS_SPECIFIC, queryInput, requestContext);
  }

  @GET
  @Path("/{jobId}/results/{outputId}/{N}")
  public Response getJobResultsSpecificN(
      @PathParam("jobId") String jobId,
      @PathParam("outputId") String outputId,
      @PathParam("N") int indexN,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext) {

    if (!isEnabledForApi(api.getData()))
      throw new NotFoundException("Processes are not available in this API.");

    checkPathParameter(extensionRegistry, api.getData(), "/jobs/{jobId}/results", "jobId", jobId);

    JobQueriesHandler.QueryInputResultsSpecificN queryInput =
        new ImmutableQueryInputResultsSpecificN.Builder()
            .from(getGenericQueryInput(api.getData()))
            .jobId(jobId)
            .outputId(outputId)
            .indexN(indexN)
            .build();

    return queryHandler.handle(Query.RESULTS_SPECIFIC_N, queryInput, requestContext);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProcessesCoreConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats = extensionRegistry.getExtensionsForType(ResultsFormatExtension.class);
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
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler);
  }
}
