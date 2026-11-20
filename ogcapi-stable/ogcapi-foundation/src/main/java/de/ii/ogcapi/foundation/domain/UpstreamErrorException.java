/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import jakarta.ws.rs.ServerErrorException;

/**
 * An API that the request depends on, e.g. an external API called by a workflow, cannot be reached
 * or failed. The response has a gateway status (502, 503 or 504) and the message as detail, since
 * the client cannot fix the request and the cause is not an error of this server.
 */
public class UpstreamErrorException extends ServerErrorException {

  private final String type;

  /**
   * @param message the detail of the error response
   * @param status 502, 503 or 504
   * @param type the exception type, a URI, or null
   * @throws IllegalArgumentException if the status is no gateway status
   */
  public UpstreamErrorException(String message, int status, String type) {
    super(message, checked(status));
    this.type = type;
  }

  public String getType() {
    return type;
  }

  /** Whether the status is that of a failed upstream API: 502, 503 or 504. */
  public static boolean isUpstreamError(int status) {
    return status == 502 || status == 503 || status == 504;
  }

  private static int checked(int status) {
    if (!isUpstreamError(status)) {
      throw new IllegalArgumentException(
          String.format("The status of an upstream error is 502, 503 or 504, found %d.", status));
    }
    return status;
  }
}
