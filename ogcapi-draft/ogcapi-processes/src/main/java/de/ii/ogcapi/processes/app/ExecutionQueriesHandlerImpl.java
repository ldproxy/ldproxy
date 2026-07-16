/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.HeaderCaching;
import de.ii.ogcapi.foundation.domain.HeaderContentDisposition;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.processes.app.format.StatusInfoLinksGenerator;
import de.ii.ogcapi.processes.domain.ExecutionQueriesHandler;
import de.ii.ogcapi.processes.domain.ProcessesExecutor;
import de.ii.ogcapi.processes.domain.format.ResultsFormatExtension;
import de.ii.ogcapi.processes.domain.format.StatusInfoFormatExtension;
import de.ii.ogcapi.processes.domain.model.Process;
import de.ii.ogcapi.processes.domain.model.ProcessRepository;
import de.ii.ogcapi.processes.domain.model.ProcessSummary.JobControlOptions;
import de.ii.ogcapi.processes.domain.model.StatusInfo;
import de.ii.ogcapi.processes.domain.model.ogc.ImmutableOgcResults;
import de.ii.ogcapi.processes.domain.model.ogc.ImmutableOgcStatusInfo;
import de.ii.ogcapi.processes.domain.model.ogc.OgcExecute;
import de.ii.ogcapi.processes.domain.model.ogc.OgcResults;
import de.ii.ogcapi.processes.domain.model.ogc.OgcStatusInfo;
import de.ii.xtraplatform.base.domain.Jackson;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotAcceptableException;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
@AutoBind
public class ExecutionQueriesHandlerImpl extends AbstractVolatileComposed
    implements ExecutionQueriesHandler {

  private final ProcessRepository processRepository;
  private final ProcessesExecutor processesExecutor;
  private final Map<ExecutionQueriesHandler.Query, QueryHandler<? extends QueryInput>>
      queryHandlers;
  private final ExtensionRegistry extensionRegistry;
  private final ObjectMapper mapper;
  private final I18n i18n;

  @Inject
  public ExecutionQueriesHandlerImpl(
      I18n i18n,
      ExtensionRegistry extensionRegistry,
      ProcessRepository processRepository,
      ProcessesExecutor processesExecutor,
      VolatileRegistry volatileRegistry,
      Jackson jackson) {
    super(ExecutionQueriesHandler.class.getSimpleName(), volatileRegistry, true);
    this.i18n = i18n;
    this.extensionRegistry = extensionRegistry;
    this.processRepository = processRepository;
    this.processesExecutor = processesExecutor;

    this.mapper = jackson.getDefaultObjectMapper();

    this.queryHandlers =
        ImmutableMap.of(
            Query.EXECUTE, QueryHandler.with(QueryInputExecution.class, this::executionResponse));

    onVolatileStart();

    addSubcomponent(processRepository);

    onVolatileStarted();
  }

  @Override
  public Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers() {
    return queryHandlers;
  }

  private Response executionResponse(
      QueryInputExecution queryInput, ApiRequestContext requestContext) {

    final OgcExecute executeRequest;
    try {
      executeRequest = mapper.readValue(queryInput.getRequestBody(), OgcExecute.class);
    } catch (IOException e) {
      throw new IllegalArgumentException("Could not parse request body: " + e.getMessage(), e);
    }

    String processId = queryInput.getProcessId();
    Process process = processRepository.getDirect(processId);
    List<JobControlOptions> jobControlOptions = process.getJobControlOptions();
    boolean async = false;

    /*
     * Cases:
     * 1. jobControlOptions is empty: execute synchronously
     * 2. process can be executed in either mode: execute asynchronously if prefered, synchronously else
     * 3. process can only be executed asynchronously: execute asynchronously
     * 4. process can only be executed synchronously: execute synchronously
     */
    if (jobControlOptions.contains(JobControlOptions.SYNC_EXECUTE)
        && jobControlOptions.contains(JobControlOptions.ASYNC_EXECUTE)) {
      async = queryInput.getPreferAsync();
    } else if (jobControlOptions.contains(JobControlOptions.ASYNC_EXECUTE)) {
      async = true;
    }

    if (!async) return executionResponseSync(queryInput, requestContext, processId, executeRequest);

    return executionResponseAsync(queryInput, requestContext, processId, executeRequest);
  }

  private Response executionResponseSync(
      QueryInputExecution queryInput,
      ApiRequestContext requestContext,
      String processId,
      OgcExecute executeRequest) {

    OgcApi api = requestContext.getApi();

    ResultsFormatExtension outputFormat =
        api.getOutputFormat(
                ResultsFormatExtension.class, requestContext.getMediaType(), Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    Map<String, Object> processResults = processesExecutor.executeSync(processId, executeRequest);
    OgcResults results =
        new ImmutableOgcResults.Builder().additionalProperties(processResults).build();

    return prepareSuccessResponse(
            requestContext,
            null,
            HeaderCaching.of(null, null, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format("%s.%s", processId, outputFormat.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(outputFormat.getEntity(results, api, requestContext))
        .build();
  }

  private Response executionResponseAsync(
      QueryInputExecution queryInput,
      ApiRequestContext requestContext,
      String processId,
      OgcExecute executeRequest) {

    OgcApi api = requestContext.getApi();

    StatusInfoFormatExtension outputFormat =
        api.getOutputFormat(
                StatusInfoFormatExtension.class, requestContext.getMediaType(), Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    StatusInfo statusInfo = processesExecutor.executeAsync(processId, executeRequest);

    final StatusInfoLinksGenerator linkGenerator = new StatusInfoLinksGenerator();
    List<Link> links =
        linkGenerator.generateLinks(
            requestContext.getUriCustomizer(), i18n, requestContext.getLanguage(), statusInfo, 3);

    OgcStatusInfo ogcStatusInfo =
        new ImmutableOgcStatusInfo.Builder().from(statusInfo).links(links).build();

    return prepareSuccessResponse(
            requestContext,
            queryInput.getIncludeLinkHeader() ? links : null,
            HeaderCaching.of(null, null, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format("%s.%s", processId, outputFormat.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(outputFormat.getEntity(ogcStatusInfo, api, requestContext))
        .build();
  }
}
