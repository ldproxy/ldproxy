/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import java.util.Map;
import java.util.Optional;

/**
 * The response to the request of a step.
 *
 * @param statusCode the HTTP status code
 * @param headers the headers, the names in lower case, the first value of each
 * @param body the content, a JSON value, if any
 */
// a class, not a record: the documentation tool of the build does not support public records
public final class StepResponse {

  private final int statusCode;
  private final Map<String, String> headers;
  private final Optional<Object> body;

  public StepResponse(int statusCode, Map<String, String> headers, Optional<Object> body) {
    this.statusCode = statusCode;
    this.headers = headers;
    this.body = body;
  }

  public int statusCode() {
    return statusCode;
  }

  public Map<String, String> headers() {
    return headers;
  }

  public Optional<Object> body() {
    return body;
  }

  public boolean isSuccessful() {
    return statusCode >= 200 && statusCode < 300;
  }
}
