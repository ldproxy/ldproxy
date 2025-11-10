/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.app;

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

public class LandingPageLinksGenerator extends DefaultLinksGenerator {

  public List<Link> generateLinks(
      URICustomizer uriBuilder,
      Optional<String> describeFeatureTypeUrl,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      Optional<String> licenseUrl,
      Optional<String> licenseName,
      I18n i18n,
      Optional<Locale> language) {
    final ImmutableList.Builder<Link> builder =
        new ImmutableList.Builder<Link>()
            .addAll(
                super.generateLinks(uriBuilder, mediaType, alternateMediaTypes, i18n, language));

    uriBuilder
        .ensureNoTrailingSlash()
        .removeParameters("lang")
        .ensureParameter("f", mediaType.parameter());

    builder
        .add(
            new ImmutableLink.Builder()
                .href(
                    uriBuilder
                        .copy()
                        .ensureLastPathSegment("conformance")
                        .removeParameters("f")
                        .toString())
                .rel("conformance")
                .title(i18n.get("conformanceLink", language))
                .build())
        .add(
            new ImmutableLink.Builder()
                .href(
                    uriBuilder
                        .copy()
                        .ensureLastPathSegment("conformance")
                        .removeParameters("f")
                        .toString())
                .rel("http://www.opengis.net/def/rel/ogc/1.0/conformance")
                .title(i18n.get("conformanceLink", language))
                .build());

    licenseUrl.ifPresent(
        url ->
            builder.add(
                new ImmutableLink.Builder()
                    .href(url)
                    .rel("license")
                    .title(licenseName.orElseGet(() -> i18n.get("licenseLink", language)))
                    .build()));

    return builder.build();
  }
}
