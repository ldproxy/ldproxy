/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.transactions.domain.MutationStrategy;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Strategy that governs mutations on collections where the {@code VERSIONED_FEATURES} building
 * block is enabled. The executor selects it via the inherited {@code isEnabledForApi(apiData,
 * collectionId)} contract; otherwise it falls back to {@code PlainMutationStrategy}.
 *
 * <p>Phase 1.0 only carries the dispatch decision; per-action versioning semantics (timestamp
 * capture, retire-and-insert on update/replace, retire-only on delete, no-backdating / no-overlap
 * guards, predecessor/successor maintenance) arrive in phases 1.1-1.6.
 */
@Singleton
@AutoBind
public class VersionedMutationStrategy implements MutationStrategy {

  @Inject
  public VersionedMutationStrategy() {}

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return VersionedFeaturesConfiguration.class;
  }

  @Override
  public int priority() {
    return 100;
  }
}
