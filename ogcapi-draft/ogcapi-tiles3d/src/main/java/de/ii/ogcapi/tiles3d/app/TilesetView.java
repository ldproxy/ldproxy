/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.app;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import de.ii.ogcapi.html.domain.ImmutableMapClient;
import de.ii.ogcapi.html.domain.MapClient;
import de.ii.ogcapi.html.domain.MapClient.Type;
import de.ii.ogcapi.html.domain.OgcApiView;
import de.ii.ogcapi.tiles3d.domain.Tiles3dConfiguration;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.tiles3d.domain.spec.Tileset3d;
import java.util.Objects;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public abstract class TilesetView extends OgcApiView {

  public TilesetView() {
    super("globe.mustache");
  }

  @Override
  public String title() {
    return "3D Tiles";
  }

  public abstract Tileset3d tileset();

  @Value.Derived
  public String tilesetJson() {
    try {
      return new ObjectMapper()
          .registerModule(new Jdk8Module())
          .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
          .writeValueAsString(tileset());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not write 3D Tiles tileset json.", e);
    }
  }

  public abstract Optional<String> additionalStyleUrl();

  public abstract Optional<BoundingBox> spatialExtent();

  public abstract Optional<String> collectionId();

  @Value.Derived
  @Override
  public String getAttribution() {
    String basemapAttribution = super.getAttribution();
    String tilesetAttribution =
        tileset().getAsset().getCopyright().map(att -> att.replace("'", "\\'")).orElse(null);
    if (Objects.nonNull(tilesetAttribution)) {
      if (Objects.nonNull(basemapAttribution)) {
        return String.join(" | ", tilesetAttribution, basemapAttribution);
      } else {
        return tilesetAttribution;
      }
    }
    return basemapAttribution;
  }

  public MapClient mapClient() {
    return new ImmutableMapClient.Builder()
        .type(Type.CESIUM)
        .backgroundUrl(
            Objects.nonNull(htmlConfig())
                ? Optional.ofNullable(htmlConfig().getBasemapUrl())
                    .map(
                        url ->
                            url.replace("{z}", "{TileMatrix}")
                                .replace("{y}", "{TileRow}")
                                .replace("{x}", "{TileCol}"))
                : Optional.empty())
        .attribution(getAttribution())
        .build();
  }

  public CesiumData3dTiles cesiumData() {
    Tiles3dConfiguration tiles3dConfig =
        collectionId().isPresent()
            ? apiData().getExtension(Tiles3dConfiguration.class, collectionId().get()).orElseThrow()
            : apiData().getExtension(Tiles3dConfiguration.class).orElseThrow();
    return new CesiumData3dTiles(
        tilesetJson(),
        spatialExtent(),
        tiles3dConfig.getIonAccessToken(),
        tiles3dConfig.getMaptilerApiKey(),
        tiles3dConfig.getCustomTerrainProviderUri(),
        tiles3dConfig.getTerrainHeightDifference());
  }
}
