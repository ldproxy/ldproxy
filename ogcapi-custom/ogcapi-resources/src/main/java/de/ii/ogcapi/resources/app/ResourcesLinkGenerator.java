/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.resources.app;

import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.ImmutableLink;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** This class is responsible for generating the links to the resources. */
public class ResourcesLinkGenerator {

  /**
   * generates the links for a resource on the page /{serviceId}/resources
   *
   * @param uriBuilder the URI, split in host, path and query
   * @param resourceId the id of the resource
   * @return the link
   */
  public Link generateResourceLink(URICustomizer uriBuilder, String resourceId) {

    final ImmutableLink.Builder builder =
        new ImmutableLink.Builder()
            .href(
                uriBuilder
                    .copy()
                    .removeParameters("f")
                    .ensureNoTrailingSlash()
                    .ensureLastPathSegment(resourceId)
                    .toString())
            .title(resourceId)
            .rel("item");

    return builder.build();
  }

  /**
   * generates the links on the service landing page /{serviceId}?f=json
   *
   * @param uriBuilder the URI, split in host, path and query
   * @return a list with links
   */
  public List<Link> generateLandingPageLinks(
      URICustomizer uriBuilder, I18n i18n, Optional<Locale> language) {

    return ImmutableList.<Link>builder()
        .add(
            new ImmutableLink.Builder()
                .href(
                    uriBuilder
                        .copy()
                        .ensureNoTrailingSlash()
                        .ensureLastPathSegment("resources")
                        .removeParameters("f")
                        .toString())
                .rel("resources")
                .title(i18n.get("resourcesLink", language))
                .build())
        .build();
  }
}
