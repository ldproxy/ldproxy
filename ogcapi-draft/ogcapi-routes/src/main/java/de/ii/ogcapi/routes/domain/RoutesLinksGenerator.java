/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.routes.domain;

import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.ImmutableLink;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** This class is responsible for generating the links to route resources. */
public class RoutesLinksGenerator {

  /**
   * generates the links on the landing page /{apiId}
   *
   * @param uriBuilder the URI, split in host, path and query
   * @return a list with links
   */
  public List<Link> generateLandingPageLinks(
      URICustomizer uriBuilder, I18n i18n, Optional<Locale> language) {

    ImmutableList.Builder<Link> builder = ImmutableList.builder();

    builder.add(
        new ImmutableLink.Builder()
            .href(
                uriBuilder
                    .copy()
                    .ensureNoTrailingSlash()
                    .ensureLastPathSegment("routes")
                    .clearParameters()
                    .toString())
            .rel("http://www.opengis.net/def/rel/ogc/1.0/routes")
            .title(i18n.get("routesLink", language))
            .build());

    return builder.build();
  }

  /**
   * generates the a link to a route /{apiId}/routes
   *
   * @param uriBuilder the URI, split in host, path and query
   * @return a list with links
   */
  public Link generateRouteLink(
      String routeId,
      String routeName,
      URICustomizer uriBuilder,
      I18n i18n,
      Optional<Locale> language) {

    return new ImmutableLink.Builder()
        .href(
            uriBuilder
                .copy()
                .ensureNoTrailingSlash()
                .ensureLastPathSegments("routes", routeId)
                .clearParameters()
                .toString())
        .rel("item")
        .title(i18n.get("routeLink", language).replace("{{name}}", routeName))
        .build();
  }

  /**
   * generates the links for a route
   *
   * @param uriBuilder the URI, split in host, path and query
   * @return a list with links
   */
  public List<Link> generateRouteLinks(
      String routeId,
      Optional<String> routeName,
      URICustomizer uriBuilder,
      ApiMediaType mediaType,
      I18n i18n,
      Optional<Locale> language) {

    return ImmutableList.of(
        new ImmutableLink.Builder()
            .href(
                uriBuilder
                    .copy()
                    .ensureNoTrailingSlash()
                    .ensureLastPathSegments("routes", routeId)
                    .clearParameters()
                    .toString())
            .rel("self")
            .type(mediaType.type().toString())
            .title(
                routeName
                    .map(s -> i18n.get("routeLink", language).replace("{{name}}", s))
                    .orElseGet(() -> i18n.get("routeLinkFallback", language)))
            .build(),
        new ImmutableLink.Builder()
            .href(
                uriBuilder
                    .copy()
                    .ensureNoTrailingSlash()
                    .ensureLastPathSegments("routes", routeId, "definition")
                    .clearParameters()
                    .toString())
            .rel("describedby")
            .title(
                routeName
                    .map(s -> i18n.get("routeDefinitionLink", language).replace("{{name}}", s))
                    .orElseGet(() -> i18n.get("routeDefinitionLinkFallback", language)))
            .build());
  }

  /**
   * generates the links for a route definition
   *
   * @param uriBuilder the URI, split in host, path and query
   * @return a list with links
   */
  public List<Link> generateRouteDefinitionLinks(
      String routeId,
      Optional<String> routeName,
      URICustomizer uriBuilder,
      ApiMediaType mediaType,
      I18n i18n,
      Optional<Locale> language) {

    return ImmutableList.of(
        new ImmutableLink.Builder()
            .href(
                uriBuilder
                    .copy()
                    .ensureNoTrailingSlash()
                    .ensureLastPathSegments("routes", routeId)
                    .clearParameters()
                    .toString())
            .rel("about")
            .title(
                routeName
                    .map(s -> i18n.get("routeLink", language).replace("{{name}}", s))
                    .orElseGet(() -> i18n.get("routeLinkFallback", language)))
            .build(),
        new ImmutableLink.Builder()
            .href(
                uriBuilder
                    .copy()
                    .ensureNoTrailingSlash()
                    .ensureLastPathSegments("routes", routeId, "definition")
                    .clearParameters()
                    .toString())
            .rel("self")
            .type(mediaType.type().toString())
            .title(
                routeName
                    .map(s -> i18n.get("routeDefinitionLink", language).replace("{{name}}", s))
                    .orElseGet(() -> i18n.get("routeDefinitionLinkFallback", language)))
            .build());
  }
}
