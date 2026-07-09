/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app.format;

import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.DefaultLinksGenerator;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.ImmutableLink;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class ProcessListLinksGenerator extends DefaultLinksGenerator {

  public List<Link> generateLinks(
      URICustomizer uriBuilder,
      int offset,
      int limit,
      int defaultLimit,
      int processesCount,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      I18n i18n,
      Optional<Locale> language) {
    final ImmutableList.Builder<Link> builder =
        new ImmutableList.Builder<Link>()
            .addAll(
                super.generateLinks(uriBuilder, mediaType, alternateMediaTypes, i18n, language));

    if (limit + offset < processesCount) {
      builder.add(
          new ImmutableLink.Builder()
              .href(getUrlWithPageAndCount(uriBuilder.copy(), offset + limit, limit, defaultLimit))
              .rel("next")
              .mediaType(mediaType)
              .title(i18n.get("nextLink", language))
              .build());
    }
    if (offset > 0) {
      builder.add(
          new ImmutableLink.Builder()
              .href(getUrlWithPageAndCount(uriBuilder.copy(), offset - limit, limit, defaultLimit))
              .rel("prev")
              .mediaType(mediaType)
              .title(i18n.get("prevLink", language))
              .build());
      builder.add(
          new ImmutableLink.Builder()
              .href(getUrlWithPageAndCount(uriBuilder.copy(), 0, limit, defaultLimit))
              .rel("first")
              .mediaType(mediaType)
              .title(i18n.get("firstLink", language))
              .build());
    }

    return builder.build();
  }

  private String getUrlWithPageAndCount(
      final URICustomizer uriBuilder, final int offset, final int limit, final int defaultLimit) {

    URICustomizer uri = uriBuilder.ensureNoTrailingSlash().removeParameters("offset", "limit");

    if (offset != 0) uri.setParameter("offset", String.valueOf(Integer.max(0, offset)));

    if (limit != defaultLimit) uri.setParameter("limit", String.valueOf(limit));

    return uri.toString();
  }

  /**
   * Generates the links for a single process on the page /{apiId}/processes.
   *
   * @param uriBuilder the URI, split in host, path and query
   * @param processId the id of the process
   * @param i18n component to get linguistic text
   * @param language the requested language (optional)
   * @return a list with links
   */
  public List<Link> generateProcessLink(
      URICustomizer uriBuilder, String processId, I18n i18n, Optional<Locale> language) {

    return ImmutableList.<Link>builder()
        .add(
            new ImmutableLink.Builder()
                .href(
                    uriBuilder
                        .copy()
                        .ensureLastPathSegment(processId)
                        .removeParameters("f")
                        .removeParameters("limit")
                        .removeParameters("offset")
                        .addParameter("f", "json")
                        .toString())
                .rel("self")
                .title(i18n.get("processLink", language).replace("{{processId}}", processId))
                .type("application/json")
                .build())
        .add(
            new ImmutableLink.Builder()
                .href(
                    uriBuilder
                        .copy()
                        .ensureLastPathSegment(processId)
                        .removeParameters("f")
                        .removeParameters("limit")
                        .removeParameters("offset")
                        .addParameter("f", "html")
                        .toString())
                .rel("alternate")
                .title(i18n.get("processLink", language).replace("{{processId}}", processId))
                .type("text/html")
                .build())
        .build();
  }
}
