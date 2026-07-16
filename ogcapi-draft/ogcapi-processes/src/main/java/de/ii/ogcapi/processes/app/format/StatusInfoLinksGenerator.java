/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app.format;

import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.DefaultLinksGenerator;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.ImmutableLink;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.processes.domain.model.StatusInfo;
import de.ii.ogcapi.processes.domain.model.StatusInfo.StatusCode;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class StatusInfoLinksGenerator extends DefaultLinksGenerator {

  public List<Link> generateLinks(
      URICustomizer uriBuilder,
      I18n i18n,
      Optional<Locale> language,
      StatusInfo statusInfo,
      int segmentsToRemove) {
    final ImmutableList.Builder<Link> builder = new ImmutableList.Builder<Link>();

    String jobId = statusInfo.getId();
    URICustomizer baseHref =
        uriBuilder
            .copy()
            .clearParameters()
            .removeLastPathSegments(segmentsToRemove)
            .ensureLastPathSegments("jobs", jobId);

    // Add self link
    builder.add(
        new ImmutableLink.Builder()
            .href(baseHref.toString())
            .rel("self")
            .title(i18n.get("selfLink", language))
            .type("application/json")
            .build());

    StatusCode currentStatus = statusInfo.getStatus();

    // Results
    if (StatusCode.SUCCESSFUL.equals(currentStatus)) {
      builder.add(
          new ImmutableLink.Builder()
              .href(baseHref.copy().ensureLastPathSegment("results").toString())
              .rel("[ogc-rel:results]")
              .title(i18n.get("jobResultsLink", language).replace("{{jobId}}", jobId))
              .type("application/json")
              .build());
    }

    builder.add(
        new ImmutableLink.Builder()
            .href(baseHref.toString())
            .rel("delete")
            .title(i18n.get("jobDismissLink", language).replace("{{jobId}}", jobId))
            .type("application/json")
            .build());

    return builder.build();
  }
}
