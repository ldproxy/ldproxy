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
import de.ii.ogcapi.processes.domain.ImmutableQueryInputJob;
import de.ii.ogcapi.processes.domain.JobQueriesHandler;
import de.ii.ogcapi.processes.domain.ProcessesCoreConfiguration;
import de.ii.ogcapi.processes.domain.format.StatusInfoFormatExtension;
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

/**
 * @title Job
 * @path jobs/{jobId}
 * @langEn Returns the complete status of a specific job.
 * @langDe Gibt den gesamten Status eines bestimmten Jobs zurück.
 * @ref:formats {@link de.ii.ogcapi.processes.domain.format.StatusInfoFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointJob extends Endpoint implements ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointJob.class);
  private static final List<String> TAGS = ImmutableList.of("Jobs");

  private final JobQueriesHandler queryHandler;

  @Inject
  public EndpointJob(ExtensionRegistry extensionRegistry, JobQueriesHandler queryHandler) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {

    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("jobs")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_JOB);

    String path = "/jobs/{jobId}";
    HttpMethods method = HttpMethods.GET;

    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);

    if (pathParameters.stream().noneMatch(param -> "jobId".equals(param.getName()))) {
      LOGGER.error(
          "Path parameter 'jobId' missing for resource at path '{}'. The GET method will not be available.",
          path);
    } else {
      List<OgcApiQueryParameter> queryParameters =
          getQueryParameters(extensionRegistry, apiData, path);

      String operationSummary = "Status of a specific job";
      Optional<String> operationDescription =
          Optional.of("Returns the status information for the job with the specified jobId.");

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
              getOperationId("getJob"),
              GROUP_JOBS_READ,
              TAGS,
              ProcessesCoreBuildingBlock.MATURITY,
              ProcessesCoreBuildingBlock.SPEC)
          .ifPresent(operation -> resourceBuilder.putOperations(method.name(), operation));
      definitionBuilder.putResources(path, resourceBuilder.build());
    }

    return definitionBuilder.build();
  }

  @GET
  @Path("/{jobId}")
  public Response getJob(
      @PathParam("jobId") String jobId,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext) {

    if (!isEnabledForApi(api.getData()))
      throw new NotFoundException("Processes are not available in this API.");

    checkPathParameter(extensionRegistry, api.getData(), "/jobs/{jobId}", "jobId", jobId);

    JobQueriesHandler.QueryInputJob queryInput =
        new ImmutableQueryInputJob.Builder()
            .from(getGenericQueryInput(api.getData()))
            .jobId(jobId)
            .build();

    return queryHandler.handle(JobQueriesHandler.Query.JOB, queryInput, requestContext);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProcessesCoreConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats = extensionRegistry.getExtensionsForType(StatusInfoFormatExtension.class);
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
