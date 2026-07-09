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
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.processes.domain.ExecutionQueriesHandler;
import de.ii.ogcapi.processes.domain.ProcessesExecutor;
import de.ii.ogcapi.processes.domain.ProcessesExecutor.StatusCode;
import de.ii.ogcapi.processes.domain.format.StatusInfoFormatExtension;
import de.ii.ogcapi.processes.domain.model.ProcessRepository;
import de.ii.ogcapi.processes.domain.model.rep.ImmutableOgcStatusInfo;
import de.ii.ogcapi.processes.domain.model.rep.OgcExecute;
import de.ii.ogcapi.processes.domain.model.rep.OgcStatusInfo;
import de.ii.xtraplatform.base.domain.Jackson;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotAcceptableException;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.text.MessageFormat;
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
      ProcessRepository repository,
      ProcessesExecutor processesExecutor,
      VolatileRegistry volatileRegistry,
      Jackson jackson) {
    super(ExecutionQueriesHandler.class.getSimpleName(), volatileRegistry, true);
    this.i18n = i18n;
    this.extensionRegistry = extensionRegistry;
    this.processRepository = repository;
    this.processesExecutor = processesExecutor;

    this.mapper = jackson.getDefaultObjectMapper();

    this.queryHandlers =
        ImmutableMap.of(
            Query.EXECUTE, QueryHandler.with(QueryInputExecution.class, this::executionResponse));

    onVolatileStart();

    addSubcomponent(repository);

    onVolatileStarted();
  }

  @Override
  public Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers() {
    return queryHandlers;
  }

  private Response executionResponse(
      QueryInputExecution queryInput, ApiRequestContext requestContext) {
    OgcApi api = requestContext.getApi();
    String processId = queryInput.getProcessId();

    StatusInfoFormatExtension outputFormat =
        api.getOutputFormat(
                StatusInfoFormatExtension.class, requestContext.getMediaType(), Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    final OgcExecute executeRequest;
    try {
      executeRequest = mapper.readValue(queryInput.getRequestBody(), OgcExecute.class);
    } catch (IOException e) {
      throw new IllegalArgumentException("Could not parse request body: " + e.getMessage(), e);
    }

    String jobId;
    if (executeRequest.getInputs().isPresent()) {
      jobId = processesExecutor.execute(processId, executeRequest.getInputs().get());
    } else {
      jobId = processesExecutor.execute(processId);
    }

    OgcStatusInfo statusInfo =
        new ImmutableOgcStatusInfo.Builder().id(jobId).status(StatusCode.ACCEPTED).build();

    return prepareSuccessResponse(
            requestContext,
            null,
            HeaderCaching.of(null, null, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format("%s.%s", processId, outputFormat.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(outputFormat.getEntity(statusInfo, api, requestContext))
        .build();
  }
}
