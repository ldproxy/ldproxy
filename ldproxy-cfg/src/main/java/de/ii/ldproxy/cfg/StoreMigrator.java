/*
 * Copyright 2023 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg;

import de.ii.xtraplatform.base.domain.AppConfiguration;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.base.domain.Constants.ENV;
import de.ii.xtraplatform.base.domain.Store;
import de.ii.xtraplatform.base.domain.StoreFilters;
import de.ii.xtraplatform.base.domain.StoreSource;
import de.ii.xtraplatform.base.domain.StoreSource.Content;
import de.ii.xtraplatform.base.domain.StoreSourceFs;
import de.ii.xtraplatform.blobs.app.ResourceStoreImpl;
import de.ii.xtraplatform.blobs.domain.BlobStoreDriver;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import de.ii.xtraplatform.blobs.domain.StoreMigration;
import de.ii.xtraplatform.blobs.domain.StoreMigration.StoreMigrationContext;
import de.ii.xtraplatform.blobs.infra.BlobStoreDriverFs;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class StoreMigrator implements Migrator<StoreMigrationContext, StoreSourceFs, StoreMigration> {

  private static final Logger LOGGER = LoggerFactory.getLogger(StoreMigrator.class);

  private final Path root;
  private final Set<BlobStoreDriver> blobStoreDrivers;

  public StoreMigrator(Path root) {
    this.root = root;
    this.blobStoreDrivers = Set.of(new BlobStoreDriverFs(new MigrationAppContext(root)));
  }

  @Override
  public boolean isApplicable(StoreMigration storeMigration) {
    switch (storeMigration.getType()) {
      case BLOB:
        return storeMigration.getMoves().stream()
            .anyMatch(
                moves -> {
                  try {
                    return store(moves.first()).has(Path.of(""));
                  } catch (IOException e) {
                    return false;
                  }
                });
      case EVENT:
        return false;
      default:
        return false;
    }
  }

  @Override
  public List<String> getPreview(StoreMigration storeMigration) {
    switch (storeMigration.getType()) {
      case BLOB:
        return storeMigration.getMoves().stream()
            .map(
                moves -> {
                  ResourceStore blobStoreFrom = store(moves.first());
                  ResourceStore blobStoreTo = store(moves.second());

                  try (Stream<Path> paths =
                      blobStoreFrom.walk(Path.of(""), 16, (p, a) -> a.isValue())) {
                    return paths
                        // .sorted(Comparator.reverseOrder())
                        .map(
                            path -> {
                              Path from =
                                  moves
                                      .first()
                                      .getAbsolutePath(Path.of(""))
                                      .resolve(path)
                                      .normalize();
                              Path to =
                                  moves
                                      .second()
                                      .getAbsolutePath(Path.of(Content.RESOURCES.getPrefix()))
                                      .resolve(path)
                                      .normalize();

                              return String.format("%s -> %s", from, to);
                            })
                        .collect(Collectors.toList());
                  } catch (IOException e) {
                    // ignore
                    LOGGER.debug("E", e);
                    return List.<String>of();
                  }
                })
            .flatMap(List::stream)
            .collect(Collectors.toList());
      case EVENT:
        break;
    }
    return List.of();
  }

  @Override
  public void execute(StoreMigration storeMigration) {
    switch (storeMigration.getType()) {
      case BLOB:
        storeMigration
            .getMoves()
            .forEach(
                moves -> {
                  /*try (Stream<Path> paths = blobStore.walk(moves.first(), 16, (p, a) -> a.isValue())) {
                      paths
                          .sorted(Comparator.reverseOrder())
                          .forEach(
                              path -> {
                                Path from = moves.first().resolve(path);
                                Path to = moves.second().resolve(path);
                                try {
                                  Optional<InputStream> inputStream = blobStore.get(from);
                                  if (inputStream.isPresent()) {
                                    //TODO
                                    blobStore.put(to, inputStream.get());
                                  }
                                } catch (IOException e) {
                                  // ignore
                                }
                              });
                    } catch (IOException e) {
                      // ignore
                    }
                  });
                  storeMigration.getCleanups().forEach(cleanup -> {
                    try (Stream<Path> paths = blobStore.walk(cleanup, 16, (p, a) -> !a.isValue())) {
                      paths
                          .sorted(Comparator.reverseOrder())
                          .forEach(
                              path -> {
                                Path path1 = cleanup.resolve(path);
                                try {
                                  blobStore.delete(path1);
                                } catch (IOException e) {
                                  // ignore
                                }
                              });
                    } catch (IOException e) {
                      // ignore
                    }*/
                });

        break;
      case EVENT:
        break;
    }
  }

  public ResourceStore store(StoreSourceFs source) {
    ResourceStoreImpl blobStore =
        new ResourceStoreImpl(new MigrationStore(source), null, null, () -> blobStoreDrivers);
    blobStore.onStart(false).toCompletableFuture().join();

    return blobStore;
  }

  private static class MigrationStore implements Store {
    private final StoreSource storeSource;

    private MigrationStore(StoreSource storeSource) {
      this.storeSource = storeSource;
    }

    @Override
    public List<StoreSource> get() {
      return List.of(storeSource);
    }

    @Override
    public List<StoreSource> get(String type) {
      return null;
    }

    @Override
    public List<StoreSource> get(Content content) {
      return null;
    }

    @Override
    public <U> List<U> get(String type, Function<StoreSource, U> map) {
      return null;
    }

    @Override
    public boolean has(String type) {
      return false;
    }

    @Override
    public Optional<StoreSource> getWritable(String type) {
      return Optional.empty();
    }

    @Override
    public <U> Optional<U> getWritable(String type, Function<StoreSource, U> map) {
      return Optional.empty();
    }

    @Override
    public boolean isWritable() {
      return false;
    }

    @Override
    public boolean isWatchable() {
      return false;
    }

    @Override
    public Optional<StoreFilters> getFilter() {
      return Optional.empty();
    }
  }

  private static class MigrationAppContext implements AppContext {
    private final Path dir;

    public MigrationAppContext(Path dir) {
      this.dir = dir;
    }

    @Override
    public String getName() {
      return null;
    }

    @Override
    public String getVersion() {
      return null;
    }

    @Override
    public ENV getEnvironment() {
      return null;
    }

    @Override
    public Path getDataDir() {
      return dir;
    }

    @Override
    public Path getTmpDir() {
      return dir.resolve("tmp");
    }

    @Override
    public AppConfiguration getConfiguration() {
      return null;
    }

    @Override
    public URI getUri() {
      return null;
    }

    @Override
    public String getInstanceName() {
      return "";
    }
  }
}
