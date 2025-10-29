/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.OgcResourceMetadata;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetOgcApi;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaObject;
import de.ii.xtraplatform.tiles.domain.TilesBoundingBox;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonDeserialize(as = ImmutableTileLayer.class)
public abstract class TileLayer extends OgcResourceMetadata {

  public abstract String getId();

  public abstract TileSet.DataType getDataType();

  public abstract Optional<String> getFeatureType();

  public abstract Optional<Integer> getGeometryDimension();

  public abstract Optional<String> getTheme();

  public abstract Optional<String> getMinTileMatrix();

  public abstract Optional<String> getMaxTileMatrix();

  public abstract Optional<Double> getMinCellSize();

  public abstract Optional<Double> getMaxCellSize();

  public abstract Optional<Double> getMinScaleDenominator();

  public abstract Optional<Double> getMaxScaleDenominator();

  public abstract Optional<TilesBoundingBox> getBoundingBox();

  public abstract Optional<JsonSchemaObject> getPropertiesSchema();

  // this is for map tiles, so we do not support the following for now:
  // public abstract Optional<StyleEntry> getStyle();

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<TileLayer> FUNNEL =
      (from, into) -> {
        OgcResourceMetadata.FUNNEL.funnel(from, into);
        into.putString(from.getId(), StandardCharsets.UTF_8);
        into.putString(from.getDataType().toString(), StandardCharsets.UTF_8);
        from.getFeatureType().ifPresent(val -> into.putString(val, StandardCharsets.UTF_8));
        from.getGeometryDimension().ifPresent(into::putInt);
        from.getTheme().ifPresent(val -> into.putString(val, StandardCharsets.UTF_8));
        from.getMinTileMatrix().ifPresent(val -> into.putString(val, StandardCharsets.UTF_8));
        from.getMaxTileMatrix().ifPresent(val -> into.putString(val, StandardCharsets.UTF_8));
        from.getMinCellSize().ifPresent(into::putDouble);
        from.getMaxCellSize().ifPresent(into::putDouble);
        from.getMinScaleDenominator().ifPresent(into::putDouble);
        from.getMaxScaleDenominator().ifPresent(into::putDouble);
        from.getBoundingBox().ifPresent(val -> TileMatrixSetOgcApi.FUNNEL_BBOX.funnel(val, into));
        from.getPropertiesSchema().ifPresent(val -> JsonSchema.FUNNEL.funnel(val, into));
      };
}
