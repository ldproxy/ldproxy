/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.google.common.collect.ImmutableList;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.hc.core5.net.URIBuilder;

public class DefaultLinksGenerator {

  public List<Link> generateLinks(
      URICustomizer uriCustomizer,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      I18n i18n,
      Optional<Locale> language) {
    return generateLinks(
        uriCustomizer, mediaType, alternateMediaTypes, List.of(), Map.of(), i18n, language);
  }

  public List<Link> generateLinks(
      URICustomizer uriCustomizer,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      List<Profile> profiles,
      Map<ApiMediaType, List<Profile>> alternateProfiles,
      I18n i18n,
      Optional<Locale> language) {
    URICustomizer uriBuilder =
        uriCustomizer.copy().removeParameters("lang").ensureNoTrailingSlash();

    final ImmutableList.Builder<Link> builder =
        new ImmutableList.Builder<Link>()
            .add(
                new ImmutableLink.Builder()
                    .href(
                        alternateMediaTypes.isEmpty()
                            ? uriBuilder.copy().clearParameters().toString()
                            : uriBuilder.copy().setParameter("f", mediaType.parameter()).toString())
                    .rel("self")
                    .type(mediaType.type().toString())
                    .profiles(profiles)
                    .title(i18n.get("selfLink", language))
                    .build())
            .addAll(
                alternateMediaTypes.stream()
                    .filter(
                        mt ->
                            alternateProfiles.keySet().stream()
                                .noneMatch(mt2 -> mt.type().equals(mt2.type())))
                    .map(generateAlternateLink(uriBuilder.copy(), i18n, language))
                    .collect(Collectors.toList()));

    profiles.forEach(
        p ->
            builder.add(
                new ImmutableLink.Builder()
                    .href(ProfileExtension.getUri(p))
                    .rel("profile")
                    .title(i18n.get("profileLink", language).replace("{{profile}}", p.getLabel()))
                    .build()));

    alternateProfiles.forEach(
        (mt, ps) ->
            ps.forEach(
                p ->
                    builder.add(
                        new ImmutableLink.Builder()
                            .href(
                                uriBuilder
                                    .setParameter("f", mt.parameter())
                                    .setParameter("profile", p.getId())
                                    .toString())
                            .rel("alternate")
                            .mediaType(mt)
                            .addProfiles(p)
                            .title(i18n.get("alternateLink", language) + " " + p.getLabel())
                            .build())));

    return builder.build();
  }

  public List<Link> generateLinks(ApiRequestContext requestContext, I18n i18n) {
    return generateLinks(
        requestContext.getUriCustomizer(),
        requestContext.getMediaType(),
        requestContext.getAlternateMediaTypes(),
        i18n,
        requestContext.getLanguage());
  }

  private Function<ApiMediaType, Link> generateAlternateLink(
      final URIBuilder uriBuilder, I18n i18n, Optional<Locale> language) {
    return mediaType ->
        new ImmutableLink.Builder()
            .href(uriBuilder.setParameter("f", mediaType.parameter()).toString())
            .rel("alternate")
            .type(mediaType.type().toString())
            .title(i18n.get("alternateLink", language) + " " + mediaType.label())
            .build();
  }
}
