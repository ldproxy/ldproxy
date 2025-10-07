/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tilematrixsets.app;

import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.DefaultLinksGenerator;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.ImmutableLink;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** This class is responsible for generating the links in the json Files. */
public class TileMatrixSetsLinkGenerator extends DefaultLinksGenerator {

  /**
   * generates the Links on the landing page /{apiId}/
   *
   * @param uriBuilder the URI, split in host, path and query
   * @param i18n component to get linguistic text
   * @param language the requested language (optional)
   * @return a List with links
   */
  public List<Link> generateLandingPageLinks(
      URICustomizer uriBuilder, I18n i18n, Optional<Locale> language) {

    return ImmutableList.<Link>builder()
        .add(
            new ImmutableLink.Builder()
                .href(
                    uriBuilder
                        .copy()
                        .removeLastPathSegment("collections")
                        .ensureNoTrailingSlash()
                        .ensureLastPathSegment("tileMatrixSets")
                        .removeParameters("f")
                        .toString())
                .rel("http://www.opengis.net/def/rel/ogc/1.0/tiling-schemes")
                .title(i18n.get("tileMatrixSetsLink", language))
                .build())
        .build();
  }

  /**
   * generates the links on the page /{apiId}/tileMatrixSets
   *
   * @param uriBuilder the URI, split in host, path and query
   * @param tileMatrixSetId the id of the tiling Scheme
   * @param i18n component to get linguistic text
   * @param language the requested language (optional)
   * @return a list with links
   */
  public List<Link> generateTileMatrixSetsLinks(
      URICustomizer uriBuilder, String tileMatrixSetId, I18n i18n, Optional<Locale> language) {

    return ImmutableList.<Link>builder()
        .add(
            new ImmutableLink.Builder()
                .href(
                    uriBuilder
                        .copy()
                        .ensureLastPathSegment(tileMatrixSetId)
                        .removeParameters("f")
                        .toString())
                .rel("self")
                .title(
                    i18n.get("tileMatrixSetLink", language)
                        .replace("{{tileMatrixSetId}}", tileMatrixSetId))
                .build())
        .build();
  }
}
