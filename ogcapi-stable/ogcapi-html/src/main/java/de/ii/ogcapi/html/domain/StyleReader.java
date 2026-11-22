/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.html.domain;

import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.html.domain.MapClient.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface StyleReader {

  Logger LOGGER = LoggerFactory.getLogger(StyleReader.class);

  enum StyleFormat {
    MBS("mbs", "maplibre-styles"),
    _3DTILES("3dtiles", "3dtiles-styles");

    private final String ext;
    private final String valueType;

    StyleFormat(String ext, String valueType) {
      this.ext = ext;
      this.valueType = valueType;
    }

    public String ext() {
      return ext;
    }

    public String valueType() {
      return valueType;
    }
  }

  boolean exists(
      String apiId,
      Optional<String> collectionId,
      String styleId,
      StyleFormat styleFormat,
      @Deprecated(forRemoval = true) OgcApiDataV2 apiData);

  List<String> getStyleIds(String apiId, Optional<String> collectionId, StyleFormat styleFormat);

  /**
   * Whether the MapLibre style of the API can be applied to the features of a collection: it has a
   * layer with the collection as source layer, or none of its layers uses a source layer. The map
   * client applies every data layer of the style to the features shown on the page, so a style with
   * source layers for other collections only would render them arbitrarily.
   */
  boolean appliesToCollection(String apiId, String collectionId, String styleId);

  default String getStyleUrl(
      Optional<String> requestedStyle,
      Optional<String> collectionId,
      String apiId,
      String serviceUrl,
      Type mapClientType,
      String defaultStyle,
      @Deprecated(forRemoval = true) OgcApiDataV2 apiData) {
    Optional<String> styleId =
        requestedStyle
            .map(s -> "DEFAULT".equals(s) ? Objects.requireNonNullElse(defaultStyle, "NONE") : s)
            .filter(s -> !"NONE".equals(s));
    StyleFormat f =
        switch (mapClientType) {
          case MAP_LIBRE -> StyleFormat.MBS;
          case OPEN_LAYERS -> StyleFormat.MBS;
          case CESIUM -> StyleFormat._3DTILES;
          default -> null;
        };

    if (styleId.isEmpty() || Objects.isNull(f)) {
      return null;
    }

    String styleUrl =
        styleId
            .map(
                s -> {
                  try {
                    URI serviceUri = new URI(serviceUrl);
                    String path =
                        collectionId.isEmpty()
                            ? String.format("%s/styles/%s", serviceUri.getPath(), s)
                            : String.format(
                                "%s/collections/%s/styles/%s",
                                serviceUri.getPath(), collectionId.get(), s);
                    String query = String.format("f=%s", f.ext());
                    return new URI(
                            serviceUri.getScheme(), serviceUri.getAuthority(), path, query, null)
                        .toString();
                  } catch (URISyntaxException e) {
                    return null;
                  }
                })
            .orElse(null);

    // Check that the style exists
    if (Objects.isNull(styleUrl)) {
      return null;
    }

    boolean exists = exists(apiId, collectionId, styleId.get(), f, apiData);

    if (exists) {
      return styleUrl;
    }

    // Try fallback to the dataset style, if we have a collection id
    if (collectionId.isPresent()) {
      if (f == StyleFormat.MBS
          && exists(apiId, Optional.empty(), styleId.get(), f, apiData)
          && !appliesToCollection(apiId, collectionId.get(), styleId.get())) {
        // the dataset style has no layer for the collection, use the default map style
        return null;
      }

      return getStyleUrl(
          requestedStyle,
          Optional.empty(),
          apiId,
          serviceUrl,
          mapClientType,
          defaultStyle,
          apiData);
    }

    LOGGER.error("Style '{}' does not exist, falling back to style 'NONE'.", styleUrl);

    return null;
  }
}
