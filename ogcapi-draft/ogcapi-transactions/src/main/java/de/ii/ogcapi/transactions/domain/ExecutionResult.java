/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

import java.util.List;
import org.immutables.value.Value;

/** Result of executing a {@link Transaction}. */
@Value.Immutable
public interface ExecutionResult {

  TxSemantic getSemantic();

  /**
   * One entry per action that was read from the request, in request order. Insert actions become
   * exactly one entry whose {@link ActionResult#getFeatureIds()} lists every inserted feature.
   */
  List<ActionResult> getActionResults();

  /**
   * Non-fatal warnings emitted by the configured transaction-lifecycle hooks (e.g. PostgreSQL
   * {@code RAISE WARNING} / {@code RAISE NOTICE} from a setup or pre-commit statement). May be
   * present on both successful and failed transactions.
   */
  List<String> getWarnings();

  /** Whether the transaction as a whole succeeded (no FAILED actions in atomic; any in batch). */
  @Value.Derived
  default boolean isSuccess() {
    return getActionResults().stream().noneMatch(r -> r.getStatus() == ActionStatus.FAILED);
  }

  @Value.Derived
  default long getInsertedCount() {
    return countFeatures(TxActionType.INSERT);
  }

  @Value.Derived
  default long getReplacedCount() {
    return countFeatures(TxActionType.REPLACE);
  }

  @Value.Derived
  default long getUpdatedCount() {
    return countFeatures(TxActionType.UPDATE);
  }

  @Value.Derived
  default long getDeletedCount() {
    return countFeatures(TxActionType.DELETE);
  }

  @Value.Derived
  default long getFailedCount() {
    return getActionResults().stream().filter(r -> r.getStatus() == ActionStatus.FAILED).count();
  }

  private long countFeatures(TxActionType type) {
    return getActionResults().stream()
        .filter(r -> r.getType() == type && r.getStatus() == ActionStatus.SUCCESS)
        .mapToLong(r -> r.getFeatureIds().size())
        .sum();
  }
}
