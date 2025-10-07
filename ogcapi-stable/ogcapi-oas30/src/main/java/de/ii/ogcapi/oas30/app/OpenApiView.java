/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.oas30.app;

import de.ii.ogcapi.html.domain.OgcApiView;
import de.ii.xtraplatform.auth.domain.Oidc;
import de.ii.xtraplatform.openapi.domain.ImmutableSwaggerUi;
import de.ii.xtraplatform.openapi.domain.SwaggerUi;
import org.immutables.value.Value;

@Value.Style(builder = "new")
@Value.Immutable
public abstract class OpenApiView extends OgcApiView {

  public OpenApiView() {
    super("openapi.mustache");
  }

  public abstract Oidc oidc();

  public abstract boolean showCommonExtensions();

  @Value.Derived
  public String baseUri() {
    return uriCustomizer().copy().setPath("").clearParameters().toString();
  }

  @Value.Derived
  public SwaggerUi swaggerUi() {
    ImmutableSwaggerUi.Builder builder =
        new ImmutableSwaggerUi.Builder()
            .definitionUri(uriCustomizer().copy().setParameter("f", "json").toString());

    if (oidc().isEnabled()) {
      builder.clientId(oidc().getClientId()).clientSecret(oidc().getClientSecret());
    }

    return builder.build();
  }
}
