/*
 * Copyright 2023 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.domain;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.google.common.hash.Funnel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonPropertyOrder({
  "title",
  "abstract",
  "keywords",
  "identifier",
  "supportedCRS",
  "wellKnownScaleSet",
  "tileMatrix"
})
public interface WmtsTileMatrixSet {

  @JacksonXmlProperty(namespace = WmtsServiceMetadata.XMLNS_OWS, localName = "Title")
  Optional<String> getTitle();

  @JacksonXmlProperty(namespace = WmtsServiceMetadata.XMLNS_OWS, localName = "Abstract")
  Optional<String> getAbstract();

  @JacksonXmlElementWrapper(namespace = WmtsServiceMetadata.XMLNS_OWS, localName = "Keywords")
  @JacksonXmlProperty(namespace = WmtsServiceMetadata.XMLNS_OWS, localName = "Keyword")
  List<String> getKeywords();

  @JacksonXmlProperty(namespace = WmtsServiceMetadata.XMLNS_OWS, localName = "Identifier")
  String getIdentifier();

  @JacksonXmlProperty(namespace = WmtsServiceMetadata.XMLNS_OWS, localName = "SupportedCRS")
  String getSupportedCRS();

  @JacksonXmlProperty(namespace = WmtsServiceMetadata.XMLNS, localName = "WellKnownScaleSet")
  Optional<String> getWellKnownScaleSet();

  @JacksonXmlElementWrapper(useWrapping = false)
  @JacksonXmlProperty(namespace = WmtsServiceMetadata.XMLNS, localName = "TileMatrix")
  List<WmtsTileMatrix> getTileMatrix();

  @SuppressWarnings("UnstableApiUsage")
  Funnel<WmtsTileMatrixSet> FUNNEL =
      (from, into) -> {
        from.getTitle().ifPresent(title -> into.putString(title, StandardCharsets.UTF_8));
        from.getAbstract().ifPresent(title -> into.putString(title, StandardCharsets.UTF_8));
        from.getKeywords().stream()
            .sorted()
            .forEachOrdered(val -> into.putString(val, StandardCharsets.UTF_8));
        into.putString(from.getIdentifier(), StandardCharsets.UTF_8);
        into.putString(from.getSupportedCRS(), StandardCharsets.UTF_8);
        from.getWellKnownScaleSet().ifPresent(val -> into.putString(val, StandardCharsets.UTF_8));
        from.getTileMatrix().forEach(val -> WmtsTileMatrix.FUNNEL.funnel(val, into));
      };
}
