/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.codelists.app;

import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.ImmutableLink;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** This class is responsible for generating the links to the codelists. */
public class CodelistsLinkGenerator {

  /**
   * generates the links for a codelist on the page /{apiId}/codelists
   *
   * @param uriBuilder the URI, split in host, path and query
   * @param codelistId the id of the codelist
   * @return the link
   */
  public Link generateCodelistLink(
      URICustomizer uriBuilder, String codelistId, Optional<String> label) {

    final ImmutableLink.Builder builder =
        new ImmutableLink.Builder()
            .href(
                uriBuilder
                    .copy()
                    .removeParameters("f")
                    .ensureNoTrailingSlash()
                    .ensureLastPathSegment(codelistId)
                    .toString())
            .title(label.orElse(codelistId))
            .rel("self");

    return builder.build();
  }

  /**
   * generates the links on the API landing page /{apiId}
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
                        .ensureLastPathSegment("codelists")
                        .removeParameters("f")
                        .toString())
                .rel("codelists")
                .title(i18n.get("codelistsLink", language))
                .build())
        .build();
  }
}
