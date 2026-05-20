/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

/** Per-action result of an executed transaction. */
@Value.Immutable
public interface ActionResult {

  /** Action type (INSERT/REPLACE/UPDATE/DELETE). */
  TxActionType getType();

  /** Target collection (always present, even on FAILED actions). */
  String getCollectionId();

  /** {@code actionId} from the request, used to correlate the response with the request. */
  Optional<String> getActionId();

  ActionStatus getStatus();

  /** Feature identifiers affected by this action. Empty for SKIPPED / FAILED outcomes. */
  List<String> getFeatureIds();

  /**
   * Human-readable error message for FAILED outcomes. Empty for SUCCESS / SKIPPED.
   *
   * <p>Implementations should put the spec-defined exception code into the message or attach
   * additional structured metadata via a follow-up extension.
   */
  Optional<String> getError();

  /**
   * Source-side identifiers (e.g. gml:id, GeoJSON id) of the features that may have caused a FAILED
   * outcome. For inserts that fail at batch commit time, this is the full set of feature ids in the
   * failing batch — the broken one is among them but cannot be narrowed further from a batch error.
   * Empty when no candidate ids are known.
   */
  @Value.Default
  default List<String> getFailedFeatureIds() {
    return List.of();
  }

  /**
   * 1-based positions within the originating wfs:Insert / GeoJSON items array of the candidate
   * features in {@link #getFailedFeatureIds()}, in the same order. Useful when the source payload
   * does not carry feature ids. Empty when not known.
   */
  @Value.Default
  default List<Integer> getFailedFeatureIndexes() {
    return List.of();
  }

  /**
   * Per-feature error messages, parallel to {@link #getFailedFeatureIndexes()} (and to {@link
   * #getFailedFeatureIds()} when ids are known). Populated for fine-grained failures such as
   * per-item schema validation under {@code Prefer: handling=strict}, where each rejected feature
   * has its own diagnostic message. Empty when the action carries only an aggregate error (e.g. a
   * batch commit that failed without identifying a specific item) — in that case consult {@link
   * #getError()} instead.
   */
  @Value.Default
  default List<String> getFailedFeatureErrors() {
    return List.of();
  }
}
