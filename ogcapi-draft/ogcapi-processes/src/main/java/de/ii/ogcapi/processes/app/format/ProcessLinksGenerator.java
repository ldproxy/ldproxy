/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app.format;

import static de.ii.ogcapi.foundation.domain.ApiMediaType.JSON_MEDIA_TYPE;

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

public class ProcessLinksGenerator extends DefaultLinksGenerator {

  public List<Link> generateLinks(
      URICustomizer uriBuilder,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      I18n i18n,
      Optional<Locale> language,
      String processId) {
    final ImmutableList.Builder<Link> builder =
        new ImmutableList.Builder<Link>()
            .addAll(
                super.generateLinks(uriBuilder, mediaType, alternateMediaTypes, i18n, language));

    builder
        // Landing page (service)
        .add(
            new ImmutableLink.Builder()
                .href(
                    uriBuilder
                        .copy()
                        .removeLastPathSegments(2)
                        .removeParameters("f", "limit", "offset")
                        .addParameter("f", JSON_MEDIA_TYPE.equals(mediaType) ? "json" : "html")
                        .toString())
                .rel("service")
                .title(i18n.get("homeLink", language))
                .mediaType(mediaType)
                .build())
        // Execute
        .add(
            new ImmutableLink.Builder()
                .href(
                    uriBuilder
                        .copy()
                        .ensureLastPathSegment("execution")
                        .removeParameters("f", "limit", "offset")
                        .addParameter("f", "json")
                        .toString())
                .rel("execute")
                .title(i18n.get("executeLink", language).replace("{{processId}}", processId))
                .type("application/json")
                .build())
        .add(generateProfileLink(i18n, language));

    return builder.build();
  }

  // Generate Profile Link (the link is broken)
  public Link generateProfileLink(I18n i18n, Optional<Locale> language) {
    return new ImmutableLink.Builder()
        .href("https://www.opengis.net/def/profile/OGC/0/ogc-process-description")
        .rel("profile")
        .title(i18n.get("profileLink", language).replace("{{profile}}", "ogc-process-description"))
        .type("application/json")
        .build();
  }
}
