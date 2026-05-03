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
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Request;
import java.util.List;
import java.util.Optional;
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
    return ForwardedUri.prefix(getRequestContext(), getWebContext());
  }

  @Value.Derived
  @Override
  public URICustomizer getUriCustomizer() {
    return ForwardedUri.from(getRequestContext(), getWebContext());
  }

  @Value.Derived
  @Override
  public URICustomizer getBaseUriCustomizer() {
    return ForwardedUri.base(getRequestContext(), getWebContext());
  }
}
