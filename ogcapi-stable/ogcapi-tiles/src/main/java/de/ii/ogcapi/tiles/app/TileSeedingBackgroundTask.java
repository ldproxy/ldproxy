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
import de.ii.xtralink.jobs.JobConfiguration;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.base.domain.resiliency.OptionalCapability;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.features.domain.DatasetChangeListener;
import de.ii.xtraplatform.features.domain.FeatureChangeListener;
import de.ii.xtraplatform.jobs.domain.JobQueue;
import de.ii.xtraplatform.services.domain.TaskContext;
import de.ii.xtraplatform.tiles.domain.ImmutableTileGenerationParameters;
import de.ii.xtraplatform.tiles.domain.SeedingOptions;
import de.ii.xtraplatform.tiles.domain.TileGenerationParameters;
import de.ii.xtraplatform.tiles.domain.TileMatrixSetLimits;
import de.ii.xtraplatform.tiles.domain.TileProvider;
import de.ii.xtraplatform.tiles.domain.TileProviderFeaturesData;
import de.ii.xtraplatform.tiles.domain.TileSeedingJob;
import de.ii.xtraplatform.tiles.domain.TilesetFeatures;
import de.ii.xtraplatform.tiles.domain.TilesetMetadata;
import de.ii.xtraplatform.xtralink.domain.Jobs;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
  private final Jobs jobs;

  @Inject
  public TileSeedingBackgroundTask(
      FeaturesCoreProviders providers,
      TilesProviders tilesProviders,
      VolatileRegistry volatileRegistry,
      JobQueue jobQueue,
      Jobs jobs) {
    this.providers = providers;
    this.tilesProviders = tilesProviders;
    this.volatileRegistry = volatileRegistry;
    this.jobQueue = jobQueue;
    this.jobs = jobs;
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
                    Objects.equals(jobSet.getType(), TileSeedingJob.TYPE)
                        && !jobSet.isDone()
                        && jobSet
                            .getEntity()
                            .filter(e -> Objects.equals(e, tileProvider.getId()))
                            .isPresent());

    if (inProgress) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("{} is already in progress, skipping new task", TileSeedingJob.LABEL);
      }

      return;
    }

    Optional<JobConfiguration> jobSet = getJobSet(api, tileProvider, reseed);

    if (jobSet.isEmpty()) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(
            "No tileset of tile provider '{}' has tiles to seed, skipping new task",
            tileProvider.getId());
      }

      return;
    }

    jobs.push(jobSet.get());
  }

  private Optional<JobConfiguration> getJobSet(
      OgcApi api, TileProvider tileProvider, boolean reseed) {
    return getJobSet(api, tileProvider, reseed, Optional.empty(), Optional.empty());
  }

  private Optional<JobConfiguration> getJobSet(
      OgcApi api,
      TileProvider tileProvider,
      boolean reseed,
      Optional<String> collectionId,
      Optional<BoundingBox> bbox) {
    OgcApiDataV2 apiData = api.getData();

    // every tileset that has to be seeded, with the extent to fall back to, if the bounds of the
    // tileset are unknown
    Map<String, Supplier<Optional<BoundingBox>>> requiredTilesets = new LinkedHashMap<>();

    for (String cid : apiData.getCollections().keySet()) {
      if (collectionId.isPresent() && !collectionId.get().equals(cid)) {
        continue;
      }
      getTilesConfiguration(apiData, cid)
          .filter(cfg -> cfg.hasCollectionTiles(tilesProviders, apiData, cid))
          .map(cfg -> cfg.getCollectionTileset(cid))
          .ifPresent(
              tileset -> requiredTilesets.putIfAbsent(tileset, () -> api.getSpatialExtent(cid)));
    }

    apiData
        .getExtension(TilesConfiguration.class)
        .filter(cfg -> cfg.hasDatasetVectorTiles(tilesProviders, apiData))
        .map(TilesConfiguration::getDatasetTileset)
        .ifPresent(tileset -> requiredTilesets.putIfAbsent(tileset, api::getSpatialExtent));

    for (String tileset : getSourceTilesets(tileProvider, requiredTilesets.keySet())) {
      requiredTilesets.putIfAbsent(tileset, Optional::empty);
    }

    Map<String, TileGenerationParameters> tilesets = new LinkedHashMap<>();
    Map<String, TileGenerationParameters> combinedTilesets = new LinkedHashMap<>();

    requiredTilesets.forEach(
        (tileset, fallbackExtent) -> {
          Optional<BoundingBox> clipBoundingBox =
              bbox.or(() -> getTilesetBounds(tileProvider, tileset)).or(fallbackExtent);

          // without an extent there is no area with data, so there is nothing to seed
          if (clipBoundingBox.isEmpty()) {
            return;
          }

          TileGenerationParameters generationParameters =
              new ImmutableTileGenerationParameters.Builder()
                  .clipBoundingBox(clipBoundingBox)
                  .substitutions(
                      FeaturesCoreProviders.DEFAULT_SUBSTITUTIONS.apply(api.getUri().toString()))
                  .build();

          if (isCombined(tileProvider, tileset)) {
            combinedTilesets.put(tileset, generationParameters);
          } else {
            tilesets.put(tileset, generationParameters);
          }
        });

    // A tileset whose zoom levels start above the levels of every seeded cache has no tiles to
    // seed. Listing it in the job set would announce work that never happens.
    getSeededCoverage(tileProvider, tilesets, combinedTilesets)
        .ifPresent(
            coverage -> {
              tilesets.keySet().removeIf(t -> coverage.getOrDefault(t, Map.of()).isEmpty());
              combinedTilesets.keySet().removeIf(t -> coverage.getOrDefault(t, Map.of()).isEmpty());
            });

    // an empty job set would never reach 100%, and while it is in the queue it suppresses every
    // following seeding run of the tile provider
    if (tilesets.isEmpty() && combinedTilesets.isEmpty()) {
      return Optional.empty();
    }

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

    JobConfiguration job = TileSeedingJob.of(tileProvider.getId(), tilesets, reseed, priority);

    Map<String, TileGenerationParameters> rasterTilesets =
        tilesets.entrySet().stream()
            .flatMap(
                ts ->
                    rasterForVectorTilesets.get(ts.getKey()).stream()
                        .map(rts -> Map.entry(rts, ts.getValue())))
            .collect(ImmutableMap.toImmutableMap(Entry::getKey, Entry::getValue));
    if (!rasterTilesets.isEmpty()) {
      job =
          Jobs.addFollowUps(
              job, TileSeedingJob.of(tileProvider.getId(), rasterTilesets, reseed, priority));
    }

    if (!combinedTilesets.isEmpty()) {
      JobConfiguration combinedJob =
          TileSeedingJob.of(tileProvider.getId(), combinedTilesets, reseed, priority);

      Map<String, TileGenerationParameters> rasterCombinedTilesets =
          combinedTilesets.entrySet().stream()
              .flatMap(
                  ts ->
                      rasterForVectorCombinedTilesets.get(ts.getKey()).stream()
                          .map(rts -> Map.entry(rts, ts.getValue())))
              .collect(ImmutableMap.toImmutableMap(Entry::getKey, Entry::getValue));
      if (!rasterCombinedTilesets.isEmpty()) {
        combinedJob =
            Jobs.addFollowUps(
                combinedJob,
                TileSeedingJob.of(tileProvider.getId(), rasterCombinedTilesets, reseed, priority));
      }

      job = Jobs.addFollowUps(job, combinedJob);
    }
    return Optional.of(job);
  }

  /**
   * The tiles the seeded caches of the tile provider cover, per tileset. Empty if that cannot be
   * determined, which is different from a tileset with no tiles to seed.
   */
  @SafeVarargs
  private Optional<Map<String, Map<String, Set<TileMatrixSetLimits>>>> getSeededCoverage(
      TileProvider tileProvider, Map<String, TileGenerationParameters>... tilesets) {
    if (!tileProvider.seeding().isAvailable()) {
      return Optional.empty();
    }

    Map<String, TileGenerationParameters> all = new LinkedHashMap<>();
    for (Map<String, TileGenerationParameters> map : tilesets) {
      all.putAll(map);
    }

    try {
      return Optional.of(tileProvider.seeding().get().getCoverage(all));
    } catch (IOException e) {
      LOGGER.debug("Could not determine which tiles to seed: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * The area in which the tileset may have data, that is the spatial extent of the feature types of
   * its layers. This is the extent that is also used to determine whether a tile of a sparse
   * tileset can be empty and whether a tileset can contribute a layer to a combined tile, so it
   * should also determine which tiles are seeded. The extent of the collection is only a fallback,
   * it may differ, if the tiles are generated from another feature provider than the features.
   */
  private Optional<BoundingBox> getTilesetBounds(TileProvider tileProvider, String tileset) {
    return tileProvider.access().isAvailable()
        ? tileProvider.access().get().getMetadata(tileset).flatMap(TilesetMetadata::getBounds)
        : Optional.empty();
  }

  /**
   * The tilesets that are combined into one of the given tilesets. They are not necessarily
   * published as a collection of this API, but since combining a tile requires a tile of every
   * source tileset, they have to be seeded as well. Otherwise every tile of such a tileset would be
   * generated on-the-fly while combining, which defeats the purpose of seeding.
   */
  private List<String> getSourceTilesets(TileProvider tileProvider, Set<String> tilesets) {
    if (!(tileProvider.getData() instanceof TileProviderFeaturesData)) {
      return List.of();
    }

    TileProviderFeaturesData data = (TileProviderFeaturesData) tileProvider.getData();

    return tilesets.stream()
        .filter(data.getTilesets()::containsKey)
        .flatMap(tileset -> data.getTilesets().get(tileset).getCombine().stream())
        .flatMap(
            layer ->
                Objects.equals(layer, TilesetFeatures.COMBINE_ALL)
                    ? data.getTilesets().keySet().stream()
                    : Stream.of(layer))
        .distinct()
        .filter(data.getTilesets()::containsKey)
        .filter(tileset -> !data.getTilesets().get(tileset).isCombined())
        .collect(Collectors.toList());
  }

  private boolean isCombined(TileProvider tileProvider, String tileset) {
    if (!(tileProvider.getData() instanceof TileProviderFeaturesData)) {
      return false;
    }

    TilesetFeatures tilesetFeatures =
        ((TileProviderFeaturesData) tileProvider.getData()).getTilesets().get(tileset);

    return Objects.nonNull(tilesetFeatures) && tilesetFeatures.isCombined();
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
        getJobSet(api, tileProvider, true, Optional.of(collectionId), Optional.of(bbox))
            .ifPresent(jobs::push);
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
