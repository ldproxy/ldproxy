/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.WithChangeListeners;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiBackgroundTask;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.tiles3d.domain.Tile3dProviders;
import de.ii.ogcapi.tiles3d.domain.Tiles3dConfiguration;
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
import de.ii.xtraplatform.tiles.domain.SeedingOptions;
import de.ii.xtraplatform.tiles.domain.TileSeedingJobSet;
import de.ii.xtraplatform.tiles3d.domain.ImmutableTile3dGenerationParameters;
import de.ii.xtraplatform.tiles3d.domain.Tile3dGenerationParameters;
import de.ii.xtraplatform.tiles3d.domain.Tile3dProvider;
import de.ii.xtraplatform.tiles3d.domain.Tile3dSeedingJobSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
@SuppressWarnings({"PMD.GodClass", "PMD.TooManyMethods"})
public class Seeding implements OgcApiBackgroundTask, WithChangeListeners {

  private static final Logger LOGGER = LoggerFactory.getLogger(Seeding.class);

  private final FeaturesCoreProviders providers;
  private final Tile3dProviders tile3dProviders;
  private final JobQueue jobQueue;
  private final VolatileRegistry volatileRegistry;

  @Inject
  public Seeding(
      FeaturesCoreProviders providers,
      Tile3dProviders tile3dProviders,
      JobQueue jobQueue,
      VolatileRegistry volatileRegistry) {
    this.providers = providers;
    this.tile3dProviders = tile3dProviders;
    this.jobQueue = jobQueue;
    this.volatileRegistry = volatileRegistry;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    // check that we have a tile provider with seeding support
    if (tile3dProviders
        .getTile3dProvider(apiData)
        .map(provider -> provider.seeding().isSupported())
        .isEmpty()) {
      return false;
    }

    return OgcApiBackgroundTask.super.isEnabledForApi(apiData);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return Tiles3dConfiguration.class;
  }

  @Override
  public Class<OgcApi> getServiceType() {
    return OgcApi.class;
  }

  @Override
  public String getLabel() {
    return "3D Tiles seeding";
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

  @Override
  public boolean runOnStart(OgcApi api) {
    return isEnabledForApi(api.getData())
        && tile3dProviders
            .getTile3dProvider(api.getData())
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
    return tile3dProviders
        .getTile3dProvider(api.getData())
        .filter(provider -> provider.seeding().isSupported())
        .map(provider -> provider.seeding().get().getOptions())
        .flatMap(SeedingOptions::getCronExpression);
  }

  @Override
  public boolean isSilent() {
    return true;
  }

  private boolean shouldPurge(OgcApi api) {
    return tile3dProviders
        .getTile3dProvider(api.getData())
        .filter(provider -> provider.seeding().isSupported())
        .map(provider -> provider.seeding().get().getOptions())
        .filter(SeedingOptions::shouldPurge)
        .isPresent();
  }

  /**
   * Run the seeding
   *
   * @param api the API
   * @param taskContext the context of the current thread
   */
  @Override
  public void run(OgcApi api, TaskContext taskContext) {
    CompletableFuture<Void> waitForVolatiles =
        volatileRegistry
            .onAvailable(tile3dProviders.getTile3dProviderOrThrow(api.getData()))
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
    Tile3dProvider tileProvider = tile3dProviders.getTile3dProviderOrThrow(api.getData());

    if (!tileProvider.seeding().isAvailable()) {
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

  private JobSet getJobSet(OgcApi api, Tile3dProvider tileProvider, boolean reseed) {
    return getJobSet(api, tileProvider, reseed, Optional.empty(), Optional.empty());
  }

  private Optional<Tiles3dConfiguration> getTilesConfiguration(
      OgcApiDataV2 apiData, String collectionId) {
    return Optional.ofNullable(apiData.getCollections().get(collectionId))
        .flatMap(featureType -> featureType.getExtension(Tiles3dConfiguration.class))
        .filter(Tiles3dConfiguration::isEnabled);
  }

  private JobSet getJobSet(
      OgcApi api,
      Tile3dProvider tileProvider,
      boolean reseed,
      Optional<String> collectionId,
      Optional<BoundingBox> bbox) {
    OgcApiDataV2 apiData = api.getData();
    Map<String, Tile3dGenerationParameters> tilesets = new LinkedHashMap<>();

    for (String cid : apiData.getCollections().keySet()) {
      if (collectionId.isPresent() && !collectionId.get().equals(cid)) {
        continue;
      }
      getTilesConfiguration(apiData, cid)
          .filter(cfg -> cfg.hasCollectionTiles(tile3dProviders, apiData, cid))
          .map(cfg -> cfg.getCollectionTileset(cid))
          .ifPresent(
              tileset -> {
                Tile3dGenerationParameters generationParameters =
                    new ImmutableTile3dGenerationParameters.Builder()
                        .clipBoundingBox(bbox.or(() -> api.getSpatialExtent(cid)))
                        .apiId(apiData.getId())
                        .collectionId(cid)
                        .build();

                tilesets.putIfAbsent(tileset, generationParameters);
              });
    }

    int priority = tileProvider.seeding().get().getOptions().getEffectivePriority();

    return Tile3dSeedingJobSet.of(tileProvider.getId(), tilesets, reseed, priority);
  }

  @Override
  public DatasetChangeListener onDatasetChange(OgcApi api) {
    return change -> {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Seeding on dataset change: {}", change);
      }

      Optional<SeedingOptions> seeding =
          tile3dProviders
              .getTile3dProvider(api.getData())
              .map(Tile3dProvider::seeding)
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

    Tile3dProvider tileProvider = tile3dProviders.getTile3dProviderOrThrow(apiData);

    if (!tileProvider.seeding().isAvailable()) {
      LOGGER.debug("Tile provider '{}' does not support seeding", tileProvider.getId());
      return change -> {};
    }

    return change -> {
      Optional<SeedingOptions> seeding =
          tile3dProviders
              .getTile3dProvider(api.getData())
              .map(Tile3dProvider::seeding)
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
}
