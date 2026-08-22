/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.infra.rest;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import java.io.IOException;
import java.util.Objects;

@Singleton
@AutoBind
public class CORSFilter implements ContainerResponseFilter {

  public static final String OPTIONS = "OPTIONS";
  public static final String SEC_FETCH_MODE = "Sec-Fetch-Mode";
  public static final String ORIGIN = "Origin";
  public static final String ADMIN = "admin";
  public static final String CORS = "cors";
  public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
  public static final String POST = "POST";
  public static final String PATCH = "PATCH";
  public static final String ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers";

  @Inject
  public CORSFilter() {}

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext)
      throws IOException {

    if (requestContext.getUriInfo().getPath().startsWith(ADMIN)
        // OPTIONS requests have their own endpoint
        || OPTIONS.equalsIgnoreCase(requestContext.getMethod())) {
      return;
    }

    String secFetchMode = requestContext.getHeaderString(SEC_FETCH_MODE);
    String origin = requestContext.getHeaderString(ORIGIN);
    if (Objects.requireNonNullElse(origin, "").isEmpty() && !CORS.equalsIgnoreCase(secFetchMode)) {
      return;
    }

    // No Access-Control-Allow-Credentials: a browser rejects a credentialed cross-origin response
    // whose Access-Control-Allow-Origin is "*", so the pair grants nothing that works. It matters
    // here because this API accepts a cookie as a credential: were the wildcard ever replaced by a
    // reflected origin, any site a victim visits could call the API with the victim's session and
    // read the answers. A bearer token that a client sets itself is an ordinary request header and
    // needs nothing from this flag. If a cross-origin client ever has to authenticate with a
    // cookie, the correct change is to allow-list its origin (echoing only known origins, with
    // Vary: Origin) and to set Allow-Credentials for those - never to combine credentials with "*".
    responseContext.getHeaders().add(ACCESS_CONTROL_ALLOW_ORIGIN, "*");

    // add additional ldproxy headers here
    ImmutableList.Builder<String> headers = new ImmutableList.Builder<>();
    headers.add(
        "Link",
        "Content-Crs",
        "Content-Bounding-Box",
        "Content-Temporal-Extent",
        "OATiles-hint",
        "Prefer",
        "ETag");
    if (POST.equalsIgnoreCase(requestContext.getMethod())) {
      headers.add("Location");
    }
    if (PATCH.equalsIgnoreCase(requestContext.getMethod())) {
      headers.add("Accept-Patch");
    }
    responseContext
        .getHeaders()
        .add(ACCESS_CONTROL_EXPOSE_HEADERS, String.join(", ", headers.build()));
  }
}
