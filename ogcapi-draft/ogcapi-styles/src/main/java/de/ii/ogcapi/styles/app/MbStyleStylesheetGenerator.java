/*
 * Copyright 2024 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app;

import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.styles.domain.ImmutableMbStyleLayer;
import de.ii.ogcapi.styles.domain.ImmutableMbStyleStylesheet.Builder;
import de.ii.ogcapi.styles.domain.ImmutableMbStyleVectorSource;
import de.ii.ogcapi.styles.domain.MbStyleLayer.LayerType;
import de.ii.ogcapi.styles.domain.MbStyleStylesheet;
import de.ii.xtraplatform.entities.domain.EntityData;
import de.ii.xtraplatform.entities.domain.EntityDataStore;
import de.ii.xtraplatform.values.domain.AutoValueFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MbStyleStylesheetGenerator
    implements AutoValueFactory<MbStyleStylesheet, String, Map<String, String>> {

  private final EntityDataStore<OgcApiDataV2> entityDataStore;

  public MbStyleStylesheetGenerator(EntityDataStore<EntityData> entityDataStore) {
    this.entityDataStore = entityDataStore.forType(OgcApiDataV2.class);
  }

  public Map<String, String> check(String apiId) {
    return Map.of();
  }

  private Map<String, String> usedColors = new HashMap<>();

  private String generateColorForCollection(String collectionName) {
    List<String> mapboxColors =
        Arrays.asList(
            "#3bb2d0", // Mapbox Maximum Blue
            "#3887be", // Mapbox Cyan-Blue Azure
            "#8a8acb", // Ube
            "#56b881", // Mapbox Emerald
            "#50667f", // Dark Electric Blue
            "#41afa5", // Mapbox Keppel
            "#f9886c", // Mapbox Salmon
            "#e55e5e", // Fire Opal
            "#ed6498", // Light Crimson
            "#fbb03b", // Mapbox Yellow Orange
            "#142736", // Mapbox Yankees Blue
            "#28353d", // Mapbox Gunmetal
            "#222b30" // Mapbox Charleston Green
            );

    Random random = new Random();
    String color;

    do {
      color = mapboxColors.get(random.nextInt(mapboxColors.size()));
    } while (usedColors.containsValue(color) && usedColors.size() < mapboxColors.size());

    usedColors.put(collectionName, color);

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
    Builder style = new Builder().version(8);

    // add source for api
    style.putSources(
        apiId,
        ImmutableMbStyleVectorSource.builder()
            .tiles(
                Collections.singletonList("{serviceUrl}/tiles/WebMercatorQuad/{z}/{y}/{x}?f=mvt"))
            .build());

    // iterate over each collection
    for (String collectionName : collectionColors.keySet()) {
      String color = collectionColors.get(collectionName);
      // add layers for each collection
      style.addLayers(
          ImmutableMbStyleLayer.builder()
              .id(collectionName + ".fill")
              .type(LayerType.fill)
              .source(collectionName)
              .sourceLayer(apiId)
              .putPaint("fill-color", color)
              .build(),
          ImmutableMbStyleLayer.builder()
              .id(collectionName + ".line")
              .type(LayerType.line)
              .source(collectionName)
              .sourceLayer(apiId)
              .putPaint("line-color", color)
              .putPaint("line-width", 2)
              .build(),
          ImmutableMbStyleLayer.builder()
              .id(collectionName + ".circle")
              .type(LayerType.circle)
              .source(collectionName)
              .sourceLayer(apiId)
              .putPaint("circle-radius", 3)
              .putPaint("circle-opacity", 0.5)
              .putPaint("circle-stroke-color", color)
              .putPaint("circle-stroke-width", 1)
              .putPaint("circle-color", color)
              .build());
    }

    return style.build();
  }
}
