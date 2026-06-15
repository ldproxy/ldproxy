/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import de.ii.xtraplatform.features.domain.PropertyLink;
import de.ii.xtraplatform.features.domain.SchemaLink;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Resolves the URI template of a captured {@link PropertyLink} against the request URIs. This is
 * the single place where the template parameters are substituted — the provider pipeline captures
 * the links without knowledge of the request URIs.
 */
public final class PropertyLinkResolver {

  private PropertyLinkResolver() {}

  /**
   * @param link the captured property link
   * @param apiUri the URI of the landing page, no trailing slash
   * @param collectionUri the URI of the collection, no trailing slash
   * @param featureUri the canonical URI of the feature, no query parameters
   * @return the resolved link URI
   */
  public static String resolve(
      PropertyLink link, String apiUri, String collectionUri, String featureUri) {
    return link.getUriTemplate()
        .replace(SchemaLink.VALUE, percentEncode(link.getValue()))
        .replace(SchemaLink.FEATURE_URI, featureUri)
        .replace(SchemaLink.COLLECTION_URI, collectionUri)
        .replace(SchemaLink.API_URI, apiUri);
  }

  private static String percentEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
