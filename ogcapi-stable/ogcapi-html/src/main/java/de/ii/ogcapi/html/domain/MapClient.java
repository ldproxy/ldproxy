/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.html.domain;

import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(builder = "new", deepImmutablesDetection = true)
public interface MapClient {

  enum Type {
    MAP_LIBRE,
    OPEN_LAYERS,
    CESIUM
  }

  enum Popup {
    HOVER_ID,
    CLICK_PROPERTIES
  }

  @Value.Default
  default Type getType() {
    return Type.MAP_LIBRE;
  }

  Optional<String> getStyleUrl();

  Optional<String> getBackgroundUrl();

  Optional<Source> getData();

  Optional<String> getAttribution();

  @Value.Default
  default boolean getSavePosition() {
    return false;
  }

  List<Double> getCenter();

  Optional<Number> getZoom();

  Optional<Map<String, String>> getBounds();

  @Value.Default
  default boolean getUseBounds() {
    return false;
  }

  @Value.Default
  default boolean drawBounds() {
    return false;
  }

  @Value.Default
  default boolean isInteractive() {
    return true;
  }

  @Value.Default
  default MapClient.Style getDefaultStyle() {
    return new ImmutableStyle.Builder().build();
  }

  @Value.Default
  default boolean getRemoveZoomLevelConstraints() {
    return false;
  }

  Optional<Popup> getPopup();

  Optional<Set<Entry<String, String>>> getFeatureTitles();

  Optional<Set<Entry<String, Collection<String>>>> getLayerGroupControl();

  @Value.Lazy
  default boolean isMapLibre() {
    return getType() == Type.MAP_LIBRE;
  }

  @Value.Lazy
  default boolean isOpenLayers() {
    return getType() == Type.OPEN_LAYERS;
  }

  @Value.Lazy
  default boolean isCesium() {
    return getType() == Type.CESIUM;
  }

  @Value.Immutable
  interface Source {
    enum TYPE {
      geojson,
      vector,
      raster
    }

    TYPE getType();

    String getUrl();

    @Value.Default
    default boolean isData() {
      return false;
    }

    Multimap<String, List<String>> getLayers();
  }

  @Value.Immutable
  interface Style {

    @Value.Default
    default String getColor() {
      return "#1D4E89";
    }

    @Value.Default
    default double getOpacity() {
      return 1.0;
    }

    @Value.Default
    default int getCircleRadius() {
      return 8;
    }

    @Value.Default
    default int getCircleMinZoom() {
      return 0;
    }

    @Value.Default
    default int getCircleMaxZoom() {
      return 24;
    }

    @Value.Default
    default int getLineWidth() {
      return 4;
    }

    @Value.Default
    default int getLineMinZoom() {
      return 0;
    }

    @Value.Default
    default int getLineMaxZoom() {
      return 24;
    }

    @Value.Default
    default double getFillOpacity() {
      return 0.2;
    }

    @Value.Default
    default int getOutlineWidth() {
      return 2;
    }

    @Value.Default
    default int getPolygonMinZoom() {
      return 0;
    }

    @Value.Default
    default int getPolygonMaxZoom() {
      return 24;
    }
  }
}
