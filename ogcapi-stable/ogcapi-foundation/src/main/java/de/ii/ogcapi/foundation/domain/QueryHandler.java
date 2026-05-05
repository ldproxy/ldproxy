/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import jakarta.ws.rs.core.Response;
import java.util.function.BiFunction;
import org.immutables.value.Value;

@Value.Immutable
public abstract class QueryHandler<U extends QueryInput> {

  public static <U extends QueryInput> QueryHandler<U> with(
      Class<U> queryInputType, BiFunction<U, ApiRequestContext, Response> queryHandler) {
    return new ImmutableQueryHandler.Builder<U>()
        .queryInputType(queryInputType)
        .queryHandler(queryHandler)
        .build();
  }

  protected abstract Class<U> getQueryInputType();

  protected abstract BiFunction<U, ApiRequestContext, Response> getQueryHandler();

  public boolean isValidInput(QueryInput queryInput) {
    return getQueryInputType().isAssignableFrom(queryInput.getClass());
  }

  public Response handle(QueryInput queryInput, ApiRequestContext requestContext) {
    return getQueryHandler().apply(getQueryInputType().cast(queryInput), requestContext);
  }
}
