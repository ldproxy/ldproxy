/*
 * Copyright 2026 interactive instruments GmbH
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
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiResourceSet;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.processes.app.ProcessesCoreBuildingBlock;
import de.ii.ogcapi.processes.app.parameter.QueryParameterLimitProcesses;
import de.ii.ogcapi.processes.app.parameter.QueryParameterOffsetProcesses;
import de.ii.ogcapi.processes.domain.ImmutableQueryInputProcesses;
import de.ii.ogcapi.processes.domain.ProcessDescriptionsFormatExtension;
import de.ii.ogcapi.processes.domain.ProcessesCoreConfiguration;
import de.ii.ogcapi.processes.domain.ProcessesQueriesHandler;
import de.ii.ogcapi.processes.domain.ProcessesQueriesHandler.Query;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// ToDo Docs (dont forget limit parameter)
/**
 * @title Processes
 * @path processes
 * @langEn The URIs of all processes supported by the API.
 * @langDe Die URIs aller von der API unterstützten Prozesse.
 * @ref:formats {@link de.ii.ogcapi.processes.domain.ProcessDescriptionsFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointProcesses extends Endpoint implements ApiExtensionHealth {

  private static final List<String> TAGS = ImmutableList.of("Processes");

  private final ProcessesQueriesHandler queryHandler;

  @Inject
  public EndpointProcesses(
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
      formats = extensionRegistry.getExtensionsForType(ProcessDescriptionsFormatExtension.class);
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
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_PROCESSES);
    String path = "/processes";
    HttpMethods method = HttpMethods.GET;
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, path);
    String operationSummary = "processes list";
    Optional<String> operationDescription =
        Optional.of(
            "The URIs of all processes supported by the server. "
                + "For each processes the id, a title and the description is provided.");
    ImmutableOgcApiResourceSet.Builder resourceBuilderSet =
        new ImmutableOgcApiResourceSet.Builder().path(path).subResourceType("Process Description");
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
            getOperationId("getProcesses"),
            GROUP_PROCESSES_READ,
            TAGS,
            ProcessesCoreBuildingBlock.MATURITY,
            ProcessesCoreBuildingBlock.SPEC)
        .ifPresent(operation -> resourceBuilderSet.putOperations(method.name(), operation));
    definitionBuilder.putResources(path, resourceBuilderSet.build());

    return definitionBuilder.build();
  }

  @GET
  public Response getProcesses(@Context OgcApi api, @Context ApiRequestContext requestContext) {

    if (!isEnabledForApi(api.getData()))
      throw new NotFoundException("Processes are not available in this API.");

    QueryParameterSet queryParameterSet = requestContext.getQueryParameterSet();
    Optional<Integer> limit = Optional.empty();
    Optional<Integer> offset = Optional.empty();

    for (OgcApiQueryParameter queryParameter : queryParameterSet.getDefinitions()) {
      if (queryParameter instanceof QueryParameterLimitProcesses) {
        limit = ((QueryParameterLimitProcesses) queryParameter).parse(queryParameterSet);
        break;
      }
    }

    for (OgcApiQueryParameter queryParameter : queryParameterSet.getDefinitions()) {
      if (queryParameter instanceof QueryParameterOffsetProcesses) {
        offset = ((QueryParameterOffsetProcesses) queryParameter).parse(queryParameterSet);
        break;
      }
    }

    ProcessesQueriesHandler.QueryInputProcesses queryInput =
        new ImmutableQueryInputProcesses.Builder()
            .from(getGenericQueryInput(api.getData()))
            // We just .get() because of the default values
            .offset(offset.get())
            .limit(limit.get())
            .defaultLimit(
                api.getData()
                    .getExtension(ProcessesCoreConfiguration.class)
                    .get()
                    .getDefaultPageSize())
            .build();

    return queryHandler.handle(Query.PROCESSES, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler);
  }
}
