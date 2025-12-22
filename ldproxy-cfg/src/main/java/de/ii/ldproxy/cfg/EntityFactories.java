/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg;

import com.google.common.collect.ImmutableSet;
import de.ii.ogcapi.foundation.app.OgcApiFactory;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.xtraplatform.auth.app.UserFactory;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import de.ii.xtraplatform.entities.domain.EntityData;
import de.ii.xtraplatform.entities.domain.EntityFactory;
import de.ii.xtraplatform.entities.domain.PersistentEntity;
import de.ii.xtraplatform.features.gml.app.FeatureProviderWfsFactory;
import de.ii.xtraplatform.features.sql.app.FeatureProviderSqlFactory;
import de.ii.xtraplatform.features.sql.app.SqlClientBasicFactorySimple;
import de.ii.xtraplatform.features.sql.app.SqlDbmsAdaptersImpl;
import de.ii.xtraplatform.features.sql.domain.SqlDbmsAdapters;
import de.ii.xtraplatform.features.sql.infra.db.SqlDbmsAdapterGpkg;
import de.ii.xtraplatform.features.sql.infra.db.SqlDbmsAdapterPgis;
import de.ii.xtraplatform.tiles.app.TileProviderFeaturesFactory;
import de.ii.xtraplatform.tiles.app.TileProviderHttpFactory;
import de.ii.xtraplatform.tiles.app.TileProviderMbTilesFactory;
import de.ii.xtraplatform.tiles3d.app.Tile3dProviderFeaturesFactory;
import de.ii.xtraplatform.tiles3d.app.Tile3dProviderFilesFactory;
import de.ii.xtraplatform.web.app.HttpApache;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface EntityFactories {

  static Set<EntityFactory> factories(
      AppContext appContext, ExtensionRegistry extensionRegistry, ResourceStore mockResourceStore) {
    SqlDbmsAdapters dbmsAdapters =
        new SqlDbmsAdaptersImpl(
            () ->
                Set.of(
                    new SqlDbmsAdapterGpkg(appContext, mockResourceStore, null),
                    new SqlDbmsAdapterPgis(appContext)));

    return ImmutableSet.<EntityFactory>builder()
        .add(
            new FeatureProviderSqlFactory(
                dbmsAdapters, new SqlClientBasicFactorySimple(dbmsAdapters)) {
              @Override
              public CompletableFuture<PersistentEntity> createInstance(EntityData entityData) {
                return CompletableFuture.completedFuture(null);
              }

              @Override
              public CompletableFuture<PersistentEntity> updateInstance(
                  EntityData entityData, boolean force) {
                return CompletableFuture.completedFuture(null);
              }

              @Override
              public void deleteInstance(String id) {}

              @Override
              public void addEntityListener(
                  Consumer<PersistentEntity> listener, boolean existing) {}

              @Override
              public void addEntityGoneListener(Consumer<PersistentEntity> listener) {}
            })
        .add(
            new FeatureProviderWfsFactory(new HttpApache(appContext)) {
              @Override
              public CompletableFuture<PersistentEntity> createInstance(EntityData entityData) {
                return CompletableFuture.completedFuture(null);
              }

              @Override
              public CompletableFuture<PersistentEntity> updateInstance(
                  EntityData entityData, boolean force) {
                return CompletableFuture.completedFuture(null);
              }

              @Override
              public void deleteInstance(String id) {}

              @Override
              public void addEntityListener(
                  Consumer<PersistentEntity> listener, boolean existing) {}

              @Override
              public void addEntityGoneListener(Consumer<PersistentEntity> listener) {}
            })
        .add(
            new TileProviderFeaturesFactory() {
              @Override
              public CompletableFuture<PersistentEntity> createInstance(EntityData entityData) {
                return CompletableFuture.completedFuture(null);
              }

              @Override
              public CompletableFuture<PersistentEntity> updateInstance(
                  EntityData entityData, boolean force) {
                return CompletableFuture.completedFuture(null);
              }

              @Override
              public void deleteInstance(String id) {}

              @Override
              public void addEntityListener(
                  Consumer<PersistentEntity> listener, boolean existing) {}

              @Override
              public void addEntityGoneListener(Consumer<PersistentEntity> listener) {}
            })
        .add(
            new TileProviderHttpFactory() {
              @Override
              public CompletableFuture<PersistentEntity> createInstance(EntityData entityData) {
                return CompletableFuture.completedFuture(null);
              }

              @Override
              public CompletableFuture<PersistentEntity> updateInstance(
                  EntityData entityData, boolean force) {
                return CompletableFuture.completedFuture(null);
              }

              @Override
              public void deleteInstance(String id) {}

              @Override
              public void addEntityListener(
                  Consumer<PersistentEntity> listener, boolean existing) {}

              @Override
              public void addEntityGoneListener(Consumer<PersistentEntity> listener) {}
            })
        .add(
            new TileProviderMbTilesFactory() {
              @Override
              public CompletableFuture<PersistentEntity> createInstance(EntityData entityData) {
                return CompletableFuture.completedFuture(null);
              }

              @Override
              public CompletableFuture<PersistentEntity> updateInstance(
                  EntityData entityData, boolean force) {
                return CompletableFuture.completedFuture(null);
              }

              @Override
              public void deleteInstance(String id) {}

              @Override
              public void addEntityListener(
                  Consumer<PersistentEntity> listener, boolean existing) {}

              @Override
              public void addEntityGoneListener(Consumer<PersistentEntity> listener) {}
            })
        .add(
            new Tile3dProviderFeaturesFactory() {
              @Override
              public CompletableFuture<PersistentEntity> createInstance(EntityData entityData) {
                return CompletableFuture.completedFuture(null);
              }

              @Override
              public CompletableFuture<PersistentEntity> updateInstance(
                  EntityData entityData, boolean force) {
                return CompletableFuture.completedFuture(null);
              }

              @Override
              public void deleteInstance(String id) {}

              @Override
              public void addEntityListener(
                  Consumer<PersistentEntity> listener, boolean existing) {}

              @Override
              public void addEntityGoneListener(Consumer<PersistentEntity> listener) {}
            })
        .add(
            new Tile3dProviderFilesFactory() {
              @Override
              public CompletableFuture<PersistentEntity> createInstance(EntityData entityData) {
                return CompletableFuture.completedFuture(null);
              }

              @Override
              public CompletableFuture<PersistentEntity> updateInstance(
                  EntityData entityData, boolean force) {
                return CompletableFuture.completedFuture(null);
              }

              @Override
              public void deleteInstance(String id) {}

              @Override
              public void addEntityListener(
                  Consumer<PersistentEntity> listener, boolean existing) {}

              @Override
              public void addEntityGoneListener(Consumer<PersistentEntity> listener) {}
            })
        .add(
            new OgcApiFactory(extensionRegistry) {
              @Override
              public CompletableFuture<PersistentEntity> createInstance(EntityData entityData) {
                return CompletableFuture.completedFuture(null);
              }

              @Override
              public CompletableFuture<PersistentEntity> updateInstance(
                  EntityData entityData, boolean force) {
                return CompletableFuture.completedFuture(null);
              }

              @Override
              public void deleteInstance(String id) {}

              @Override
              public void addEntityListener(
                  Consumer<PersistentEntity> listener, boolean existing) {}

              @Override
              public void addEntityGoneListener(Consumer<PersistentEntity> listener) {}
            })
        .add(
            new UserFactory(null) {
              @Override
              public CompletableFuture<PersistentEntity> createInstance(EntityData entityData) {
                return CompletableFuture.completedFuture(null);
              }

              @Override
              public CompletableFuture<PersistentEntity> updateInstance(
                  EntityData entityData, boolean force) {
                return CompletableFuture.completedFuture(null);
              }

              @Override
              public void deleteInstance(String id) {}

              @Override
              public void addEntityListener(
                  Consumer<PersistentEntity> listener, boolean existing) {}

              @Override
              public void addEntityGoneListener(Consumer<PersistentEntity> listener) {}
            })
        .build();
  }
}
