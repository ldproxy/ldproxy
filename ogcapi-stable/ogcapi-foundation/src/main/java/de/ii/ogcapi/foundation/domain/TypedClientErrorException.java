/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;

/**
 * A client error for which a specification mandates an exception type, reported as the {@code type}
 * member of the error response.
 */
public class TypedClientErrorException extends ClientErrorException {

  private final String type;

  public TypedClientErrorException(String message, Response.Status status, String type) {
    super(message, status);
    this.type = type;
  }

  public String getType() {
    return type;
  }
}
