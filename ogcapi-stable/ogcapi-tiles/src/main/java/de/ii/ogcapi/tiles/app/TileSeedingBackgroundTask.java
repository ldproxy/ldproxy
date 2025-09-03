/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.WithChangeListeners;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiBackgroundTask;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.tiles.domain.TilesConfiguration;
import de.ii.ogcapi.tiles.domain.TilesProviders;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.base.domain.resiliency.OptionalCapability;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.features.domain.DatasetChangeListener;
import de.ii.xtraplatform.features.domain.FeatureChangeListener;
import de.ii.xtraplatform.jobs.domain.JobQueue;
import de.ii.xtraplatform.jobs.domain.JobSet;
import de.ii.xtraplatform.services.domain.TaskContext;
import de.ii.xtraplatform.tiles.domain.ImmutableTileGenerationParameters;
import de.ii.xtraplatform.tiles.domain.SeedingOptions;
import de.ii.xtraplatform.tiles.domain.TileGenerationParameters;
import de.ii.xtraplatform.tiles.domain.TileProvider;
import de.ii.xtraplatform.tiles.domain.TileProviderFeaturesData;
import de.ii.xtraplatform.tiles.domain.TileSeedingJobSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for a automatic generation of the Tiles. The range is specified in the
 * config. The automatic generation is executed, when the server is started/restarted.
 */
@Singleton
@AutoBind
public class TileSeedingBackgroundTask implements OgcApiBackgroundTask, WithChangeListeners {

  private static final Logger LOGGER = LoggerFactory.getLogger(TileSeedingBackgroundTask.class);

  private final FeaturesCoreProviders providers;
  private final TilesProviders tilesProviders;
  private final VolatileRegistry volatileRegistry;
  private final JobQueue jobQueue;

  @Inject
  public TileSeedingBackgroundTask(
      FeaturesCoreProviders providers,
      TilesProviders tilesProviders,
      VolatileRegistry volatileRegistry,
      JobQueue jobQueue) {
    this.providers = providers;
    this.tilesProviders = tilesProviders;
    this.volatileRegistry = volatileRegistry;
    this.jobQueue = jobQueue;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    // check that we have a tile provider with seeding support
    if (tilesProviders
        .getTileProvider(apiData)
        .map(provider -> provider.seeding().isSupported())
        .isEmpty()) {
      return false;
    }

    return OgcApiBackgroundTask.super.isEnabledForApi(apiData);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return TilesConfiguration.class;
  }

  @Override
  public Class<OgcApi> getServiceType() {
    return OgcApi.class;
  }

  @Override
  public String getLabel() {
    return "Tile cache seeding";
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    providers
        .getFeatureProvider(api.getData())
        .ifPresent(provider -> updateChangeListeners(provider.changes(), api));

    return ValidationResult.of();
  }

  @Override
  public void onShutdown(OgcApi api) {
    providers
        .getFeatureProvider(api.getData())
        .ifPresent(provider -> removeChangeListeners(provider.changes(), api));

    OgcApiBackgroundTask.super.onShutdown(api);
  }

  // TODO: seeding options without available
  @Override
  public boolean runOnStart(OgcApi api) {
    return isEnabledForApi(api.getData())
        && tilesProviders
            .getTileProvider(api.getData())
            .filter(provider -> provider.seeding().isSupported())
            .map(provider -> provider.seeding().get().getOptions())
            .filter(SeedingOptions::shouldRunOnStartup)
            .isPresent();
  }

  @Override
  public Optional<String> runPeriodic(OgcApi api) {
    if (!isEnabledForApi(api.getData())) {
      return Optional.empty();
    }
    return tilesProviders
        .getTileProvider(api.getData())
        .filter(provider -> provider.seeding().isSupported())
        .map(provider -> provider.seeding().get().getOptions())
        .flatMap(SeedingOptions::getCronExpression);
  }

  @Override
  public int getMaxPartials(OgcApi api) {
    return 1;
  }

  @Override
  public boolean isSilent() {
    return true;
  }

  private boolean shouldPurge(OgcApi api) {
    return tilesProviders
        .getTileProvider(api.getData())
        .filter(provider -> provider.seeding().isSupported())
        .map(provider -> provider.seeding().get().getOptions())
        .filter(SeedingOptions::shouldPurge)
        .isPresent();
  }

  /**
   * Run the seeding
   *
   * @param api
   * @param taskContext
   */
  @Override
  public void run(OgcApi api, TaskContext taskContext) {
    CompletableFuture<Void> waitForVolatiles =
        volatileRegistry
            .onAvailable(tilesProviders.getTileProviderOrThrow(api.getData()))
            .toCompletableFuture();

    if (!waitForVolatiles.isDone()) {
      LOGGER.info("Tile cache seeding suspended");
      waitForVolatiles.join();
      LOGGER.info("Tile cache seeding resumed");
    }

    boolean reseed = shouldPurge(api);

    try {
      if (!taskContext.isStopped()) {
        seedTilesetsFull(api, reseed);
      } else if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Seeding task context is stopped, skipping seeding");
      }

    } catch (IOException e) {
      if (!taskContext.isStopped()) {
        throw new RuntimeException("Error accessing the tile cache during seeding.", e);
      } else if (LOGGER.isDebugEnabled()) {
        LogContext.errorAsDebug(LOGGER, e, "Seeding task context is stopped, ignoring exception");
      }
    } catch (Throwable e) {
      // in general, this should only happen on shutdown (as we cannot influence shutdown order,
      // exceptions during seeding on shutdown are currently inevitable), but for other situations
      // we still add the error to the log
      if (!taskContext.isStopped()) {
        LogContext.error(
            LOGGER,
            e,
            "An error occurred during seeding. Note that this may be a side-effect of a server shutdown.");
        throw new RuntimeException(
            "An error occurred during seeding. Note that this may be a side-effect of a server shutdown.",
            e);
      } else if (LOGGER.isDebugEnabled()) {
        LogContext.errorAsDebug(LOGGER, e, "Seeding task context is stopped, ignoring exception");
      }
    }
  }

  private void seedTilesetsFull(OgcApi api, boolean reseed) throws IOException {
    TileProvider tileProvider = tilesProviders.getTileProviderOrThrow(api.getData());

    if (!tileProvider.seeding().isAvailable() || !tileProvider.generator().isAvailable()) {
      LOGGER.debug("Tile provider '{}' does not support seeding", tileProvider.getId());
      return;
    }

    boolean inProgress =
        jobQueue.getSets().stream()
            .anyMatch(
                jobSet ->
                    Objects.equals(jobSet.getType(), TileSeedingJobSet.TYPE)
                        && !jobSet.isDone()
                        && jobSet
                            .getEntity()
                            .filter(e -> Objects.equals(e, tileProvider.getId()))
                            .isPresent());

    if (inProgress) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("{} is already in progress, skipping new task", TileSeedingJobSet.LABEL);
      }

      return;
    }

    JobSet jobSet = getJobSet(api, tileProvider, reseed);

    jobQueue.push(jobSet);

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Added seeding job set to the queue ({})", jobSet.getId());
    }
  }

  private JobSet getJobSet(OgcApi api, TileProvider tileProvider, boolean reseed) {
    return getJobSet(api, tileProvider, reseed, Optional.empty(), Optional.empty());
  }

  private JobSet getJobSet(
      OgcApi api,
      TileProvider tileProvider,
      boolean reseed,
      Optional<String> collectionId,
      Optional<BoundingBox> bbox) {
    OgcApiDataV2 apiData = api.getData();
    Map<String, TileGenerationParameters> tilesets = new LinkedHashMap<>();
    Map<String, TileGenerationParameters> combinedTilesets = new LinkedHashMap<>();

    for (String cid : apiData.getCollections().keySet()) {
      if (collectionId.isPresent() && !collectionId.get().equals(cid)) {
        continue;
      }
      getTilesConfiguration(apiData, cid)
          .filter(cfg -> cfg.hasCollectionTiles(tilesProviders, apiData, cid))
          .map(cfg -> cfg.getCollectionTileset(cid))
          .ifPresent(
              tileset -> {
                TileGenerationParameters generationParameters =
                    new ImmutableTileGenerationParameters.Builder()
                        .clipBoundingBox(bbox.or(() -> api.getSpatialExtent(cid)))
                        .substitutions(
                            FeaturesCoreProviders.DEFAULT_SUBSTITUTIONS.apply(
                                api.getUri().toString()))
                        .build();

                if (((TileProviderFeaturesData) tileProvider.getData())
                    .getTilesets()
                    .get(tileset)
                    .isCombined()) {
                  combinedTilesets.putIfAbsent(tileset, generationParameters);
                } else {
                  tilesets.putIfAbsent(tileset, generationParameters);
                }
              });
    }

    apiData
        .getExtension(TilesConfiguration.class)
        .filter(cfg -> cfg.hasDatasetVectorTiles(tilesProviders, apiData))
        .map(TilesConfiguration::getDatasetTileset)
        .ifPresent(
            tileset -> {
              TileGenerationParameters generationParameters =
                  new ImmutableTileGenerationParameters.Builder()
                      .clipBoundingBox(bbox.or(() -> api.getSpatialExtent()))
                      .substitutions(
                          FeaturesCoreProviders.DEFAULT_SUBSTITUTIONS.apply(
                              api.getUri().toString()))
                      .build();

              if (((TileProviderFeaturesData) tileProvider.getData())
                  .getTilesets()
                  .get(tileset)
                  .isCombined()) {
                combinedTilesets.putIfAbsent(tileset, generationParameters);
              } else {
                tilesets.putIfAbsent(tileset, generationParameters);
              }
            });

    Map<String, List<String>> rasterForVectorTilesets =
        tilesets.keySet().stream()
            .map(
                tileset ->
                    Map.entry(
                        tileset,
                        tileProvider.access().get().getMapStyles(tileset).stream()
                            .map(
                                style ->
                                    tileProvider.access().get().getMapStyleTileset(tileset, style))
                            .collect(Collectors.toList())))
            .collect(ImmutableMap.toImmutableMap(Entry::getKey, Entry::getValue));

    Map<String, List<String>> rasterForVectorCombinedTilesets =
        combinedTilesets.keySet().stream()
            .map(
                tileset ->
                    Map.entry(
                        tileset,
                        tileProvider.access().get().getMapStyles(tileset).stream()
                            .map(
                                style ->
                                    tileProvider.access().get().getMapStyleTileset(tileset, style))
                            .collect(Collectors.toList())))
            .collect(ImmutableMap.toImmutableMap(Entry::getKey, Entry::getValue));

    int priority = tileProvider.seeding().get().getOptions().getEffectivePriority();

    JobSet jobSet = TileSeedingJobSet.of(tileProvider.getId(), tilesets, reseed, priority);

    Map<String, TileGenerationParameters> rasterTilesets =
        tilesets.entrySet().stream()
            .flatMap(
                ts ->
                    rasterForVectorTilesets.get(ts.getKey()).stream()
                        .map(rts -> Map.entry(rts, ts.getValue())))
            .collect(ImmutableMap.toImmutableMap(Entry::getKey, Entry::getValue));
    if (!rasterTilesets.isEmpty()) {
      jobSet =
          jobSet.with(TileSeedingJobSet.of(tileProvider.getId(), rasterTilesets, reseed, priority));
    }

    if (!combinedTilesets.isEmpty()) {
      JobSet combinedJobSet =
          TileSeedingJobSet.of(tileProvider.getId(), combinedTilesets, reseed, priority);

      Map<String, TileGenerationParameters> rasterCombinedTilesets =
          combinedTilesets.entrySet().stream()
              .flatMap(
                  ts ->
                      rasterForVectorCombinedTilesets.get(ts.getKey()).stream()
                          .map(rts -> Map.entry(rts, ts.getValue())))
              .collect(ImmutableMap.toImmutableMap(Entry::getKey, Entry::getValue));
      if (!rasterCombinedTilesets.isEmpty()) {
        combinedJobSet =
            combinedJobSet.with(
                TileSeedingJobSet.of(
                    tileProvider.getId(), rasterCombinedTilesets, reseed, priority));
      }

      jobSet = jobSet.with(combinedJobSet);
    }
    return jobSet;
  }

  @Override
  public DatasetChangeListener onDatasetChange(OgcApi api) {
    return change -> {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Seeding on dataset change: {}", change);
      }

      Optional<SeedingOptions> seeding =
          tilesProviders
              .getTileProvider(api.getData())
              .map(TileProvider::seeding)
              .filter(OptionalCapability::isSupported)
              .map(s -> s.get().getOptions());

      if (seeding.isEmpty() || !seeding.get().shouldRunOnDatasetChange()) {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug(
              "Seeding on dataset changes is disabled {} {}",
              seeding.isEmpty(),
              seeding.get().getRunOnDatasetChange());
        }
        return;
      }

      try {
        seedTilesetsFull(api, seeding.get().shouldPurge());
      } catch (IOException e) {
        throw new RuntimeException("Error accessing the tile cache during seeding.", e);
      } catch (Throwable e) {
        LogContext.error(
            LOGGER,
            e,
            "An error occurred during seeding. Note that this may be a side-effect of a server shutdown.");
      }
    };
  }

  @Override
  public FeatureChangeListener onFeatureChange(OgcApi api) {
    OgcApiDataV2 apiData = api.getData();

    TileProvider tileProvider = tilesProviders.getTileProviderOrThrow(apiData);

    if (!tileProvider.seeding().isAvailable() || !tileProvider.generator().isAvailable()) {
      LOGGER.debug("Tile provider '{}' does not support seeding", tileProvider.getId());
      return change -> {};
    }

    return change -> {
      Optional<SeedingOptions> seeding =
          tilesProviders
              .getTileProvider(api.getData())
              .map(TileProvider::seeding)
              .filter(OptionalCapability::isSupported)
              .map(s -> s.get().getOptions());

      if (seeding.isEmpty() || !seeding.get().shouldRunOnFeatureChange()) {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug(
              "Seeding on feature changes is disabled {} {}",
              seeding.isEmpty(),
              seeding.get().getRunOnDatasetChange());
        }
        return;
      }

      String collectionId =
          FeaturesCoreConfiguration.getCollectionId(api.getData(), change.getFeatureType());
      List<BoundingBox> bboxes = new ArrayList<>();

      switch (change.getAction()) {
        case UPDATE:
          // if old and new bbox intersect, merge them, otherwise delete tiles separately
          change
              .getOldBoundingBox()
              .flatMap(
                  oldBbox ->
                      change
                          .getNewBoundingBox()
                          .filter(newBbox -> BoundingBox.intersects(oldBbox, newBbox))
                          .map(newBbox -> BoundingBox.merge(oldBbox, newBbox)))
              .ifPresentOrElse(
                  bboxes::add,
                  () -> {
                    change.getOldBoundingBox().ifPresent(bboxes::add);
                    change.getNewBoundingBox().ifPresent(bboxes::add);
                  });
          break;
        case CREATE:
          change.getNewBoundingBox().ifPresent(bboxes::add);
          break;
        case DELETE:
          change.getOldBoundingBox().ifPresent(bboxes::add);
          break;
      }

      for (BoundingBox bbox : bboxes) {
        JobSet jobSet =
            getJobSet(api, tileProvider, true, Optional.of(collectionId), Optional.of(bbox));

        jobQueue.push(jobSet);
      }
    };
  }

  private Optional<TilesConfiguration> getTilesConfiguration(
      OgcApiDataV2 apiData, String collectionId) {
    return Optional.ofNullable(apiData.getCollections().get(collectionId))
        .flatMap(featureType -> featureType.getExtension(TilesConfiguration.class))
        .filter(TilesConfiguration::isEnabled);
  }
}
