/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.DefaultLinksGenerator;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.ImmutableLink;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.foundation.domain.ProfileExtension;
import de.ii.ogcapi.foundation.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class FeatureLinksGenerator extends DefaultLinksGenerator {

  public List<Link> generateLinks(
      URICustomizer uriBuilder,
      List<Profile> profiles,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      ApiMediaType collectionMediaType,
      String canonicalUri,
      I18n i18n,
      Optional<Locale> language) {
    final ImmutableList.Builder<Link> builder =
        new ImmutableList.Builder<Link>()
            .addAll(
                super.generateLinks(uriBuilder, mediaType, alternateMediaTypes, i18n, language));

    if (canonicalUri != null)
      builder.add(
          new ImmutableLink.Builder()
              .href(canonicalUri)
              .rel("canonical")
              .title(i18n.get("persistentLink", language))
              .build());

    builder.add(
        new ImmutableLink.Builder()
            .href(
                uriBuilder
                    .copy()
                    .clearParameters()
                    .ensureParameter("f", collectionMediaType.parameter())
                    .removeLastPathSegments(2)
                    .toString())
            .rel("collection")
            .type(collectionMediaType.type().toString())
            .title(i18n.get("collectionLink", language))
            .build());

    profiles.forEach(
        p ->
            builder.add(
                new ImmutableLink.Builder()
                    .href(ProfileExtension.getUri(p))
                    .rel("profile")
                    .title(i18n.get("profileLink", language).replace("{{profile}}", p.getLabel()))
                    .build()));

    return builder.build();
  }

  public List<Link> generateLinksReduced(
      URICustomizer uriBuilder,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      ApiMediaType collectionMediaType,
      String canonicalUri,
      I18n i18n,
      Optional<Locale> language) {
    final ImmutableList.Builder<Link> builder = new ImmutableList.Builder<Link>();

    if (canonicalUri != null)
      builder.add(
          new ImmutableLink.Builder()
              .href(canonicalUri)
              .rel("canonical")
              .title(i18n.get("persistentLink", language))
              .build());

    builder.add(
        new ImmutableLink.Builder()
            .href(
                uriBuilder
                    .copy()
                    .clearParameters()
                    .ensureParameter("f", collectionMediaType.parameter())
                    .removeLastPathSegments(2)
                    .toString())
            .rel("collection")
            .type(collectionMediaType.type().toString())
            .title(i18n.get("collectionLink", language))
            .build());

    return builder.build();
  }
}
