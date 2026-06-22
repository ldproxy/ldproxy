/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app;

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
public class ProcessDescriptionsLinkGenerator extends DefaultLinksGenerator {

  /**
   * Generates the links for a single process description on the page /{apiId}/processes.
   *
   * @param uriBuilder the URI, split in host, path and query
   * @param processId the id of the process
   * @param i18n component to get linguistic text
   * @param language the requested language (optional)
   * @return a list with links
   */
  public List<Link> generateProcessDescriptionLinks(
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
                        .toString())
                .rel("self")
                .title(
                    i18n.get("processDescriptionLink", language)
                        .replace("{{processId}}", processId))
                .build())
        .build();
  }
}
