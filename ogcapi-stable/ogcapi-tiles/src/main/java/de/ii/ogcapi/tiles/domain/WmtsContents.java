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
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonPropertyOrder({"layers", "tileMatrixSets"})
public interface WmtsContents {

  @JacksonXmlElementWrapper(useWrapping = false)
  @JacksonXmlProperty(namespace = WmtsServiceMetadata.XMLNS, localName = "Layer")
  List<WmtsLayer> getLayers();

  @JacksonXmlElementWrapper(useWrapping = false)
  @JacksonXmlProperty(namespace = WmtsServiceMetadata.XMLNS, localName = "TileMatrixSet")
  List<WmtsTileMatrixSet> getTileMatrixSets();

  @SuppressWarnings("UnstableApiUsage")
  Funnel<WmtsContents> FUNNEL =
      (from, into) -> {
        from.getLayers().forEach(val -> WmtsLayer.FUNNEL.funnel(val, into));
        from.getTileMatrixSets().forEach(val -> WmtsTileMatrixSet.FUNNEL.funnel(val, into));
      };
}
