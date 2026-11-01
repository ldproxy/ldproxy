/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain.async;

import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiResponse;
import de.ii.ogcapi.foundation.domain.ImmutableApiOperation;
import de.ii.ogcapi.foundation.domain.ImmutableApiResponse;
import java.util.ArrayList;
import java.util.List;

/** The response of an operation that is executed as a job with {@code Prefer: respond-async}. */
public final class AsyncResponses {

  private AsyncResponses() {}

  public static ApiResponse accepted() {
    return new ImmutableApiResponse.Builder()
        .statusCode(ApiOperation.STATUS_202)
        .description(
            "The request is executed asynchronously as a job (`Prefer: respond-async`). The header"
                + " `Location` contains the URI of the status of the job.")
        .build();
  }

  /** The operation with the response of an asynchronous execution, if that is enabled. */
  public static ApiOperation withAccepted(ApiOperation operation, boolean async) {
    if (!async) {
      return operation;
    }
    List<ApiResponse> responses = new ArrayList<>(operation.getAdditionalResponses());
    responses.add(accepted());
    return ImmutableApiOperation.copyOf(operation).withAdditionalResponses(responses);
  }
}
