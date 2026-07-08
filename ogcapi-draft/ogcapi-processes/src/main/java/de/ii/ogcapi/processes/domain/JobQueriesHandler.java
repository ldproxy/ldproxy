/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain;

import de.ii.ogcapi.foundation.domain.PermissionGroup;
import de.ii.ogcapi.foundation.domain.PermissionGroup.Base;
import de.ii.ogcapi.foundation.domain.QueriesHandler;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryIdentifier;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import java.util.Map;
import org.immutables.value.Value;

public interface JobQueriesHandler extends QueriesHandler<JobQueriesHandler.Query>, Volatile2 {

  String GROUP_JOBS = "jobs";
  PermissionGroup GROUP_JOBS_READ = PermissionGroup.of(Base.READ, GROUP_JOBS, "access jobs");

  @Override
  Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers();

  enum Query implements QueryIdentifier {
    JOB,
    RESULTS
  }

  @Value.Immutable
  interface QueryInputJob extends QueryInput {

    String getJobId();
  }

  @Value.Immutable
  interface QueryInputResults extends QueryInput {
    String getJobId();
  }
}
