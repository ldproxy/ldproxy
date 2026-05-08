/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import jakarta.ws.rs.WebApplicationException;

public class UnprocessableEntity extends WebApplicationException {

  private static final long serialVersionUID = -2187376341627253652L;

  public UnprocessableEntity() {
    super(422);
  }

  public UnprocessableEntity(String message) {
    super(message, 422);
  }
}
