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
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.google.common.hash.Funnel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JacksonXmlRootElement(namespace = WmtsServiceMetadata.XMLNS, localName = "Layer")
@JsonPropertyOrder({
  "title",
  "abstract",
  "keywords",
  "wgs84BoundingBox",
  "identifier",
  "style",
  "formats",
  "tileMatrixSetLink",
  "resourceURL"
})
public interface WmtsLayer {

  @JacksonXmlProperty(namespace = WmtsServiceMetadata.XMLNS_OWS, localName = "Title")
  Optional<String> getTitle();

  @JacksonXmlProperty(namespace = WmtsServiceMetadata.XMLNS_OWS, localName = "Abstract")
  Optional<String> getAbstract();

  @JacksonXmlElementWrapper(namespace = WmtsServiceMetadata.XMLNS_OWS, localName = "Keywords")
  @JacksonXmlProperty(namespace = WmtsServiceMetadata.XMLNS_OWS, localName = "Keyword")
  List<String> getKeywords();

  @JacksonXmlProperty(namespace = WmtsServiceMetadata.XMLNS_OWS, localName = "WGS84BoundingBox")
  Optional<WmtsWGS84BoundingBox> getWGS84BoundingBox();

  @JacksonXmlProperty(namespace = WmtsServiceMetadata.XMLNS_OWS, localName = "Identifier")
  String getIdentifier();

  @JacksonXmlElementWrapper(useWrapping = false)
  @JacksonXmlProperty(namespace = WmtsServiceMetadata.XMLNS, localName = "Style")
  List<WmtsStyle> getStyle();

  @JacksonXmlElementWrapper(useWrapping = false)
  @JacksonXmlProperty(namespace = WmtsServiceMetadata.XMLNS, localName = "Format")
  List<String> getFormats();

  @JacksonXmlElementWrapper(useWrapping = false)
  @JacksonXmlProperty(namespace = WmtsServiceMetadata.XMLNS, localName = "TileMatrixSetLink")
  List<WmtsTileMatrixSetLink> getTileMatrixSetLink();

  @JacksonXmlElementWrapper(useWrapping = false)
  @JacksonXmlProperty(namespace = WmtsServiceMetadata.XMLNS, localName = "ResourceURL")
  List<WmtsResourceURL> getResourceURL();

  @SuppressWarnings("UnstableApiUsage")
  Funnel<WmtsLayer> FUNNEL =
      (from, into) -> {
        from.getTitle().ifPresent(title -> into.putString(title, StandardCharsets.UTF_8));
        from.getAbstract().ifPresent(title -> into.putString(title, StandardCharsets.UTF_8));
        from.getKeywords().stream()
            .sorted()
            .forEachOrdered(val -> into.putString(val, StandardCharsets.UTF_8));
        from.getWGS84BoundingBox().ifPresent(val -> WmtsWGS84BoundingBox.FUNNEL.funnel(val, into));
        into.putString(from.getIdentifier(), StandardCharsets.UTF_8);
        from.getStyle().stream().sorted().forEachOrdered(val -> WmtsStyle.FUNNEL.funnel(val, into));
        from.getFormats().stream()
            .sorted()
            .forEachOrdered(val -> into.putString(val, StandardCharsets.UTF_8));
        from.getTileMatrixSetLink().forEach(val -> WmtsTileMatrixSetLink.FUNNEL.funnel(val, into));
        from.getResourceURL().stream()
            .sorted()
            .forEachOrdered(val -> WmtsResourceURL.FUNNEL.funnel(val, into));
      };
}
