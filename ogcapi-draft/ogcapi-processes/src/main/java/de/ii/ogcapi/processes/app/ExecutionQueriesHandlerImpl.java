/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.processes.domain.ExecutionQueriesHandler;
import de.ii.ogcapi.processes.domain.ProcessesExecutor;
import de.ii.ogcapi.processes.domain.model.ProcessRepository;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Singleton
@AutoBind
public class ExecutionQueriesHandlerImpl extends AbstractVolatileComposed
    implements ExecutionQueriesHandler {

  private final ProcessRepository processRepository;
  private final ProcessesExecutor processesExecutor;
  private final Map<ExecutionQueriesHandler.Query, QueryHandler<? extends QueryInput>>
      queryHandlers;
  private final ExtensionRegistry extensionRegistry;
  private final I18n i18n;

  @Inject
  public ExecutionQueriesHandlerImpl(
      I18n i18n,
      ExtensionRegistry extensionRegistry,
      ProcessRepository repository,
      ProcessesExecutor processesExecutor,
      VolatileRegistry volatileRegistry) {
    super(ExecutionQueriesHandler.class.getSimpleName(), volatileRegistry, true);
    this.i18n = i18n;
    this.extensionRegistry = extensionRegistry;
    this.processRepository = repository;
    this.processesExecutor = processesExecutor;

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

    String jobId = processesExecutor.execute(processId);

    return Response.ok().entity(jobId).build();
  }
}
