/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.io.ByteStreams;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiBackgroundTask;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.tiles3d.domain.Availability;
import de.ii.ogcapi.tiles3d.domain.ImmutableQueryInputSubtreeOld;
import de.ii.ogcapi.tiles3d.domain.QueriesHandler3dTiles.QueryInputSubtreeOld;
import de.ii.ogcapi.tiles3d.domain.Subtree;
import de.ii.ogcapi.tiles3d.domain.TileResourceCache;
import de.ii.ogcapi.tiles3d.domain.TileResourceDescriptor;
import de.ii.ogcapi.tiles3d.domain.Tiles3dConfiguration;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.cql.domain.Cql;
import de.ii.xtraplatform.cql.domain.Cql.Format;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.services.domain.ServicesContext;
import de.ii.xtraplatform.services.domain.TaskContext;
import de.ii.xtraplatform.tiles.domain.SeedingOptions;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.Response;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.shape.fractal.MortonCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for a automatic generation of the Tiles. The range is specified in the
 * config. The automatic generation is executed, when the server is started/restarted.
 */
@Singleton
@AutoBind
@SuppressWarnings({"PMD.GodClass", "PMD.TooManyMethods"})
public class Seeding implements OgcApiBackgroundTask {

  private static final Logger LOGGER = LoggerFactory.getLogger(Seeding.class);

  private final ExtensionRegistry extensionRegistry;
  private final TileResourceCache tileResourcesCache;
  private final ServicesContext servicesContext;
  private final FeaturesCoreProviders providers;
  private final FeaturesCoreQueriesHandler queryHandlerFeatures;
  private final Cql cql;
  private final VolatileRegistry volatileRegistry;

  @Inject
  public Seeding(
      ExtensionRegistry extensionRegistry,
      TileResourceCache tileResourcesCache,
      ServicesContext servicesContext,
      FeaturesCoreProviders providers,
      FeaturesCoreQueriesHandler queryHandlerFeatures,
      Cql cql,
      VolatileRegistry volatileRegistry) {
    this.extensionRegistry = extensionRegistry;
    this.tileResourcesCache = tileResourcesCache;
    this.servicesContext = servicesContext;
    this.providers = providers;
    this.queryHandlerFeatures = queryHandlerFeatures;
    this.cql = cql;
    this.volatileRegistry = volatileRegistry;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    if (!apiData.getEnabled()) {
      return false;
    }

    if (apiData
        .getExtension(Tiles3dConfiguration.class)
        .filter(Tiles3dConfiguration::isEnabled)
        .isEmpty()) {
      return false;
    }

    // 3D Tiles has fixed formats for subtree and tile resources, so we do not check anything
    // related to formats

    // no vector tiles support for WFS backends
    return providers
        .getFeatureProvider(apiData)
        .map(FeatureProvider::supportsHighLoad)
        .orElse(false);
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
  public boolean runOnStart(OgcApi api) {
    return isEnabledForApi(api.getData())
        && api.getData()
            .getExtension(Tiles3dConfiguration.class)
            .flatMap(Tiles3dConfiguration::getSeeding)
            .filter(seedingOptions -> !seedingOptions.shouldRunOnStartup())
            .isEmpty();
  }

  @Override
  public Optional<String> runPeriodic(OgcApi api) {
    if (!isEnabledForApi(api.getData())) {
      return Optional.empty();
    }
    return api.getData()
        .getExtension(Tiles3dConfiguration.class)
        .flatMap(Tiles3dConfiguration::getSeeding)
        .flatMap(SeedingOptions::getCronExpression);
  }

  @Override
  public int getMaxPartials(OgcApi api) {
    return api.getData()
        .getExtension(Tiles3dConfiguration.class)
        .flatMap(Tiles3dConfiguration::getSeeding)
        .map(SeedingOptions::getEffectiveMaxThreads)
        .orElse(1);
  }

  private boolean shouldPurge(OgcApi api) {
    return api.getData()
        .getExtension(Tiles3dConfiguration.class)
        .flatMap(Tiles3dConfiguration::getSeeding)
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
    volatileRegistry
        .onAvailable(
            tileResourcesCache,
            queryHandlerFeatures,
            providers.getFeatureProviderOrThrow(api.getData()))
        .toCompletableFuture()
        .join();

    if (shouldPurge(api)) {
      purge(api, taskContext);
    }

    trySeeding(api, taskContext);
  }

  private void purge(OgcApi api, TaskContext taskContext) {
    if (taskContext.isFirstPartial()) {
      try {
        taskContext.setStatusMessage("purging cache");
        tileResourcesCache.deleteTileResources(api);
        taskContext.setStatusMessage("purged cache successfully");
      } catch (IOException e) {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("{}: purging failed | {}", getLabel(), e.getMessage());
        }
      }
    } else {
      // TODO is there a way to wait for the first partial to complete the purge?
      try {
        // wait for purge to finish before continuing
        Thread.sleep(5000);
      } catch (InterruptedException e) {
        // ignore
      }
    }
  }

  private void trySeeding(OgcApi api, TaskContext taskContext) {
    try {
      // add any additional single-layer tiles
      if (!taskContext.isStopped()) {
        seed(api, taskContext);
      }

    } catch (IOException e) {
      if (!taskContext.isStopped()) {
        throw new IllegalStateException("Error accessing the tile cache during seeding.", e);
      }
    } catch (Throwable e) {
      // in general, this should only happen on shutdown (as we cannot influence shutdown order,
      // exceptions during seeding on shutdown are currently inevitable), but for other situations
      // we still add the error to the log
      if (!taskContext.isStopped()) {
        throw new IllegalStateException(
            "An error occurred during seeding. Note that this may be a side-effect of a server shutdown.",
            e);
      }
    }
  }

  @SuppressWarnings("PMD.CognitiveComplexity")
  private void seed(OgcApi api, TaskContext taskContext) throws IOException {
    long numberOfSubtrees = getNumberOfSubtrees(api, taskContext, 0, 0, 0);
    final double[] currentSubtree = {0.0};
    final double[] currentSkipped = {0.0};

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Seeding {} subtrees", numberOfSubtrees);
    }

    walkCollectionsAndSubtrees(
        api,
        // the root subtree is part of every partial, because it is needed in every partial to
        // determine which child subtrees can be skipped
        0,
        0,
        0,
        (subtreeDescriptor) -> {
          String collectionId = subtreeDescriptor.getCollectionId();
          int level = subtreeDescriptor.getLevel();

          setStatus(
              taskContext,
              subtreeDescriptor,
              collectionId,
              Math.round(currentSubtree[0]),
              numberOfSubtrees,
              Math.round(currentSkipped[0]));

          byte[] subtreeBytes = getSubtreeFromCache(subtreeDescriptor);

          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(
                "Processing {}. In cache: {}.", subtreeDescriptor, subtreeBytes.length > 0);
          }

          if (subtreeBytes.length == 0
              && level == 0
              && taskContext.isPartial()
              && !taskContext.isFirstPartial()) {
            subtreeBytes = waitForRootSubtree(subtreeDescriptor);
            if (subtreeBytes.length == 0) {
              if (LOGGER.isWarnEnabled()) {
                LOGGER.warn(
                    "{}: Waiting for the root subtree failed for collection '{}'.",
                    getLabel(),
                    subtreeDescriptor.getCollectionId());
              }
              return SubtreeWalkerResponse.of();
            }
          }

          final Tiles3dConfiguration cfg =
              api.getData().getExtension(Tiles3dConfiguration.class, collectionId).orElseThrow();
          final int subtreeLevels = Objects.requireNonNull(cfg.getSubtreeLevels());
          final int maxLevel = Objects.requireNonNull(cfg.getMaxLevel());

          final Subtree subtree =
              subtreeBytes.length > 0
                  ? Subtree.of(subtreeBytes)
                  : computeAndCacheSubtree(subtreeDescriptor, cfg);

          if (level > 0 || !taskContext.isPartial() || taskContext.isFirstPartial()) {
            processContentAvailability(taskContext, subtreeDescriptor, subtree, subtreeLevels);
          }

          SubtreeWalkerResponse result =
              processChildSubtreeAvailability(
                  taskContext, subtreeDescriptor, subtree, subtreeLevels, maxLevel);

          currentSubtree[0] += 1;
          currentSkipped[0] += result.getSkipped();
          taskContext.setCompleteness((currentSubtree[0] + currentSkipped[0]) / numberOfSubtrees);

          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(
                "Processed {}/{} subtrees, skipped {}.",
                Math.round(currentSubtree[0]),
                numberOfSubtrees,
                Math.round(currentSkipped[0]));
          }

          return result;
        });
  }

  private byte[] waitForRootSubtree(TileResourceDescriptor subtreeDescriptor) {
    // TODO is there a way to wait for the first partial to complete the computation
    //      of the root subtree?
    byte[] subtreeBytes = new byte[0];
    int count = 0;
    while (subtreeBytes.length == 0 && count < 300) {
      try {
        count++;
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        // ignore
      }
      subtreeBytes = getSubtreeFromCache(subtreeDescriptor);
    }
    return subtreeBytes;
  }

  private void setStatus(
      TaskContext taskContext,
      TileResourceDescriptor subtreeDescriptor,
      String collectionId,
      long processed,
      long numberOfSubtrees,
      long skipped) {
    if (subtreeDescriptor.getLevel() > 0
        || !taskContext.isPartial()
        || taskContext.isFirstPartial()) {
      taskContext.setStatusMessage(
          String.format(
              "currently processing subtree -> %s, %s/%s/%s; processed %d/%d subtrees, skipped %d",
              collectionId,
              subtreeDescriptor.getLevel(),
              subtreeDescriptor.getX(),
              subtreeDescriptor.getY(),
              processed,
              numberOfSubtrees,
              skipped));
    } else {
      taskContext.setStatusMessage(
          String.format(
              "currently waiting for subtree -> %s, %s/%s/%s",
              collectionId,
              subtreeDescriptor.getLevel(),
              subtreeDescriptor.getX(),
              subtreeDescriptor.getY()));
    }
  }

  private Subtree computeAndCacheSubtree(
      TileResourceDescriptor descriptor, Tiles3dConfiguration cfg) {
    final QueryInputSubtreeOld queryInput =
        ImmutableQueryInputSubtreeOld.builder()
            .api(descriptor.getApi())
            .collectionId(descriptor.getCollectionId())
            .level(descriptor.getLevel())
            .x(descriptor.getX())
            .y(descriptor.getY())
            .featureProvider(providers.getFeatureProviderOrThrow(descriptor.getApiData()))
            .featureType(
                descriptor
                    .getApiData()
                    .getExtension(FeaturesCoreConfiguration.class, descriptor.getCollectionId())
                    .flatMap(FeaturesCoreConfiguration::getFeatureType)
                    .orElse(descriptor.getCollectionId()))
            .geometryProperty(
                providers
                    .getFeatureSchema(
                        descriptor.getApiData(),
                        descriptor
                            .getApiData()
                            .getCollectionData(descriptor.getCollectionId())
                            .orElseThrow())
                    .flatMap(SchemaBase::getFilterGeometry)
                    .map(SchemaBase::getFullPathAsString)
                    .orElseThrow())
            .servicesUri(servicesContext.getUri())
            .maxLevel(Objects.requireNonNull(cfg.getMaxLevel()))
            .firstLevelWithContent(Objects.requireNonNull(cfg.getFirstLevelWithContent()))
            .subtreeLevels(Objects.requireNonNull(cfg.getSubtreeLevels()))
            .contentFilters(
                cfg.getContentFilters().stream()
                    .map(filter -> cql.read(filter, Format.TEXT))
                    .collect(Collectors.toUnmodifiableList()))
            .tileFilters(
                cfg.getTileFilters().stream()
                    .map(filter -> cql.read(filter, Format.TEXT))
                    .collect(Collectors.toUnmodifiableList()))
            .build();

    Subtree subtree = Subtree.of(servicesContext, queryHandlerFeatures, queryInput, descriptor);
    storeSubtree(descriptor, Subtree.getBinary(subtree));

    return subtree;
  }

  private void storeSubtree(TileResourceDescriptor subtree, byte[] subtreeBytes) {
    try {
      tileResourcesCache.storeTileResource(subtree, subtreeBytes);
    } catch (IOException e) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn(
            "{}: writing subtree failed -> {}, {}/{}/{} | {}",
            getLabel(),
            subtree.getCollectionId(),
            subtree.getLevel(),
            subtree.getX(),
            subtree.getY(),
            e.getMessage());
        if (LOGGER.isDebugEnabled(LogContext.MARKER.STACKTRACE)) {
          LOGGER.debug(LogContext.MARKER.STACKTRACE, "Stacktrace:", e);
        }
      }
    }
  }

  private SubtreeWalkerResponse processChildSubtreeAvailability(
      TaskContext taskContext,
      TileResourceDescriptor subtreeDescriptor,
      Subtree subtree,
      int subtreeLevels,
      int maxLevel) {
    final Availability childSubtreeAvailability = subtree.getChildSubtreeAvailability();
    final byte[] buffer =
        childSubtreeAvailability.getBitstream().isPresent()
            ? subtree.getChildSubtreeAvailabilityBin()
            : Subtree.EMPTY;
    boolean always =
        buffer.length == 0
            && childSubtreeAvailability.getConstant().filter(c -> c == 1).isPresent();

    if (always || buffer.length > 0) {
      List<TileResourceDescriptor> children = subtreeDescriptor.getChildren(subtreeLevels);
      List<TileResourceDescriptor> toProcess =
          IntStream.range(0, MortonCode.size(subtreeLevels))
              .filter(
                  i ->
                      subtreeInTask(taskContext, children.get(i), subtreeLevels)
                          && (always || getAvailability(buffer, i)))
              .mapToObj(children::get)
              .collect(Collectors.toCollection(LinkedList::new));

      List<TileResourceDescriptor> skip =
          IntStream.range(0, MortonCode.size(subtreeLevels))
              .filter(
                  i ->
                      subtreeInTask(taskContext, children.get(i), subtreeLevels)
                          && !always
                          && !getAvailability(buffer, i))
              .mapToObj(children::get)
              .collect(Collectors.toUnmodifiableList());
      long skipped =
          skip.stream()
              .mapToLong(
                  child ->
                      getNumberOfSubtrees(
                          child.getApi(),
                          taskContext,
                          child.getLevel(),
                          child.getX(),
                          child.getY()))
              .sum();
      return SubtreeWalkerResponse.of(toProcess, skipped);
    }

    if (subtreeDescriptor.getLevel() + subtreeLevels <= maxLevel) {
      List<TileResourceDescriptor> children = subtreeDescriptor.getChildren(subtreeLevels);
      long skipped =
          IntStream.range(0, MortonCode.size(subtreeLevels))
              .filter(i -> subtreeInTask(taskContext, children.get(i), subtreeLevels))
              .mapToObj(children::get)
              .mapToLong(
                  child ->
                      getNumberOfSubtrees(
                          child.getApi(),
                          taskContext,
                          child.getLevel(),
                          child.getX(),
                          child.getY()))
              .sum();
      return SubtreeWalkerResponse.of(skipped);
    }

    return SubtreeWalkerResponse.of();
  }

  private void processContentAvailability(
      TaskContext taskContext,
      TileResourceDescriptor subtreeDescriptor,
      Subtree subtree,
      int subtreeLevels) {
    final Availability contentAvailability = subtree.getContentAvailability().get(0);

    final byte[] buffer =
        contentAvailability.getBitstream().isPresent()
            ? subtree.getContentAvailabilityBin()
            : Subtree.EMPTY;
    boolean always =
        buffer.length == 0 && contentAvailability.getConstant().filter(c -> c == 1).isPresent();

    // generate content
    if (always || buffer.length > 0) {
      int xl = subtreeDescriptor.getX();
      int yl = subtreeDescriptor.getY();
      for (int il = 0; il < subtreeLevels; il++) {
        for (int idx = 0; idx < MortonCode.size(il); idx++) {
          if (always || getAvailability(buffer, il, idx)) {
            Coordinate coord = MortonCode.decode(idx);
            final TileResourceDescriptor contentDescriptor =
                TileResourceDescriptor.contentOf(
                    subtreeDescriptor.getApi(),
                    subtreeDescriptor.getCollectionId(),
                    subtreeDescriptor.getLevel() + il,
                    xl + (int) coord.x,
                    yl + (int) coord.y);
            processContent(contentDescriptor, taskContext);
          }
        }
        xl *= 2;
        yl *= 2;
      }
    }
  }

  private static boolean getAvailability(byte[] availability, int idx) {
    return isSet(availability, idx);
  }

  private static boolean getAvailability(byte[] availability, int level, int idxLevel) {
    int idx = idxLevel;
    // add shift from previous levels
    for (int i = 0; i < level; i++) {
      idx += MortonCode.size(i);
    }
    return isSet(availability, idx);
  }

  private static boolean isSet(byte[] availability, int idx) {
    int byteIndex = idx / 8;
    int bitIndex = idx % 8;
    int bitValue = (availability[byteIndex] >> bitIndex) & 1;
    return bitValue == 1;
  }

  private byte[] getSubtreeFromCache(TileResourceDescriptor subtree) {
    try {
      if (tileResourcesCache.tileResourceExists(subtree)) {
        return tileResourcesCache
            .getTileResource(subtree)
            .map(
                stream -> {
                  try (stream) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ByteStreams.copy(stream, baos);
                    return baos.toByteArray();
                  } catch (IOException e) {
                    throw new IllegalStateException(e.getMessage(), e);
                  }
                })
            .orElse(null);
      }
    } catch (Exception e) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn(
            "Failed to retrieve subtree {}/{}/{} for collection {} from the cache. Reason: {}",
            subtree.getLevel(),
            subtree.getX(),
            subtree.getY(),
            subtree.getCollectionId(),
            e.getMessage());
        if (LOGGER.isDebugEnabled(LogContext.MARKER.STACKTRACE)) {
          LOGGER.debug(LogContext.MARKER.STACKTRACE, "Stacktrace:", e);
        }
      }
    }
    return new byte[0];
  }

  private void processContent(TileResourceDescriptor content, TaskContext taskContext) {
    try {
      if (!tileResourcesCache.tileResourceExists(content)) {
        OgcApiDataV2 apiData = content.getApiData();
        taskContext.setStatusMessage(
            String.format(
                "currently processing content -> %s, %s/%s/%s",
                content.getCollectionId(), content.getLevel(), content.getX(), content.getY()));

        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("Processing {}...", content);
        }

        // generate and store content
        Tiles3dConfiguration cfg =
            apiData
                .getExtension(Tiles3dConfiguration.class, content.getCollectionId())
                .orElseThrow();

        Response response =
            Tiles3dContentUtil.getContent(
                servicesContext,
                extensionRegistry,
                content.getApi(),
                content.getCollectionId(),
                providers.getFeatureProviderOrThrow(
                    apiData, apiData.getCollectionData(content.getCollectionId()).orElseThrow()),
                queryHandlerFeatures,
                cql,
                cfg,
                content,
                content.getQuery(providers),
                new URICustomizer(servicesContext.getUri())
                    .ensureLastPathSegments(
                        content.getApi().getId(),
                        "collections",
                        content.getCollectionId(),
                        "3dtiles",
                        String.format(
                            "content_%d_%d_%d",
                            content.getLevel(), content.getX(), content.getY())),
                Optional.empty());

        if (Objects.nonNull(response.getEntity())) {
          try {
            tileResourcesCache.storeTileResource(content, (byte[]) response.getEntity());
          } catch (IOException e) {
            LogContext.error(
                LOGGER, e, "Could not write feature response to resource '{}'", tileResourcesCache);
          }
        }
      }
    } catch (Exception e) {
      LogContext.errorAsWarn(
          LOGGER,
          e,
          "{}: writing content failed -> {}, {}/{}/{}",
          getLabel(),
          content.getCollectionId(),
          content.getLevel(),
          content.getX(),
          content.getY());
    }
  }

  private long getNumberOfSubtrees(OgcApi api, TaskContext taskContext, int level, int x, int y) {
    final long[] numberOfSubtrees = {0};

    try {
      walkCollectionsAndSubtrees(
          api,
          level,
          x,
          y,
          (subtree) -> {
            numberOfSubtrees[0]++;

            final Tiles3dConfiguration cfg =
                subtree
                    .getApiData()
                    .getExtension(Tiles3dConfiguration.class, subtree.getCollectionId())
                    .orElseThrow();
            final int subtreeLevels = Objects.requireNonNull(cfg.getSubtreeLevels());
            final int maxLevel = Objects.requireNonNull(cfg.getMaxLevel());
            if (subtree.getLevel() + subtreeLevels <= maxLevel) {
              return SubtreeWalkerResponse.of(
                  subtree.getChildren(subtreeLevels).stream()
                      .filter(descriptor -> subtreeInTask(taskContext, descriptor, subtreeLevels))
                      .collect(Collectors.toCollection(LinkedList::new)));
            }
            return SubtreeWalkerResponse.of();
          });
    } catch (IOException e) {
      // ignore
    }

    return numberOfSubtrees[0];
  }

  private boolean subtreeInTask(
      TaskContext taskContext, TileResourceDescriptor subtree, int subtreeLevels) {
    if (!taskContext.isPartial()) {
      return true;
    }

    if (subtree.getLevel() == subtreeLevels) {
      return taskContext.matchesPartialModulo(subtree.getX() + subtree.getY());
    }

    final int xl = subtree.getX() / (int) Math.pow(2, subtree.getLevel() - subtreeLevels);
    final int yl = subtree.getY() / (int) Math.pow(2, subtree.getLevel() - subtreeLevels);
    return taskContext.matchesPartialModulo(xl + yl);
  }

  interface SubtreeWalker {
    SubtreeWalkerResponse visit(TileResourceDescriptor subtree) throws IOException;
  }

  private void walkCollectionsAndSubtrees(
      OgcApi api, int level, int x, int y, SubtreeWalker subtreeWalker) throws IOException {
    for (Entry<String, FeatureTypeConfigurationOgcApi> entry :
        api.getData().getCollections().entrySet()) {
      if (api.getData()
          .getExtension(Tiles3dConfiguration.class, entry.getKey())
          .map(ExtensionConfiguration::isEnabled)
          .isPresent()) {
        String collectionId = entry.getKey();
        walkSubtrees(
            TileResourceDescriptor.subtreeOf(api, collectionId, level, x, y), subtreeWalker);
      }
    }
  }

  private void walkSubtrees(TileResourceDescriptor start, SubtreeWalker subtreeWalker)
      throws IOException {
    LinkedList<TileResourceDescriptor> queue = new LinkedList<>();
    queue.add(start);
    while (!queue.isEmpty()) {
      TileResourceDescriptor subtree = queue.removeFirst();
      queue.addAll(subtreeWalker.visit(subtree).getNext());
    }
  }
}
