/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tilematrixsets.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetLimitsGenerator;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetLimitsOgcApi;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.CrsTransformationException;
import de.ii.xtraplatform.crs.domain.CrsTransformer;
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.tiles.domain.MinMax;
import de.ii.xtraplatform.tiles.domain.TileMatrixSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for generating a list of tileMatrixSetLimits in json responses for
 * /tiles and /collections/{collectionsId}/tiles requests.
 */
@Singleton
@AutoBind
public class TileMatrixSetLimitsGeneratorImpl implements TileMatrixSetLimitsGenerator {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(TileMatrixSetLimitsGeneratorImpl.class);
  private final CrsTransformerFactory crsTransformerFactory;

  @Inject
  public TileMatrixSetLimitsGeneratorImpl(CrsTransformerFactory crsTransformerFactory) {
    this.crsTransformerFactory = crsTransformerFactory;
  }

  @Override
  public List<TileMatrixSetLimitsOgcApi> getTileMatrixSetLimits(
      OgcApi api,
      TileMatrixSet tileMatrixSet,
      MinMax tileMatrixRange,
      Optional<String> collectionId) {
    return tileMatrixSet
        .getLimitsList(tileMatrixRange, getBoundingBox(api, tileMatrixSet, collectionId))
        .stream()
        .map(TileMatrixSetLimitsOgcApi::of)
        .collect(Collectors.toList());
  }

  @Override
  public TileMatrixSetLimitsOgcApi getTileMatrixSetLimits(
      OgcApi api, TileMatrixSet tileMatrixSet, int tileMatrix, Optional<String> collectionId) {
    return TileMatrixSetLimitsOgcApi.of(
        tileMatrixSet.getLimits(tileMatrix, getBoundingBox(api, tileMatrixSet, collectionId)));
  }

  /**
   * Return a list of tileMatrixSetLimits for all collections in the dataset.
   *
   * @param boundingBox a bounding box
   * @param tileMatrixSet the tile matrix set
   * @return list of TileMatrixSetLimits
   */
  @Override
  public List<TileMatrixSetLimitsOgcApi> getTileMatrixSetLimits(
      BoundingBox boundingBox, TileMatrixSet tileMatrixSet, MinMax tileMatrixRange) {

    Optional<BoundingBox> bbox =
        getBoundingBoxInTargetCrs(boundingBox, tileMatrixSet.getCrs(), crsTransformerFactory);

    if (bbox.isEmpty()) {
      // fallback to bbox of the tile matrix set
      LOGGER.debug(
          "Bounding box cannot be transformed to the CRS of the tile matrix set. Using the tile matrix set bounding box.");
      bbox = Optional.of(tileMatrixSet.getBoundingBox());
    }

    return tileMatrixSet.getLimitsList(tileMatrixRange, bbox.get()).stream()
        .map(TileMatrixSetLimitsOgcApi::of)
        .collect(Collectors.toList());
  }

  private static BoundingBox getBoundingBox(
      OgcApi api, TileMatrixSet tileMatrixSet, Optional<String> collectionId) {
    Optional<BoundingBox> boundingBox =
        collectionId.isPresent()
            ? api.getSpatialExtent(collectionId.get(), tileMatrixSet.getCrs())
            : api.getSpatialExtent(tileMatrixSet.getCrs());

    if (boundingBox.isPresent()) {
      return boundingBox.get();
    }

    // fallback to bbox of the tile matrix set
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace(
          "No bounding box found or bounding box cannot be transformed to the CRS of the tile matrix set. Using the tile matrix set bounding box.");
    }

    return tileMatrixSet.getBoundingBox();
  }

  // TODO move
  /**
   * convert a bounding box to a bounding box in another CRS
   *
   * @param bbox the bounding box in some CRS
   * @param targetCrs the target CRS
   * @param crsTransformerFactory the factory for CRS transformations
   * @return the converted bounding box
   */
  private static Optional<BoundingBox> getBoundingBoxInTargetCrs(
      BoundingBox bbox, EpsgCrs targetCrs, CrsTransformerFactory crsTransformerFactory) {
    EpsgCrs sourceCrs = bbox.getEpsgCrs();
    if (sourceCrs.getCode() == targetCrs.getCode()
        && sourceCrs.getForceAxisOrder() == targetCrs.getForceAxisOrder()) return Optional.of(bbox);

    Optional<CrsTransformer> transformer =
        crsTransformerFactory.getTransformer(sourceCrs, targetCrs);
    if (transformer.isPresent()) {
      try {
        return Optional.ofNullable(transformer.get().transformBoundingBox(bbox));
      } catch (CrsTransformationException e) {
        LOGGER.error(
            String.format(
                Locale.US,
                "Cannot convert bounding box (%f, %f, %f, %f) from %s to %s. Reason: %s",
                bbox.getXmin(),
                bbox.getYmin(),
                bbox.getXmax(),
                bbox.getYmax(),
                sourceCrs,
                targetCrs,
                e.getMessage()));
        return Optional.empty();
      }
    }
    LOGGER.error(
        String.format(
            Locale.US,
            "Cannot convert bounding box (%f, %f, %f, %f) from %s to %s. Reason: no applicable transformer found.",
            bbox.getXmin(),
            bbox.getYmin(),
            bbox.getXmax(),
            bbox.getYmax(),
            sourceCrs,
            targetCrs));
    return Optional.empty();
  }
}
