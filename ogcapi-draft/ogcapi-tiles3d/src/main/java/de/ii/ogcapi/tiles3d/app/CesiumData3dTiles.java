/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.app;

import de.ii.xtraplatform.crs.domain.BoundingBox;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class CesiumData3dTiles {

  public final boolean hasTerrainProvider;
  public final Optional<String> ionAccessToken;
  public final Optional<String> maptilerApiKey;
  public final Optional<String> customTerrainProviderUri;
  public final Optional<Double> terrainHeightDifference;
  public final Double centerLon;
  public final Double centerLat;
  public final Double centerHeight;
  public final String encodedTileset;

  public CesiumData3dTiles(
      String tilesetJson,
      Optional<BoundingBox> bbox,
      Optional<String> ionAccessToken,
      Optional<String> maptilerApiKey,
      Optional<String> customTerrainProviderUri,
      Optional<Double> terrainHeightDifference) {
    this.ionAccessToken = ionAccessToken;
    this.maptilerApiKey = maptilerApiKey;
    this.customTerrainProviderUri = customTerrainProviderUri;
    this.terrainHeightDifference = terrainHeightDifference;
    this.hasTerrainProvider = maptilerApiKey.isPresent() && customTerrainProviderUri.isPresent();
    this.encodedTileset = URLEncoder.encode(tilesetJson, StandardCharsets.UTF_8);
    this.centerLon =
        bbox.map(box -> Math.toRadians((box.getXmax() + box.getXmin()) / 2)).orElse(null);
    this.centerLat =
        bbox.map(box -> Math.toRadians((box.getYmax() + box.getYmin()) / 2)).orElse(null);
    this.centerHeight = bbox.map(box -> (box.getZmax() + box.getZmin()) / 2).orElse(null);
  }

  public boolean fromTiles() {
    return true;
  }
}
