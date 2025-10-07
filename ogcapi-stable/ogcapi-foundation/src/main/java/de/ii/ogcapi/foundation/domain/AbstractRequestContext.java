/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import de.ii.xtraplatform.web.domain.ForwardedUri;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Request;
import org.immutables.value.Value;

@Value.Immutable
public abstract class AbstractRequestContext implements ApiRequestContext {

  abstract ContainerRequestContext getRequestContext();

  @Value.Derived
  @Override
  public Optional<Request> getRequest() {
    return Optional.ofNullable(getRequestContext().getRequest());
  }

  @Value.Derived
  @Override
  public List<String> getBasePathSegments() {
    return ForwardedUri.prefix(getRequestContext());
  }

  @Value.Derived
  @Override
  public URICustomizer getUriCustomizer() {
    return new URICustomizer(getRequestContext().getUriInfo().getRequestUri())
        .prependPathSegments(getBasePathSegments());
  }

  @Value.Derived
  @Override
  public URICustomizer getBaseUriCustomizer() {
    return new URICustomizer(getRequestContext().getUriInfo().getRequestUri())
        .setPathSegments(getBasePathSegments())
        .ensureNoTrailingSlash()
        .clearParameters();
  }
}
