/*
 * Copyright 2023 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.domain;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.google.common.hash.Funnel;
import de.ii.xtraplatform.tiles.domain.JacksonXmlAnnotation.XmlIgnore;
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonPropertyOrder({"lowerCorner", "upperCorner"})
public interface WmtsWGS84BoundingBox {

  @JacksonXmlProperty(namespace = WmtsServiceMetadata.XMLNS_OWS, localName = "LowerCorner")
  @Value.Derived
  @Value.Auxiliary
  default String getLowerCorner() {
    return String.join(
        " ", getLowerCornerValues().stream().map(String::valueOf).toArray(String[]::new));
  }

  @XmlIgnore
  List<Number> getLowerCornerValues();

  @JacksonXmlProperty(namespace = WmtsServiceMetadata.XMLNS_OWS, localName = "UpperCorner")
  @Value.Derived
  @Value.Auxiliary
  default String getUpperCorner() {
    return String.join(
        " ", getUpperCornerValues().stream().map(String::valueOf).toArray(String[]::new));
  }

  @XmlIgnore
  List<Number> getUpperCornerValues();

  @SuppressWarnings("UnstableApiUsage")
  Funnel<WmtsWGS84BoundingBox> FUNNEL =
      (from, into) -> {
        from.getLowerCornerValues().forEach(val -> into.putLong(val.longValue()));
        from.getUpperCornerValues().forEach(val -> into.putLong(val.longValue()));
      };
}
