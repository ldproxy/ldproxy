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
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.transactions.domain.MutationStrategy;
import de.ii.ogcapi.transactions.domain.TxAction;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration.MutationTime;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;
import java.time.Instant;
import java.util.Optional;

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

  @Override
  public Instant resolveMutationTimestamp(
      OgcApiDataV2 apiData,
      TxAction action,
      Instant scopeTimestamp,
      Optional<Instant> ogcMutationDatetimeHeader) {
    MutationTime mode =
        apiData
            .getExtension(VersionedFeaturesConfiguration.class, action.getCollectionId())
            .map(VersionedFeaturesConfiguration::getMutationTime)
            .orElse(null);
    if (mode == null || mode == MutationTime.SERVER) {
      return scopeTimestamp;
    }
    // mutationTime: client. Precedence per the plan: body-supplied value > header > 400. Body
    // extraction is per-action-type and lands together with the action implementations in phases
    // 1.2-1.4; until then this falls back to the header for every action type. Delete will keep
    // using the header even after 1.2-1.4 because it carries no body.
    return ogcMutationDatetimeHeader.orElseThrow(
        () ->
            new BadRequestException(
                "mutationTime is 'client' on collection '"
                    + action.getCollectionId()
                    + "' but neither the action body nor the OGC-Mutation-Datetime header"
                    + " supplied a mutation timestamp."));
  }
}
