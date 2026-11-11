/*
 * Copyright 2023 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import de.ii.xtraplatform.auth.domain.User;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Checks whether a request may execute an operation of an API. Besides the request dispatcher, it
 * is used where an operation is executed within another request, for example a process executed as
 * a tool of the MCP endpoint, which must require the permissions of the execution endpoint.
 */
public interface ApiRequestAuthorizer {

  ApiRequestContext checkAuthorization(
      ApiRequestContext apiRequestContext,
      @Nullable ApiOperation apiOperation,
      Optional<User> optionalUser,
      Optional<byte[]> body);
}
