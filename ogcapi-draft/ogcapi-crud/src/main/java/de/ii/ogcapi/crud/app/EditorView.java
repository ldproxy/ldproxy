/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crud.app;

import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.html.domain.ImmutableMapClient.Builder;
import de.ii.ogcapi.html.domain.ImmutableSource;
import de.ii.ogcapi.html.domain.MapClient;
import de.ii.ogcapi.html.domain.MapClient.Popup;
import de.ii.ogcapi.html.domain.MapClient.Source.TYPE;
import de.ii.ogcapi.html.domain.MapClient.Type;
import de.ii.ogcapi.html.domain.OgcApiView;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(builder = "new")
public abstract class EditorView extends OgcApiView {

  public EditorView() {
    super("editor.mustache");
  }

  public abstract Optional<BoundingBox> getSpatialExtent();

  public abstract String getCollectionId();

  public abstract Optional<String> getStyleUrl();

  public abstract Optional<String> getLogoutUrl();

  @Value.Derived
  public Map<String, String> getBbox() {
    return getSpatialExtent()
        .map(
            bbox ->
                ImmutableMap.of(
                    "minLng", Double.toString(bbox.getXmin()),
                    "minLat", Double.toString(bbox.getYmin()),
                    "maxLng", Double.toString(bbox.getXmax()),
                    "maxLat", Double.toString(bbox.getYmax())))
        .orElse(ImmutableMap.<String, String>of());
  }

  @Value.Derived
  public MapClient getMapClient() {
    return new Builder()
        .backgroundUrl(
            Optional.ofNullable(htmlConfig())
                .map(HtmlConfiguration::getLeafletUrl)
                .or(() -> Optional.ofNullable(htmlConfig()).map(HtmlConfiguration::getBasemapUrl)))
        .attribution(getAttribution())
        .bounds(Optional.ofNullable(getBbox()))
        .type(Type.OPEN_LAYERS)
        .data(
            new ImmutableSource.Builder()
                .type(TYPE.geojson)
                .url(uriCustomizer().removeLastPathSegments(3).clearParameters().toString())
                .putLayers(getCollectionId(), List.of(getCollectionId()))
                .build())
        .popup(Popup.HOVER_ID)
        /*.styleUrl(Optional.ofNullable(styleUrl))
        .removeZoomLevelConstraints(removeZoomLevelConstraints)*/
        .build();
  }
}
