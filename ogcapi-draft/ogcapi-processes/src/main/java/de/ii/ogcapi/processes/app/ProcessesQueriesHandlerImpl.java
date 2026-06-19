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
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.processes.domain.ProcessDescriptionRepository;
import de.ii.ogcapi.processes.domain.ProcessesQueriesHandler;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Singleton
@AutoBind
public class ProcessesQueriesHandlerImpl extends AbstractVolatileComposed
    implements ProcessesQueriesHandler {

  private final ProcessDescriptionRepository processDescriptionRepository;
  private final Map<Query, QueryHandler<? extends QueryInput>> queryHandlers;
  private final ExtensionRegistry extensionRegistry;
  private final I18n i18n;

  @Inject
  public ProcessesQueriesHandlerImpl(
      I18n i18n,
      ExtensionRegistry extensionRegistry,
      ProcessDescriptionRepository repository,
      VolatileRegistry volatileRegistry) {
    super(ProcessesQueriesHandler.class.getSimpleName(), volatileRegistry, true);
    this.i18n = i18n;
    this.extensionRegistry = extensionRegistry;
    this.processDescriptionRepository = repository;

    this.queryHandlers =
        ImmutableMap.of(
            Query.PROCESSES,
            QueryHandler.with(QueryInputProcesses.class, this::getProcessesResponse));

    onVolatileStart();

    addSubcomponent(repository);

    onVolatileStarted();
  }

  @Override
  public Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers() {
    return queryHandlers;
  }

  private Response getProcessesResponse(
      QueryInputProcesses queryInput, ApiRequestContext requestContext) {
    return Response.ok()
        .type("application/json")
        .entity(Map.of("message", queryInput.getMessage()))
        .build();
  }
}
