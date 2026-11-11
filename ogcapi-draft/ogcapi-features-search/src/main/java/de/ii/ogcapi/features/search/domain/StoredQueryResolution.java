/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import java.util.Map;

/**
 * Resolves a stored query with parameter values to an executable query expression, for a caller
 * without an HTTP request, e.g. a process that runs a stored query. The values are checked as those
 * of a GET request of {@code /search/{queryId}}; the format is chosen by the caller.
 */
// a service with an implementation that is bound by the platform, not a target of lambdas
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface StoredQueryResolution {

  /**
   * @param api the API
   * @param queryId the id of the stored query
   * @param parameters the parameter values as in a GET request, without {@code f}
   * @return the executable query and the evaluated parameters
   * @throws IllegalArgumentException with all problems found, if the query does not exist, a
   *     parameter is unknown or a value is invalid
   */
  Resolved resolve(OgcApi api, String queryId, Map<String, String> parameters);

  /** A resolved stored query: the executable query and the evaluated parameters. */
  final class Resolved {
    private final QueryExpression query;
    private final QueryParameterSet queryParameterSet;

    public Resolved(QueryExpression query, QueryParameterSet queryParameterSet) {
      this.query = query;
      this.queryParameterSet = queryParameterSet;
    }

    public QueryExpression getQuery() {
      return query;
    }

    public QueryParameterSet getQueryParameterSet() {
      return queryParameterSet;
    }
  }
}
