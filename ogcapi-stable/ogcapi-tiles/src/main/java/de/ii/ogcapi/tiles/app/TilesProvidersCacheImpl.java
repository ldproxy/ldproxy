/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.app;

import static de.ii.ogcapi.tiles.app.TilesBuildingBlock.DATASET_TILES;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetLimitsGenerator;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetLimitsOgcApi;
import de.ii.ogcapi.tiles.domain.TilesProviders;
import de.ii.ogcapi.tiles.domain.TilesProvidersCache;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.tiles.domain.MinMax;
import de.ii.xtraplatform.tiles.domain.TileMatrixSet;
import de.ii.xtraplatform.tiles.domain.TileMatrixSetRepository;
import de.ii.xtraplatform.tiles.domain.TileProvider;
import de.ii.xtraplatform.tiles.domain.TilesetMetadata;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class TilesProvidersCacheImpl extends AbstractVolatileComposed
    implements TilesProvidersCache {

  private static final Logger LOGGER = LoggerFactory.getLogger(TilesProvidersCacheImpl.class);

  private final TilesProviders tilesProviders;
  private final TileMatrixSetLimitsGenerator limitsGenerator;
  private final TileMatrixSetRepository tileMatrixSetRepository;

  @Inject
  public TilesProvidersCacheImpl(
      TilesProviders tilesProviders,
      TileMatrixSetLimitsGenerator limitsGenerator,
      TileMatrixSetRepository tileMatrixSetRepository,
      VolatileRegistry volatileRegistry) {
    super(volatileRegistry, true);
    this.tilesProviders = tilesProviders;
    this.limitsGenerator = limitsGenerator;
    this.tileMatrixSetRepository = tileMatrixSetRepository;

    onVolatileStart();

    addSubcomponent(tileMatrixSetRepository);

    onVolatileStarted();
  }

  @Override
  public void deleteTiles(
      OgcApi api,
      Optional<String> collectionId,
      Optional<String> tileMatrixSetId,
      Optional<BoundingBox> boundingBox) {
    LOGGER.info(
        "Purging tile cache for collection '{}', tiling scheme '{}', bounding box '{}'",
        collectionId.orElse("*"),
        tileMatrixSetId.orElse("*"),
        boundingBox.isEmpty()
            ? "*"
            : String.format(
                Locale.US,
                "%f,%f,%f,%f",
                boundingBox.get().getXmin(),
                boundingBox.get().getYmin(),
                boundingBox.get().getXmax(),
                boundingBox.get().getYmax()));

    OgcApiDataV2 apiData = api.getData();
    Optional<TilesetMetadata> tilesetMetadata =
        tilesProviders.getTilesetMetadata(
            apiData, collectionId.flatMap(apiData::getCollectionData));
    if (tilesetMetadata.isEmpty()) return;

    Map<String, MinMax> zoomLevels = tilesetMetadata.get().getLevels();

    Map<String, MinMax> relevantZoomLevels =
        tileMatrixSetId
            .map(
                tmsId ->
                    zoomLevels.entrySet().stream()
                        .filter(entry -> Objects.equals(tmsId, entry.getKey()))
                        .collect(
                            Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)))
            .orElse(zoomLevels);

    Map<String, BoundingBox> relevantBoundingBoxes =
        relevantZoomLevels.keySet().stream()
            .map(
                tmsId -> {
                  TileMatrixSet tileMatrixSet = tileMatrixSetRepository.get(tmsId).orElseThrow();
                  BoundingBox bbox =
                      boundingBox.orElseGet(
                          () ->
                              api.getSpatialExtent(collectionId)
                                  .orElse(tileMatrixSet.getBoundingBox()));
                  return new SimpleImmutableEntry<>(tmsId, bbox);
                })
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    deleteTiles(apiData, collectionId, relevantZoomLevels, relevantBoundingBoxes);

    LOGGER.info("Purging tile cache has finished");
  }

  private void deleteTiles(
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      Map<String, MinMax> zoomLevels,
      Map<String, BoundingBox> boundingBoxes) {
    for (Map.Entry<String, MinMax> tileSet : zoomLevels.entrySet()) {
      TileMatrixSet tileMatrixSet = tileMatrixSetRepository.get(tileSet.getKey()).orElseThrow();
      MinMax levels = tileSet.getValue();
      BoundingBox bbox = boundingBoxes.get(tileSet.getKey());

      // first the dataset tiles
      deleteTiles(apiData, Optional.empty(), tileMatrixSet, levels, bbox);

      if (collectionId.isPresent()) {
        // also the single collection tiles for the collection
        deleteTiles(apiData, collectionId, tileMatrixSet, levels, bbox);
      } else {
        // all single collection tiles
        for (String colId : apiData.getCollections().keySet()) {
          deleteTiles(apiData, Optional.of(colId), tileMatrixSet, levels, bbox);
        }
      }
    }
  }

  private void deleteTiles(
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      TileMatrixSet tileMatrixSet,
      MinMax levels,
      BoundingBox bbox) {
    List<TileMatrixSetLimitsOgcApi> limitsList =
        limitsGenerator.getTileMatrixSetLimits(bbox, tileMatrixSet, levels);
    for (TileMatrixSetLimitsOgcApi limits : limitsList) {
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(
            "Deleting tiles from cache: API {}, collection {}, tiles {}/{}/{}-{}/{}-{}, TMS rows {}-{}",
            apiData.getId(),
            collectionId.orElse(DATASET_TILES),
            tileMatrixSet.getId(),
            limits.getTileMatrix(),
            limits.getMinTileRow(),
            limits.getMaxTileRow(),
            limits.getMinTileCol(),
            limits.getMaxTileCol(),
            tileMatrixSet.getTmsRow(
                Integer.parseInt(limits.getTileMatrix()), limits.getMaxTileRow()),
            tileMatrixSet.getTmsRow(
                Integer.parseInt(limits.getTileMatrix()), limits.getMinTileRow()));
      }

      tilesProviders
          .getTileProviderOrThrow(
              apiData, collectionId.flatMap(apiData::getCollectionData), TileProvider::seeding)
          .deleteFromCache(collectionId.orElse(DATASET_TILES), tileMatrixSet, limits);
    }
  }
}
