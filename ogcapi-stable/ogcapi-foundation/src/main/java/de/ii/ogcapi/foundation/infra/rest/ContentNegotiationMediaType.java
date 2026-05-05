/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.infra.rest;

import de.ii.ogcapi.foundation.domain.ApiMediaType;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.UriInfo;
import java.util.Optional;
import java.util.Set;

public interface ContentNegotiationMediaType {
  Optional<ApiMediaType> negotiateMediaType(
      ContainerRequestContext requestContext, Set<ApiMediaType> supportedMediaTypes);

  Optional<ApiMediaType> negotiateMediaType(
      Request request,
      HttpHeaders httpHeaders,
      UriInfo uriInfo,
      Set<ApiMediaType> supportedMediaTypes);
}
