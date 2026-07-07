/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain;

import static de.ii.ogcapi.processes.domain.ProcessesQueriesHandler.GROUP_PROCESSES;

import de.ii.ogcapi.foundation.domain.PermissionGroup;
import de.ii.ogcapi.foundation.domain.PermissionGroup.Base;
import de.ii.ogcapi.foundation.domain.QueriesHandler;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryIdentifier;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import java.io.InputStream;
import java.util.Map;
import org.immutables.value.Value;

public interface ExecutionQueriesHandler
    extends QueriesHandler<ExecutionQueriesHandler.Query>, Volatile2 {

  // ToDo Add EXECUTE Permission for PermissionGroup ?
  PermissionGroup GROUP_PROCESSES_EXECUTE =
      PermissionGroup.of(Base.READ, GROUP_PROCESSES, "execute processes");

  @Override
  Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers();

  enum Query implements QueryIdentifier {
    EXECUTE
  }

  @Value.Immutable
  interface QueryInputExecution extends QueryInput {

    String getProcessId();

    InputStream getRequestBody();
  }
}
