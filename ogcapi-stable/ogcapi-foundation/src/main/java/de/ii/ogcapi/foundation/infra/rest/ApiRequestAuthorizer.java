/*
 * Copyright 2023 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.infra.rest;

import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.xtraplatform.base.domain.auth.User;
import java.util.Optional;
import javax.annotation.Nullable;

public interface ApiRequestAuthorizer {

  ApiRequestContext checkAuthorization(
      ApiRequestContext apiRequestContext,
      @Nullable ApiOperation apiOperation,
      Optional<User> optionalUser,
      Optional<byte[]> body);
}
