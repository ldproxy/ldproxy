/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.SingleFeatureResponseCustomizer;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration;
import de.ii.xtraplatform.features.domain.Tuple;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

/**
 * Adds the Memento headers (RFC 7089) to a feature of a versioned collection: the version start as
 * {@code Memento-Datetime}, links to the original resource and the time map, and the immutable
 * caching directive for a retired version.
 */
@Singleton
@AutoBind
public class VersionedFeatureResponseHeaders implements SingleFeatureResponseCustomizer {

  private final I18n i18n;

  @Inject
  public VersionedFeatureResponseHeaders(I18n i18n) {
    this.i18n = i18n;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return VersionedFeaturesConfiguration.class;
  }

  @Override
  public void customize(
      Response.ResponseBuilder response,
      OgcApi api,
      String collectionId,
      String featureId,
      Optional<Tuple<Instant, Instant>> temporalExtent,
      ApiRequestContext requestContext) {
    Instant mementoDatetime =
        temporalExtent.map(Tuple::first).filter(Objects::nonNull).orElse(null);
    Instant mementoEnd = temporalExtent.map(Tuple::second).filter(Objects::nonNull).orElse(null);

    if (Objects.nonNull(mementoEnd)) {
      // The returned version is retired (PRIMARY_INTERVAL_END is set) — its representation will
      // never change. Override any per-collection Cache-Control with the immutable directive.
      response.header("Cache-Control", null);
      response.header("Cache-Control", "public, max-age=31536000, immutable");
    }

    if (Objects.nonNull(mementoDatetime)) {
      response.header(
          "Memento-Datetime",
          DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT")).format(mementoDatetime));
      String originalHref = requestContext.getUriCustomizer().copy().clearParameters().toString();
      String timeMapHref =
          requestContext
              .getUriCustomizer()
              .copy()
              .clearParameters()
              .ensureLastPathSegment("versions")
              .toString();
      response.header("Link", linkHeader(originalHref, "original", requestContext));
      response.header("Link", linkHeader(timeMapHref, "timemap", requestContext));
      response.header("Link", linkHeader(timeMapHref, "version-history", requestContext));
    }
  }

  private String linkHeader(String href, String rel, ApiRequestContext requestContext) {
    String title = i18n.getLinkTitle(rel, requestContext.getLanguage()).orElse(rel);
    return String.format("<%s>; rel=\"%s\"; title=\"%s\"", href, rel, title);
  }
}
