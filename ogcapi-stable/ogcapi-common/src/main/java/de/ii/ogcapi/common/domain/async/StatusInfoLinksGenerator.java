/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain.async;

import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.DefaultLinksGenerator;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.ImmutableLink;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.xtralink.jobs.Identifiers.Status;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The links of a job status info. Retrieved from the job, the status info links itself (self, and
 * alternate for the other formats). Otherwise, in the response to an execute request or in a
 * callback, it links the job as the resource to monitor (monitor). In both cases the results are
 * linked when they are available. Dismissing a job is a DELETE on the job itself, so it needs no
 * link.
 */
public class StatusInfoLinksGenerator extends DefaultLinksGenerator {

  /** The links of the status info retrieved from the job, the URI of the request. */
  public List<Link> generateLinks(
      ApiRequestContext requestContext, I18n i18n, OgcApiJobStatus statusInfo) {
    URICustomizer jobUri = requestContext.getUriCustomizer().copy().clearParameters();

    return ImmutableList.<Link>builder()
        .addAll(
            generateLinks(
                jobUri,
                requestContext.getMediaType(),
                requestContext.getAlternateMediaTypes(),
                i18n,
                requestContext.getLanguage()))
        .addAll(resultsLink(jobUri, i18n, requestContext.getLanguage(), statusInfo))
        .build();
  }

  /**
   * The links of the status info in the response to a request on another resource, e.g. an execute
   * request; the job is below the API, {@code segmentsToRemove} path segments above the request.
   */
  public List<Link> generateMonitorLinks(
      ApiRequestContext requestContext,
      int segmentsToRemove,
      I18n i18n,
      OgcApiJobStatus statusInfo) {
    URICustomizer jobUri =
        requestContext
            .getUriCustomizer()
            .copy()
            .clearParameters()
            .removeLastPathSegments(segmentsToRemove)
            .ensureLastPathSegments("jobs", statusInfo.getId());

    return monitorLinks(jobUri, i18n, requestContext.getLanguage(), statusInfo);
  }

  /**
   * The links of the status info outside of a request, e.g. in a callback: derived from the URL of
   * the API instead of the URL of a request; the titles are in the default language.
   */
  public List<Link> generateMonitorLinks(String apiUri, I18n i18n, OgcApiJobStatus statusInfo) {
    URICustomizer jobUri;
    try {
      jobUri = new URICustomizer(apiUri).ensureLastPathSegments("jobs", statusInfo.getId());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Invalid API URI: " + apiUri, e);
    }

    return monitorLinks(jobUri, i18n, Optional.empty(), statusInfo);
  }

  // as other links besides self and alternate, the link to monitor the job is one link without a
  // type, the representation is negotiated
  private static List<Link> monitorLinks(
      URICustomizer jobUri, I18n i18n, Optional<Locale> language, OgcApiJobStatus statusInfo) {
    return ImmutableList.<Link>builder()
        .add(
            new ImmutableLink.Builder()
                .href(jobUri.toString())
                .rel("monitor")
                .title(
                    i18n.get("jobMonitorLink", language).replace("{{jobId}}", statusInfo.getId()))
                .build())
        .addAll(resultsLink(jobUri, i18n, language, statusInfo))
        .build();
  }

  // Results; for a failed job they are the reason for the failure
  private static List<Link> resultsLink(
      URICustomizer jobUri, I18n i18n, Optional<Locale> language, OgcApiJobStatus statusInfo) {
    Status currentStatus = statusInfo.getStatus();
    if (!Status.SUCCESSFUL.equals(currentStatus) && !Status.FAILED.equals(currentStatus)) {
      return List.of();
    }

    return List.of(
        new ImmutableLink.Builder()
            .href(jobUri.copy().ensureLastPathSegment("results").toString())
            .rel("https://www.opengis.net/def/rel/ogc/1.0/results")
            .title(i18n.get("jobResultsLink", language).replace("{{jobId}}", statusInfo.getId()))
            .type(
                statusInfo
                    .getResultMediaType()
                    .orElse(
                        Status.FAILED.equals(currentStatus)
                            ? "application/problem+json"
                            : "application/json"))
            .build());
  }
}
