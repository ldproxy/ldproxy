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
import de.ii.ogcapi.processes.app.parameter.QueryParameterLimitProcessList;
import de.ii.ogcapi.processes.app.parameter.QueryParameterOffsetProcessList;
import de.ii.ogcapi.processes.domain.ImmutableQueryInputProcesses;
import de.ii.ogcapi.processes.domain.ProcessesCoreConfiguration;
import de.ii.ogcapi.processes.domain.ProcessesQueriesHandler;
import de.ii.ogcapi.processes.domain.ProcessesQueriesHandler.Query;
import de.ii.ogcapi.processes.domain.format.ProcessListFormatExtension;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @title Processes
 * @path processes
 * @langEn Returns a list containing the summaries of a subset of all processes supported by this
 *     API. Supports pagination using links (first, next, prev and last) to discover all subsets.
 * @langDe Gibt eine Liste mit Zusammenfassungen einer Teilmenge aller von dieser API unterstützten
 *     Prozesse zurück. Unterstützt die Paginierung über Links (first, next, prev and last), um alle
 *     Teilmengen zu erkunden.
 * @ref:formats {@link de.ii.ogcapi.processes.domain.format.ProcessListFormatExtension}
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
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder();

    definitionBuilder
        .apiEntrypoint("processes")
        .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_PROCESSES);

    String path = "/processes";
    HttpMethods method = HttpMethods.GET;
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, path);
    String operationSummary = "Lists all available processes offered by the server";

    Optional<ProcessesCoreConfiguration> config =
        apiData.getExtension(ProcessesCoreConfiguration.class);
    Optional<String> operationDescription =
        Optional.of(
            String.format(
                """
                   This operation fetches a list of summaries of processes supported by this API. \
                   The response is a document containing a list of process summaries. \

                   To support access to all process summaries without overloading the client, the API supports paged access with links to the next, first, previous and last page if applicable. \
                   For example if more processes are available than the page size, which is controlled by the `limit` parameter (default: %d, maximum: %d, minimum: %d), a next-link will be included.""",
                config.map(ProcessesCoreConfiguration::getDefaultPageSize).get(),
                config.map(ProcessesCoreConfiguration::getMaximumPageSize).get(),
                config.map(ProcessesCoreConfiguration::getMinimumPageSize).get()));
    ImmutableOgcApiResourceSet.Builder resourceBuilderSet =
        new ImmutableOgcApiResourceSet.Builder().path(path).subResourceType("ProcessSummary");
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
  @Path("/")
  public Response getProcesses(@Context OgcApi api, @Context ApiRequestContext requestContext) {

    if (!isEnabledForApi(api.getData()))
      throw new NotFoundException("Processes are not available in this API.");

    QueryParameterSet queryParameterSet = requestContext.getQueryParameterSet();
    Optional<Integer> limit = Optional.empty();
    Optional<Integer> offset = Optional.empty();

    for (OgcApiQueryParameter queryParameter : queryParameterSet.getDefinitions()) {
      if (queryParameter instanceof QueryParameterLimitProcessList) {
        limit = ((QueryParameterLimitProcessList) queryParameter).parse(queryParameterSet);
        break;
      }
    }

    for (OgcApiQueryParameter queryParameter : queryParameterSet.getDefinitions()) {
      if (queryParameter instanceof QueryParameterOffsetProcessList) {
        offset = ((QueryParameterOffsetProcessList) queryParameter).parse(queryParameterSet);
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
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProcessesCoreConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats = extensionRegistry.getExtensionsForType(ProcessListFormatExtension.class);
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
