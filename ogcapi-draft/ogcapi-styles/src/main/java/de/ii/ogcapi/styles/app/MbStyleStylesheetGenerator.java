/*
 * Copyright 2024 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app;

import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.styles.domain.ImmutableArrayValue;
import de.ii.ogcapi.styles.domain.ImmutableMbStyleLayer;
import de.ii.ogcapi.styles.domain.ImmutableMbStyleRasterSource;
import de.ii.ogcapi.styles.domain.ImmutableMbStyleStylesheet.Builder;
import de.ii.ogcapi.styles.domain.ImmutableMbStyleVectorSource;
import de.ii.ogcapi.styles.domain.ImmutableNumberValue;
import de.ii.ogcapi.styles.domain.ImmutableStringValue;
import de.ii.ogcapi.styles.domain.MbStyleLayer.LayerType;
import de.ii.ogcapi.styles.domain.MbStyleStylesheet;
import de.ii.xtraplatform.entities.domain.EntityData;
import de.ii.xtraplatform.entities.domain.EntityDataStore;
import de.ii.xtraplatform.values.domain.AutoValueFactory;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MbStyleStylesheetGenerator
    implements AutoValueFactory<MbStyleStylesheet, String, Map<String, String>> {

  private final EntityDataStore<OgcApiDataV2> entityDataStore;

  public MbStyleStylesheetGenerator(EntityDataStore<EntityData> entityDataStore) {
    this.entityDataStore = entityDataStore.forType(OgcApiDataV2.class);
  }

  public Map<String, String> check(String apiId) {
    return Map.of();
  }

  private int colorIndex = 0;

  private String generateColorForCollection(String collectionName) {
    List<String> mapboxColors =
        Arrays.asList(
            "#3887be", // Mapbox Cyan-Blue Azure
            "#56b881", // Mapbox Emerald
            "#50667f", // Dark Electric Blue
            "#41afa5", // Mapbox Keppel
            "#e55e5e", // Fire Opal
            "#8a8acb", // Ube
            "#ed6498", // Light Crimson
            "#fbb03b", // Mapbox Yellow Orange
            "#28353d", // Mapbox Gunmetal
            "#f9886c", // Mapbox Salmon
            "#3bb2d0" // Mapbox Maximum Blue
            );

    String color = mapboxColors.get(colorIndex);
    colorIndex = (colorIndex + 1) % mapboxColors.size();

    return color;
  }

  @Override
  public Map<String, String> analyze(String apiId) {

    if (!entityDataStore.hasAny(apiId)) {
      throw new IllegalArgumentException("No API found with the id: " + apiId);
    }

    // get api from entityDataStore
    OgcApiDataV2 apiData = entityDataStore.get(entityDataStore.fullIdentifier(apiId));

    // extract collections
    Map<String, ?> collections = apiData.getCollections();

    // create a map of collection names to colors
    Map<String, String> collectionColors = new LinkedHashMap<>();
    for (String collectionName : collections.keySet()) {
      // generate a color for each collection
      String color = generateColorForCollection(collectionName);
      collectionColors.put(collectionName, color);
    }

    return collectionColors;
  }

  @Override
  public MbStyleStylesheet generate(String apiId, Map<String, String> collectionColors) {
    Builder style = new Builder().version(8).zoom(12);

    // add base map
    style.putSources(
        "osm",
        ImmutableMbStyleRasterSource.builder()
            .tiles(
                List.of(
                    "https://a.tile.openstreetmap.org/{z}/{x}/{y}.png",
                    "https://b.tile.openstreetmap.org/{z}/{x}/{y}.png",
                    "https://c.tile.openstreetmap.org/{z}/{x}/{y}.png"))
            .attribution(
                "&copy; <a href=\"http://osm.org/copyright\">OpenStreetMap</a> contributors")
            .build());

    style.addLayers(
        ImmutableMbStyleLayer.builder().id("basemap").type(LayerType.raster).source("osm").build());

    // add source for api
    style.putSources(
        apiId,
        ImmutableMbStyleVectorSource.builder()
            .tiles(List.of("{serviceUrl}/tiles/WebMercatorQuad/{z}/{y}/{x}?f=mvt"))
            .build());

    // iterate over each collection
    for (String collectionName : collectionColors.keySet()) {
      String color = collectionColors.get(collectionName);
      // add layers for each collection
      style.addLayers(
          ImmutableMbStyleLayer.builder()
              .id(collectionName + ".fill")
              .type(LayerType.fill)
              .source(apiId)
              .sourceLayer(collectionName)
              .putPaint("fill-color", ImmutableStringValue.of(color))
              .filter(
                  ImmutableArrayValue.of(
                      List.of(
                          ImmutableStringValue.of("=="),
                          ImmutableArrayValue.of(List.of(ImmutableStringValue.of("geometry-type"))),
                          ImmutableStringValue.of("Polygon"))))
              .build(),
          ImmutableMbStyleLayer.builder()
              .id(collectionName + ".line")
              .type(LayerType.line)
              .source(apiId)
              .sourceLayer(collectionName)
              .putPaint("line-color", ImmutableStringValue.of(color))
              .putPaint("line-width", ImmutableNumberValue.of(2))
              .filter(
                  ImmutableArrayValue.of(
                      List.of(
                          ImmutableStringValue.of("=="),
                          ImmutableArrayValue.of(List.of(ImmutableStringValue.of("geometry-type"))),
                          ImmutableStringValue.of("LineString"))))
              .build(),
          ImmutableMbStyleLayer.builder()
              .id(collectionName + ".circle")
              .type(LayerType.circle)
              .source(apiId)
              .sourceLayer(collectionName)
              .putPaint("circle-radius", ImmutableNumberValue.of(3))
              .putPaint("circle-opacity", ImmutableNumberValue.of(0.5))
              .putPaint("circle-stroke-color", ImmutableStringValue.of(color))
              .putPaint("circle-stroke-width", ImmutableNumberValue.of(1))
              .putPaint("circle-color", ImmutableStringValue.of(color))
              .filter(
                  ImmutableArrayValue.of(
                      List.of(
                          ImmutableStringValue.of("=="),
                          ImmutableArrayValue.of(List.of(ImmutableStringValue.of("geometry-type"))),
                          ImmutableStringValue.of("Point"))))
              .build());
    }

    return style.build();
  }
}
