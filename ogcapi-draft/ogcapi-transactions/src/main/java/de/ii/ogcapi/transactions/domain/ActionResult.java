/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;
import org.threeten.extra.Interval;

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
   * Non-fatal warnings the data source emitted while executing this action (e.g. PostgreSQL {@code
   * RAISE WARNING} / {@code RAISE NOTICE} from triggers or functions invoked by the action's
   * statements). Present on any outcome — a failed action keeps the warnings emitted before its
   * error.
   */
  @Value.Default
  default List<String> getWarnings() {
    return List.of();
  }

  /**
   * Source-side identifiers (e.g. gml:id, GeoJSON id) of the features that may have caused a FAILED
   * outcome. For inserts that fail at batch commit time, this is the full set of feature ids in the
   * failing batch — the broken one is among them but cannot be narrowed further from a batch error.
   * Empty when no candidate ids are known; the same items then appear in {@link
   * #getFailedFeaturePayloads()} so the client can correlate by content.
   */
  @Value.Default
  default List<String> getFailedFeatureIds() {
    return List.of();
  }

  /**
   * Raw payload (as sent by the client) of every failing item that has no source-side id. Used as a
   * content-based locator so the client can match the failure against the request body. The
   * encoding is the request's {@code Content-Type} (GeoJSON Feature JSON, GML element XML, …).
   * Empty when every failing item is already identified by {@link #getFailedFeatureIds()} or when
   * the source payload is not available.
   */
  @Value.Default
  default List<String> getFailedFeaturePayloads() {
    return List.of();
  }

  /**
   * Per-feature error messages, parallel to the combined sequence of {@link #getFailedFeatureIds()}
   * followed by {@link #getFailedFeaturePayloads()}. Populated for fine-grained failures such as
   * per-item schema validation under {@code Prefer: handling=strict}, where each rejected feature
   * has its own diagnostic message. Empty when the action carries only an aggregate error (e.g. a
   * batch commit that failed without identifying a specific item) — in that case consult {@link
   * #getError()} instead.
   */
  @Value.Default
  default List<String> getFailedFeatureErrors() {
    return List.of();
  }

  /**
   * New spatial extent of the features touched by this action, when known. Executor-internal: used
   * by the transactions command handler to feed {@code featureProvider.changes().handle(...)} so
   * collection metadata (bbox / item count / lastModified) stays current; not part of the JSON
   * response.
   */
  @JsonIgnore
  Optional<BoundingBox> getNewBoundingBox();

  /**
   * New temporal extent of the features touched by this action, when known. Executor-internal, same
   * use as {@link #getNewBoundingBox()}.
   */
  @JsonIgnore
  Optional<Interval> getNewInterval();
}
