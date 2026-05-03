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
import java.io.IOException;
import java.util.Objects;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;

@Singleton
@AutoBind
public class CORSFilter implements ContainerResponseFilter {

  public static final String OPTIONS = "OPTIONS";
  public static final String SEC_FETCH_MODE = "Sec-Fetch-Mode";
  public static final String ORIGIN = "Origin";
  public static final String ADMIN = "admin";
  public static final String CORS = "cors";
  public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
  public static final String ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
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

    responseContext.getHeaders().add(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
    responseContext.getHeaders().add(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");

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
