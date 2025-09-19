/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.domain;

import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.ImmutableLink;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** This class is responsible for generating the links to the styles. */
public class StylesLinkGenerator {

  /**
   * generates the links on the service landing page /{serviceId}?f=json
   *
   * @param uriBuilder the URI, split in host, path and query
   * @return a list with links
   */
  public List<Link> generateLandingPageLinks(
      URICustomizer uriBuilder,
      Optional<String> defaultStyle,
      I18n i18n,
      Optional<Locale> language) {

    ImmutableList.Builder<Link> builder = ImmutableList.builder();

    builder.add(
        new ImmutableLink.Builder()
            .href(
                uriBuilder
                    .copy()
                    .ensureNoTrailingSlash()
                    .ensureLastPathSegment("styles")
                    .removeParameters("f")
                    .toString())
            .rel("http://www.opengis.net/def/rel/ogc/1.0/styles")
            .title(i18n.get("stylesLink", language))
            .build());

    if (defaultStyle.isPresent() && !defaultStyle.get().equals("NONE"))
      builder.add(
          new ImmutableLink.Builder()
              .href(
                  uriBuilder
                      .copy()
                      .ensureNoTrailingSlash()
                      .ensureLastPathSegments("styles", defaultStyle.get())
                      .setParameter("f", "html")
                      .toString())
              .rel("ldp-map")
              .title(i18n.get("webmapLink", language))
              .build());

    return builder.build();
  }

  /**
   * generates the links on the collection page /{serviceId}/collections/{collectionId}?f=json
   *
   * @param uriBuilder the URI, split in host, path and query
   * @return a list with links
   */
  public List<Link> generateCollectionLinks(
      URICustomizer uriBuilder,
      Optional<String> defaultStyle,
      I18n i18n,
      Optional<Locale> language) {

    ImmutableList.Builder<Link> builder = ImmutableList.builder();

    builder.add(
        new ImmutableLink.Builder()
            .href(
                uriBuilder
                    .copy()
                    .ensureNoTrailingSlash()
                    .ensureLastPathSegment("styles")
                    .removeParameters("f")
                    .toString())
            .rel("http://www.opengis.net/def/rel/ogc/1.0/styles")
            .title(i18n.get("stylesLink", language))
            .build());

    if (defaultStyle.isPresent())
      builder.add(
          new ImmutableLink.Builder()
              .href(
                  uriBuilder
                      .copy()
                      .ensureNoTrailingSlash()
                      .ensureLastPathSegments("styles", defaultStyle.get())
                      .setParameter("f", "html")
                      .toString())
              .rel("ldp-map")
              .title(i18n.get("webmapLink", language))
              .build());

    return builder.build();
  }

  /**
   * generates the links for a style on the page /{serviceId}/styles
   *
   * @param uriBuilder the URI, split in host, path and query
   * @param styleId the ids of the styles
   * @return a list with links
   */
  public List<Link> generateStyleLinks(
      URICustomizer uriBuilder,
      String styleId,
      List<ApiMediaType> mediaTypes,
      I18n i18n,
      Optional<Locale> language) {

    final ImmutableList.Builder<Link> builder = new ImmutableList.Builder<Link>();

    for (ApiMediaType mediaType : mediaTypes) {
      builder.add(
          new ImmutableLink.Builder()
              .href(
                  uriBuilder
                      .copy()
                      .ensureNoTrailingSlash()
                      .ensureLastPathSegment(styleId)
                      .setParameter("f", mediaType.parameter())
                      .toString())
              .rel("stylesheet")
              .type(mediaType.type().toString())
              .title(
                  mediaType.label().equals("HTML")
                      ? i18n.get("stylesheetLinkMap", language)
                      : i18n.get("stylesheetLink", language)
                          .replace("{{format}}", mediaType.label()))
              .build());
    }

    return builder.build();
  }

  /**
   * generates the link for the style metadata on the page /{serviceId}/styles
   *
   * @param uriBuilder the URI, split in host, path and query
   * @param styleId the id of the style
   * @return the link
   */
  public Link generateStyleMetadataLink(
      URICustomizer uriBuilder, String styleId, I18n i18n, Optional<Locale> language) {

    return new ImmutableLink.Builder()
        .href(
            uriBuilder
                .copy()
                .ensureNoTrailingSlash()
                .ensureLastPathSegments("styles", styleId, "metadata")
                .removeParameters("f")
                .toString())
        .rel("describedby")
        .title(i18n.get("styleMetadataLink", language))
        .build();
  }

  /**
   * generates a link for a style on the page /{serviceId}/styles/{styleId}/map/tiles
   *
   * @param uriBuilder the URI, split in host, path and query
   * @param styleId the id of the style
   * @return the link
   */
  public Link generateMapTilesLink(
      URICustomizer uriBuilder, String styleId, I18n i18n, Optional<Locale> language) {

    return new ImmutableLink.Builder()
        .href(
            uriBuilder
                .copy()
                .ensureNoTrailingSlash()
                .ensureLastPathSegments("styles", styleId, "map", "tiles")
                .removeParameters("f")
                .toString())
        .rel("http://www.opengis.net/def/rel/ogc/1.0/tilesets-map")
        .title(i18n.get("styledMapTilesLink", language))
        .build();
  }

  /**
   * generates the link for a style legend on the page /{serviceId}/styles
   *
   * @param uriBuilder the URI, split in host, path and query
   * @param styleId the id of the style
   * @return a list with links
   */
  public Link generateStyleLegendLink(
      URICustomizer uriBuilder, String styleId, I18n i18n, Optional<Locale> language) {

    return new ImmutableLink.Builder()
        .href(
            uriBuilder
                .copy()
                .ensureNoTrailingSlash()
                .ensureLastPathSegments("styles", styleId, "legend")
                .removeParameters("f")
                .toString())
        .rel("http://www.opengis.net/def/rel/ogc/1.0/legend")
        .title(i18n.get("styleLegendLink", language))
        .build();
  }

  public Link generateStylesheetLink(
      URICustomizer uriBuilder,
      String styleId,
      ApiMediaType mediaType,
      I18n i18n,
      Optional<Locale> language) {

    final ImmutableLink.Builder builder =
        new ImmutableLink.Builder()
            .href(
                uriBuilder
                    .copy()
                    .ensureNoTrailingSlash()
                    .ensureLastPathSegment(styleId)
                    .setParameter("f", mediaType.parameter())
                    .toString())
            .rel("stylesheet")
            .title(i18n.get("stylesheetLink", language).replace("{{format}}", mediaType.label()))
            .type(mediaType.type().toString());

    return builder.build();
  }
}
